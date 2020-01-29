/*
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.cloud.deployer.process;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

@ApplicationScoped
public class ProcessObserver {

    private int general;
    private CountDownLatch latch;
    private int processStart;
    private DeploymentProcessState lastProcess;

    private ConcurrentHashMap<ChangeKind, Integer> eventCounts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<ChangeKind, CountDownLatch> eventLatches = new ConcurrentHashMap<>();

    private static final Logger LOGGER = Logger.getLogger(ProcessObserver.class.getName());

    void generalObserver(@ObservesAsync StateChanged event) {
        this.general++;
        this.lastProcess = event.getProcess();
        eventCounts.compute(event.getKind(), ((changeKind, count) -> count == null ? 1 : count+1));
        var completionMessage = event.getProcess().getCompletionMessage();
        LOGGER.info(event.getProcess().getId() + " " + event.getKind() + " " + completionMessage != null ? completionMessage : "");
        eventLatches.get(event.getKind()).countDown();
        if (this.latch != null) {
            latch.countDown();
        }
    }

    void startObserver(@ObservesAsync @ChangeKind.Filter(ChangeKind.PROCESS_STARTED) StateChanged event) {
        this.processStart++;
    }

    @PostConstruct
    public void reset() {
        this.general = 0;
        this.processStart = 0;
        this.lastProcess = null;
        this.latch = null;
        eventCounts.clear();
        Stream.of(ChangeKind.values()).forEach(kind -> eventLatches.put(kind, new CountDownLatch(1)));
    }

    public void expect(int events) {
        latch = new CountDownLatch(events);
    }

    public void await() {
        if (latch != null) {
            try {
                assertTrue("Event should  be fired within short time", latch.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void await(ChangeKind kind) {
        await(kind, 5, TimeUnit.SECONDS);
    }

    public void await(ChangeKind kind, long timeOut, TimeUnit unit) {
        try {
            assertTrue("Event "+kind+" should be fired within "+timeOut+" "+unit, eventLatches.get(kind).await(timeOut, unit));
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for "+kind, e);
        }
    }

    public int getGeneral() {
        return general;
    }

    public int getProcessStart() {
        return eventCounts.getOrDefault(ChangeKind.PROCESS_STARTED, 0);
    }

    public DeploymentProcessState getLastProcess() {
        return lastProcess;
    }
}
