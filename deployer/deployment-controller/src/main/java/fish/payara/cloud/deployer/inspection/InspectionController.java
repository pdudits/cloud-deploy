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

package fish.payara.cloud.deployer.inspection;

import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.StateChanged;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/**
 * Drive the inspection.
 *
 * <p>The controller will instantiate all implementations of {@link Inspection} instances in the application,
 * and let them inspect contents of the artifact in single pass.</p>
 */
@ApplicationScoped
class InspectionController {
    @Inject
    @ConfigProperty(name = "inspection.timeout", defaultValue = "PT5S")
    Duration inspectionTimeout;

    @Inject
    ExecutorService executorService;

    @Inject
    DeploymentProcess process;

    @Inject
    Instance<Inspection> inspections;

    void startInspection(@ObservesAsync @ChangeKind.Filter(ChangeKind.PROCESS_STARTED) StateChanged event) {
        CompletableFuture.runAsync(() -> this.doInspection(event.getProcess()), executorService)
                .orTimeout(inspectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        process.fail(event.getProcess(), "Inspection failed", exception);
                    } else {
                        process.inspectionFinished(event.getProcess());
                    }
                });

    }

    void doInspection(DeploymentProcessState deployment) {
        process.inspectionStarted(deployment);
        var file = deployment.getTempLocation();
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Deployment does not contain a valid file: " + file);
        }
        try (var zipFile = new ZipFile(file)) {
            var inspectionInstances = instantiateInspections();
            try {
                var artifact = InspectedArtifact.inspect(deployment.getName(), zipFile, inspectionInstances);
                artifact.getConfigurations().forEach(conf -> process.addConfiguration(deployment, conf));
            } finally {
                inspectionInstances.forEach(inspections::destroy);
            }
        } catch (IOException e) {
            throw new InspectionException("Failed to process artifact", e);
        }
    }

    private List<Inspection> instantiateInspections() {
        return inspections.stream().collect(Collectors.toUnmodifiableList());
    }
}
