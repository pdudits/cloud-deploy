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

import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.MockDeploymentProcess;
import fish.payara.cloud.deployer.process.Namespace;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This test is closely bound to how KubernetesWatcher operates.
 * Capture is created from {@link CreateTestManual#inspectWatches()}, which stores Kubernetes events in a format
 * understood by {@link StoredLog} and these are then fed into mock kubernetes server
 */
public class WatchTest {
    @Rule
    public KubernetesServer mockServer = new KubernetesServer();

    @Test
    public void fromCapture() throws IOException, InterruptedException {
        var log = StoredLog.parse(Path.of("src/test/resources/single-app-1.log"));
        log.applyWatchStream("Ingress", "/apis/extensions/v1beta1/ingresses?labelSelector=app.kubernetes.io%2Fmanaged-by%3Dpayara-cloud&watch=true", 10, mockServer);
        log.applyWatchStream("Deployment", "/apis/apps/v1/deployments?labelSelector=app.kubernetes.io%2Fmanaged-by%3Dpayara-cloud&watch=true", 5, mockServer);
        log.applyWatchStream("Pod", "/api/v1/pods?labelSelector=app.kubernetes.io%2Fmanaged-by%3Dpayara-cloud&watch=true", 5, mockServer);
        log.applyLogStream("68a78ed6-41da-11ea-95a2-920d8d1d0d91", "/api/v1/namespaces/test-dev/pods/ui-77867f97dc-7lg5n/log?pretty=false&follow=true", 5, mockServer);

        var deploymentProcess = MockDeploymentProcess.get();

        MockDeploymentProcess.startFixedIDProcess("6a94c593-7061-4fe6-94de-6bca574077a7", "ROOT.war", new Namespace("test","dev"), null);

        try (KubernetesWatcher watcher = new KubernetesWatcher() {
            @Override
            protected void logEvent(Watcher.Action action, HasMetadata resource) {
            }

            @Override
            protected void logLog(String id, ObjectMeta podMeta, byte[] chunk) {
            }
        }) {
            watcher.client = mockServer.getClient();
            watcher.executorService = Executors.newSingleThreadScheduledExecutor();
            watcher.process = deploymentProcess;
            watcher.pumpInterval = Duration.ofMillis(50);
            watcher.startWatching();

            var observer = MockDeploymentProcess.observer();
            observer.await(ChangeKind.DEPLOYMENT_READY);
            observer.await(ChangeKind.INGRESS_READY,11, TimeUnit.SECONDS);
            observer.await(ChangeKind.POD_CREATED);
            observer.await(ChangeKind.PROVISION_FINISHED);
            observer.await(ChangeKind.OUTPUT_LOGGED);
        }
    }
}
