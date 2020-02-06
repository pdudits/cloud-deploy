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
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.StateChanged;
import fish.payara.cloud.deployer.setup.DirectProvisioning;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Watcher listens to Kubernetes events related to objects created by the deployer and updates deployment process state
 * based on them.
 */
@ApplicationScoped
@DirectProvisioning
class KubernetesWatcher implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(KubernetesWatcher.class.getName());

    @Inject
    NamespacedKubernetesClient client;
    @Inject
    ScheduledExecutorService executorService;
    @Inject
    DeploymentProcess process;
    @Inject
    @ConfigProperty(name = "kubernetes.watcher.pumpinterval", defaultValue = "PT1S")
    Duration pumpInterval = Duration.ofSeconds(1);

    private WatchHandle<Ingress> ingressWatch;
    private WatchHandle<Deployment> deploymentWatch;
    private WatchHandle<Pod> podWatch;
    private Set<String> startingPods = new ConcurrentHashMap<String, Boolean>().keySet(true);
    private ConcurrentMap<String, WatchedPod> podWatches = new ConcurrentHashMap<>();

    /**
     * Subscribes to changes of managed resources.
     */
    void startWatching() {
        startWatchingIngress();
        startWatchingDeployment();
        startWatchingPods();
        executorService.scheduleWithFixedDelay(this::pumpPodLogs, 2000, pumpInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void startWatching(@Observes @Initialized(ApplicationScoped.class) Object init) {
        startWatching();
    }

    class WatchHandle<T> {
        Watch watch;
        volatile Watcher<T> watcher;
        private final String kind;
        private final BiFunction<Watcher<T>, String, Watch> registration;
        private final BiConsumer<Watcher.Action, T> handler;
        private String resourceVersion;


        WatchHandle(String kind, BiFunction<Watcher<T>,String,Watch> registration, BiConsumer<Watcher.Action, T> handler) {
            this.kind = kind;
            this.registration = registration;
            this.handler = handler;
        }

        void register() {
            watcher = new Watcher<T>() {
                @Override
                public void eventReceived(Action action, T resource) {
                    handler.accept(action, resource);
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                    LOGGER.log(Level.WARNING, kind + " watch closed", cause);
                    if (retryableException(cause) && WatchHandle.this.watcher == this) {
                        // For ingresses I observe getting 2-day old event first, client remembers the resource version
                        // and subsequent reconnects fail on too old resource version.
                        // Let's play along with the API server here
                        if ("Gone".equals(cause.getStatus().getReason())) {
                            var matcher = Pattern.compile("too old resource version: .+\\((.+)\\)").matcher(cause.getStatus().getMessage());
                            if (matcher.matches()) {
                                resourceVersion = matcher.group(1);
                            }
                        }
                        executorService.schedule(WatchHandle.this::register, 1, TimeUnit.SECONDS);
                    }
                }
            };
            watch = registration.apply(watcher, resourceVersion);
        }

        void close() {
            if (watch != null) {
                watch.close();
            }
        }

    }


    private void startWatchingPods() {
        podWatch = new WatchHandle<>("Pod",
                (w,r) -> client.inAnyNamespace().pods()
                        .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                        .withResourceVersion(r)
                        .watch(w),
                this::podEventReceived);
        podWatch.register();
    }

    private void startWatchingDeployment() {
        deploymentWatch = new WatchHandle<>("Deployment",
                (w,r) -> client.inAnyNamespace().apps().deployments()
                        .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                        .withResourceVersion(r)
                        .watch(w),
                this::deploymentEventReceived);
        deploymentWatch.register();
    }

    private void startWatchingIngress() {
        ingressWatch = new WatchHandle<>("Ingress",
                (w,r) -> client.inAnyNamespace().extensions().ingresses()
                        .withLabel("app.kubernetes.io/managed-by", "payara-cloud")
                        .withResourceVersion(r)
                        .watch(w),
                this::ingressEventReceived);
        ingressWatch.register();
    }

    private boolean retryableException(KubernetesClientException cause) {
        return cause != null && cause.getCode() == 410;
    }


    @PreDestroy
    @Override
    public void close() {
        podWatch.close();
        deploymentWatch.close();
        ingressWatch.close();
        podWatches.values().forEach(WatchedPod::close);
    }

    // Handlers -- update the process when needed here
    protected void handlePodEvent(Watcher.Action action, Pod resource, String id) {
        if (action == Watcher.Action.ADDED) {
            process.podCreated(process.getProcessState(id), resource.getMetadata().getNamespace(), resource.getMetadata().getName());
        }
    }

    protected void handleDeploymentEvent(Watcher.Action action, Deployment resource, String id) {
        if (isDeploymentReady(resource)) {
            process.deploymentFinished(process.getProcessState(id));
        }
    }

    protected void handleLog(String id, ObjectMeta podMeta, byte[] availableBytes) {
        process.outputLogged(process.getProcessState(id), utf8String(availableBytes));
    }

    protected void handleIngressEvent(Watcher.Action action, Ingress resource, String id) {
        if (hasActiveIngress(resource)) {
            process.endpointActivated(process.getProcessState(id));
        }
    }

    private boolean isDeploymentReady(Deployment resource) {
        return resource.getStatus() != null && resource.getStatus().getReadyReplicas() != null && resource.getStatus().getReadyReplicas() > 0;
    }

    private boolean hasActiveIngress(Ingress resource) {
        if (resource.getStatus() == null || resource.getStatus().getLoadBalancer() == null) {
            return false;
        }
        return resource.getStatus().getLoadBalancer().getIngress().stream()
                .anyMatch(lbIngress -> lbIngress.getHostname() != null || lbIngress.getIp() != null);
    }

    /**
     * Collect new output from any pods' logs. Called in regular intervals.
     */
    private void pumpPodLogs() {
        for (Map.Entry<String, WatchedPod> logWatchEntry : podWatches.entrySet()) {
            var id = logWatchEntry.getKey();
            var stream = logWatchEntry.getValue().getOutput();
            try {
                if (stream.available() > 0) {
                    byte[] availableBytes = stream.readNBytes(stream.available());
                    logLog(id, logWatchEntry.getValue().podMeta, availableBytes);
                    handleLog(id, logWatchEntry.getValue().podMeta, availableBytes);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to process log pump of " + id, e);
                unregisterLogWatch(id);
            }
        }
    }

    private void podEventReceived(Watcher.Action action, Pod resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
        handlePodEvent(action, resource, id);
        if (becameReady(action, resource)) {
            registerLogWatch(id, resource);
        }
    }

    /**
     * Track when created pod enters ready state (its main container is started)
     * @param action
     * @param resource
     * @return
     */
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

    private void registerLogWatch(String id, Pod resource) {
        podWatches.computeIfAbsent(id, (i) -> new WatchedPod(resource));
    }

    void unregisterOnSuccess(@ObservesAsync @ChangeKind.Filter(ChangeKind.PROVISION_FINISHED) StateChanged event) {
        unregisterLogWatch(event.getProcess().getId());
    }

    void unregisterOnFail(@ObservesAsync @ChangeKind.Filter(ChangeKind.FAILED) StateChanged event) {
        unregisterLogWatch(event.getProcess().getId());
    }

    private void unregisterLogWatch(String id) {
        LOGGER.info("Disconnecting from log of "+id);
        var watch = podWatches.remove(id);
        watch.close();
    }


    private void deploymentEventReceived(Watcher.Action action, Deployment resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
        handleDeploymentEvent(action, resource, id);
    }

    private void ingressEventReceived(Watcher.Action action, Ingress resource) {
        var id = getDeploymentId(resource);
        if (id == null) {
            return;
        }
        logEvent(action, resource);
        handleIngressEvent(action, resource, id);
    }

    private String getDeploymentId(HasMetadata resource) {
        String id = resource.getMetadata().getLabels().get("app.kubernetes.io/part-of");
        if (id == null) {
            LOGGER.warning(String.format("Managed %s without deployment id - strange : %s", resource.getKind(), resource));
            return null;
        }
        return id;
    }


    protected void logEvent(Watcher.Action action, HasMetadata resource) {
        LOGGER.info(resource.getKind() + " " + action + " " + resource.getMetadata().getNamespace()+resource.getMetadata().getName());
    }

    protected void logLog(String id, ObjectMeta podMeta, byte[] chunk) {

    }

    private static String utf8String(byte[] chunk) {
        return new String(chunk, StandardCharsets.UTF_8);
    }

    class WatchedPod {
        final LogWatch logWatch;
        final ObjectMeta podMeta;

        WatchedPod(Pod resource) {
            this.podMeta = resource.getMetadata();
            this.logWatch = client.pods()
                    .inNamespace(resource.getMetadata().getNamespace())
                    .withName(resource.getMetadata().getName()).watchLog();
        }

        void close() {
            this.logWatch.close();
        }

        InputStream getOutput() {
            return this.logWatch.getOutput();
        }
    }
}
