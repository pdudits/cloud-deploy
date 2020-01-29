/*
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */

package fish.payara.cloud.deployer.kubernetes;

import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.mockwebserver.utils.BodyProvider;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

public class StoredLog {
    static class Event {
        Instant timestamp;
        Duration sinceLastEvent;
        String action;
        String payload;

        public Event(Instant timestamp, String action, String payload) {
            this.timestamp = timestamp;
            this.action = action;
            this.payload = payload;
        }

        String toJson() {
            return String.format("{\"type\": \"%s\", \"object\":%s}",
                    action, payload);
        }

    }

    static class EventStream {
        private List<Event> stream = new ArrayList<>();

        void add(Instant timestamp, String action, String payload) {
            stream.add(new Event(timestamp, action, payload));
        }

        void finish() {
            Event previous = null;
            for (Event event : stream) {
                if (previous != null) {
                    event.sinceLastEvent = Duration.between(previous.timestamp, event.timestamp);
                }
                previous = event;
            }
        }
    }

    private Map<String, EventStream> streams = new HashMap<>();

    private EventStream stream(String key) {
        return streams.computeIfAbsent(key, (k) -> new EventStream());
    }

    public boolean hasStream(String id) {
        return streams.containsKey(id);
    }

    public List<Event> get(String id) {
        return hasStream(id) ? streams.get(id).stream : null;
    }

    public void applyWatchStream(String stream, String path, double speedup, KubernetesServer server) {
        applyStream(stream, path, speedup, server, Event::toJson);
    }

    public void applyLogStream(String stream, String path, double speedup, KubernetesServer server) {
        // Since logs use just simple long poll, there's no way to emulate it with mock server.
        // we'll send the entire log at once now.
        var expectation = server.expect().get().withPath(path)
                .andReturn(200, get(stream).stream().map(e -> e.payload).collect(joining("")))
                .once();
    }

    private void applyStream(String stream, String path, double speedup, KubernetesServer server, Function<Event,Object> emitter) {
        // the internal types are so ugly, that I'm grateful for var :)
        var expectation = server.expect().withPath(path).andUpgradeToWebSocket().open();

        for (Event event : get(stream)) {
            if (event.sinceLastEvent == null) {
                expectation = expectation.immediately().andEmit(emitter.apply(event));
            } else {
                expectation = expectation.waitFor((long)(event.sinceLastEvent.toMillis()/speedup))
                        .andEmit(emitter.apply(event));
            }
        }
        expectation.done().once();
    }


    private void parseFile(Path path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path.toFile(), StandardCharsets.UTF_8));
        while (reader.ready()) {
            Instant timestamp = Instant.parse(reader.readLine());
            String kind = reader.readLine();
            if ("Log".equals(kind)) {
                String podUid = reader.readLine();
                int length = Integer.parseInt(reader.readLine());
                char[] payload = new char[length];
                reader.read(payload);
                reader.readLine(); // newline
                stream(podUid).add(timestamp, null, new String(payload));
            } else {
                String objectKind = reader.readLine();
                String action = reader.readLine();
                String resource = reader.readLine();
                stream(objectKind).add(timestamp, action, resource);
            }
        }
        streams.values().stream().forEach(EventStream::finish);
    }

    static StoredLog parse(Path path) throws IOException {
        var result = new StoredLog();
        result.parseFile(path);
        return result;
    }

}
