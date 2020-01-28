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

import com.google.common.base.Charsets;
import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.StateChanged;
import fish.payara.cloud.deployer.setup.DirectProvisioning;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.utils.Serialization;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@DirectProvisioning
class KubernetesWatcher {
    private static final Logger LOGGER = Logger.getLogger(KubernetesWatcher.class.getName());

    @Inject
    NamespacedKubernetesClient client;
    @Inject
    ScheduledExecutorService executorService;

    private Watch ingressWatch;
    private Watch deploymentWatch;
    private Watch podWatch;
    private Set<String> startingPods = new ConcurrentHashMap<String, Boolean>().keySet(true);
    private ConcurrentMap<String, LogWatch> podWatches = new ConcurrentHashMap<>();

    @PostConstruct
    void startWatching() {
        ingressWatch = client.inAnyNamespace().extensions().ingresses()
                .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                .watch(new Watcher<Ingress>() {
                    @Override
                    public void eventReceived(Action action, Ingress resource) {
                        ingressEventReceived(action, resource);
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        LOGGER.log(Level.INFO, "Ingress watch closed", cause);
                    }
                });
        deploymentWatch = client.inAnyNamespace().apps().deployments()
                .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                .watch(new Watcher<Deployment>() {
                    @Override
                    public void eventReceived(Action action, Deployment resource) {
                        deploymentEventReceived(action, resource);
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        LOGGER.log(Level.INFO, "Deployment watch closed", cause);
                    }
                });
        podWatch = client.inAnyNamespace().pods()
                .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod resource) {
                        podEventReceived(action, resource);
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        LOGGER.log(Level.INFO, "Pod watch closed", cause);
                    }
                });
        executorService.scheduleWithFixedDelay(this::pumpPodLogs, 2000, 1000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void close() {
        podWatch.close();
        deploymentWatch.close();
        ingressWatch.close();
        podWatches.values().forEach(LogWatch::close);
    }

    private void pumpPodLogs() {
        for (Map.Entry<String, LogWatch> logWatchEntry : podWatches.entrySet()) {
            var id = logWatchEntry.getKey();
            var stream = logWatchEntry.getValue().getOutput();
            try {
                if (stream.available() > 0) {
                    byte[] availableBytes = stream.readNBytes(stream.available());
                    // what will it throw when this is not valid UTF_8 sequence?
                    String chunk = new String(availableBytes, Charsets.UTF_8);
                    LOGGER.info("Log for " + id + ":" + chunk);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to process log pump of " + id, e);
                unregisterLogWatch(id);
            }
        }
    }

    private boolean becameReady(Watcher.Action action, Pod resource) {
        var uid = resource.getMetadata().getUid();
        if (action == Watcher.Action.ADDED) {
            startingPods.add(uid);
            return false;
        } else if (startingPods.contains(uid)) {
            boolean isReady = resource.getStatus().getContainerStatuses().stream().anyMatch(status -> status.getReady());
            if (isReady) {
                startingPods.remove(uid);
            }
            return isReady;
        } else {
            return false;
        }
    }



    private void podEventReceived(Watcher.Action action, Pod resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
        if (becameReady(action, resource)) {
            registerLogWatch(id, resource);
        }

    }


    private void registerLogWatch(String id, Pod resource) {
        podWatches.computeIfAbsent(id, (i) -> client.pods()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName()).watchLog());
    }

    void unregisterOnSuccess(@ObservesAsync @ChangeKind.Filter(ChangeKind.PROVISION_FINISHED) StateChanged event) {
        unregisterLogWatch(event.getProcess().getId());
    }

    private void unregisterLogWatch(String id) {
        var watch = podWatches.remove(id);
        watch.close();
    }

    void unregisterOnFail(@ObservesAsync @ChangeKind.Filter(ChangeKind.FAILED) StateChanged event) {
        unregisterLogWatch(event.getProcess().getId());
    }


    private void deploymentEventReceived(Watcher.Action action, Deployment resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
    }

    private String getDeploymentId(HasMetadata resource) {
        String id = resource.getMetadata().getLabels().get("app.kubernetes.io/part-of");
        if (id == null) {
            LOGGER.warning(String.format("Managed %s without deployment id - strange : %s", resource.getKind(), resource));
            return null;
        }
        return id;
    }

    private void ingressEventReceived(Watcher.Action action, Ingress resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
    }

    private void logEvent(Watcher.Action action, HasMetadata resource) {
        LOGGER.info(resource.getKind() + " " + action + "\n" + Serialization.asJson(resource));
    }
}
