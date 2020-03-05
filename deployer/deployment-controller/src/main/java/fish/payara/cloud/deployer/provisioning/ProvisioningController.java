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

package fish.payara.cloud.deployer.provisioning;

import fish.payara.cloud.deployer.artifactstorage.ArtifactStorage;
import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.StateChanged;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
class ProvisioningController {

    private static final Logger LOGGER = Logger.getLogger(ProvisioningController.class.getName());

    @Inject
    @ConfigProperty(name = "provisioning.timeout", defaultValue = "PT30S")
    Duration inactivityTimeout;

    @Inject
    DeploymentProcess process;

    @Inject
    ScheduledExecutorService executorService;

    @Inject
    ArtifactStorage artifactStorage;

    @Inject
    Provisioner provisioner;

    private ConcurrentMap<String,DeploymentInProvisioning> activityMonitor = new ConcurrentHashMap<>();


    void startProvisioning(@ObservesAsync @ChangeKind.Filter(ChangeKind.CONFIGURATION_FINISHED) StateChanged event) {
        // possibly we could do any transformations of the artifact (i. e. embedding configuration) here

        // then store artifact to persistent storage
        try {
            var persistentUri = artifactStorage.storeArtifact(event.getProcess());
            process.artifactStored(event.getProcess(), persistentUri);
        } catch (IOException e) {
            process.fail(event.getProcess(), "Failed to store artifact", e);
            return;
        }

        startMonitoring(event);
        process.provisioningStarted(event.getProcess());

        try {
            provisioner.provision(event.getProcess());
            // provisoning happens asynchronously so it's up to provisioner to mark end of provisioning.
        } catch (ProvisioningException e) {
            process.fail(event.getProcess(), "Failed to provision", e);
        }
    }


    void deleteFailedArtifacts(@ObservesAsync @ChangeKind.Filter(ChangeKind.FAILED) StateChanged event) {
        var uri = event.getProcess().getPersistentLocation();
        if (uri != null) {
            try {
                artifactStorage.deleteArtifact(event.getProcess());
                process.deletionFinished(event.getProcess());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not delete artifact "+uri, e);
            }
        }
    }

    void startMonitoring(StateChanged event) {
        var id = event.getProcess().getId();
        activityMonitor.put(id, new DeploymentInProvisioning(event));
        scheduleMonitor(id, inactivityTimeout);
    }

    private void checkMonitor(String id) {
        var monitor = activityMonitor.get(id);
        if (monitor != null) {
            var remainingTimeout = monitor.expiresIn(inactivityTimeout);
            if (remainingTimeout.toMillis() < 10) { // there's some rounding going on
                cancelProvisioning(id);
                process.fail(monitor.process, "Provisioning timed out", null);
                activityMonitor.remove(id);
            } else {
                scheduleMonitor(id, remainingTimeout);
            }
        }
    }

    private void scheduleMonitor(String id, Duration remainingTimeout) {
        executorService.schedule(() -> checkMonitor(id), remainingTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelProvisioning(String id) {

    }

    void monitorProgress(@ObservesAsync StateChanged event) {
        var id = event.getProcess().getId();
        var monitor = activityMonitor.get(id);
        if (monitor != null) {
            if (!monitor.update(event)) {
                activityMonitor.remove(id);
            }
        }
    }
    
    void deleteProvision(@ObservesAsync @ChangeKind.Filter(ChangeKind.DELETION_STARTED) StateChanged event) {
        try {
            artifactStorage.deleteArtifact(event.getProcess());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete artifact from storage "+event.getProcess().getPersistentLocation());
        }
        provisioner.delete(event.getProcess());
    }

    static class DeploymentInProvisioning {
        private final DeploymentProcessState process;
        int lastVersion;
        ChangeKind lastEvent;
        Instant versionTimestamp;

        DeploymentInProvisioning(StateChanged event) {
            this.lastEvent = event.getKind();
            this.lastVersion = event.getAtVersion();
            this.process = event.getProcess();
            versionTimestamp = Instant.now();
        }

        synchronized boolean update(StateChanged event) {
            if (event.getAtVersion() > lastVersion) {
                this.lastEvent = event.getKind();
                this.lastVersion = event.getAtVersion();
                versionTimestamp = Instant.now();
            }
            return !event.getKind().isTerminal();
        }

        Duration inactivityDuration() {
            return Duration.between(versionTimestamp, Instant.now());
        }

        Duration expiresIn(Duration inactivityTimeout) {
            return inactivityTimeout.minus(inactivityDuration());
        }
    }
}