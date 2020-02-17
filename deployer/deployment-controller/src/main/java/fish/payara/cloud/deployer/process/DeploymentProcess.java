/*
 * Copyright (c) 2019-2020 Payara Foundation and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Driving component of Deployment Process.
 * It is responsible for creating new DeploymentProcess, accessing existing ones, as well as performing changes
 * to the process' state.
 * Upon change of state, it broadcasts asynchronous event {@link StateChanged}.
 */
@ApplicationScoped
public class DeploymentProcess {
    private ConcurrentHashMap<String, DeploymentProcessState> runningProcesses = new ConcurrentHashMap<>();

    @Inject
    Event<StateChanged> deploymentEvent;

    /**
     * Start new deployment process.
     * @param artifactLocation non-null local location of deployment artifact
     * @param name the name of the upload
     * @param target namespace to deploy to
     * @return new state representing the deployment process
     */
    public DeploymentProcessState start(File artifactLocation, String name, Namespace target) {
        var processState = new DeploymentProcessState(target, name, artifactLocation);
        return start(processState);
    }

    DeploymentProcessState start(DeploymentProcessState processState) {
        runningProcesses.put(processState.getId(), processState);
        fireAsync(processState.start());
        return processState;
    }

    /**
     * Add new configuration to a process
     * @param process process to update
     * @param configuration configuration to add
     * @throws IllegalArgumentException when configuration with same kind and id is already present in the process
     */
    public void addConfiguration(DeploymentProcessState process, Configuration configuration) {
        updateProcess(process, p -> p.addConfiguration(configuration));
    }

    /**
     * Update configuration values, and submit the configuration automatically
     * @param process process to update
     * @param kind kind of configuration
     * @param id id of configuration
     * @param values values to set
     * @throws IllegalArgumentException when such configuration is not present
     * @throws ConfigurationValidationException when values are not valid
     * @return
     */
    public DeploymentProcessState setConfiguration(DeploymentProcessState process, String kind, String id, Map<String,String> values) {
        return setConfiguration(process, kind, id, true, values);
    }

    /**
     * Update configuration values, and optionally submit the configuration if it is complete.
     * @param process process to update
     * @param kind kind of configuration
     * @param id id of configuration
     * @param submit whether to submit if configuration is complete after update
     * @param values values to set
     * @throws IllegalArgumentException when such configuration is not present
     * @throws ConfigurationValidationException when values are not valid
     * @return
     */
    public DeploymentProcessState setConfiguration(DeploymentProcessState process, String kind, String id, boolean submit, Map<String,String> values) {
        process = updateProcess(process, p -> p.setConfiguration(kind, id, submit, values));
        if (submit) {
            process = updateProcess(process, p -> p.submitConfigurations(false));
        }
        return process;
    }

    /**
     * Submit all configurations of a process.
     * @param process process to update
     * @throws IllegalStateException when some of the configurations are incomplete
     * @return
     */
    public DeploymentProcessState submitConfigurations(DeploymentProcessState process) {
        return updateProcess(process, p -> p.submitConfigurations(true));
    }

    /**
     * Mark deployment process as failed.
     * @param process Process to update
     * @param message completion message
     * @param exception throwable that caused the failure
     */
    public void fail(DeploymentProcessState process, String message, Throwable exception) {
        updateProcess(process, p -> p.fail(message, exception));
    }

    private DeploymentProcessState updateProcess(DeploymentProcessState process, Function<DeploymentProcessState, StateChanged> update) {
        var storedProcess = runningProcesses.get(process.getId());
        StateChanged event = null;
        boolean isReady = false;
        boolean wasReady = false;
        synchronized (storedProcess) {
            wasReady = storedProcess.isReady();
            event = update.apply(storedProcess);
            isReady = storedProcess.isReady();
        }
        if (event != null) {
            fireAsync(event);
        }
        if (isReady && !wasReady) {
            return updateProcess(process, DeploymentProcessState::provisionFinished);
        }
        return storedProcess;
    }

    CompletionStage<StateChanged> fireAsync(StateChanged changeEvent) {
        return deploymentEvent.select(changeEvent.getKind().asFilter()).fireAsync(changeEvent);
    }

    /**
     * Mark that inspection process is finished
     * @param process
     * @return
     */
    public DeploymentProcessState inspectionStarted(DeploymentProcessState process) {
        return updateProcess(process, p -> p.transition(ChangeKind.INSPECTION_STARTED));
    }

    /**
     * Mark that inspection process is finished
     * @param process
     * @return
     */
    public DeploymentProcessState inspectionFinished(DeploymentProcessState process) {
        return updateProcess(process, p -> p.transition(ChangeKind.INSPECTION_FINISHED));
    }
    
    public DeploymentProcessState getProcessState(String id) {
        return runningProcesses.get(id);
    }

    /**
     * Mark that configuration process starts
     * @param process
     * @return
     */
    public DeploymentProcessState configurationStarted(DeploymentProcessState process) {
        return updateProcess(process, p -> p.transition(ChangeKind.CONFIGURATION_STARTED));
    }

    public DeploymentProcessState provisioningStarted(DeploymentProcessState process) {
        return updateProcess(process, p -> p.transition(ChangeKind.PROVISION_STARTED));
    }
    
    public DeploymentProcessState deleteArtifact(DeploymentProcessState process) {
        updateProcess(process, DeploymentProcessState::deleteArtifact);
        return artifactDeleted(process);
    }

    public DeploymentProcessState artifactDeleted(DeploymentProcessState process) {
        return updateProcess(process, DeploymentProcessState::removePersistentLocation);
    }

    public DeploymentProcessState artifactStored(DeploymentProcessState process, URI persistentUri) {
        return updateProcess(process, p -> p.setPersistentLocation(persistentUri));
    }

    public DeploymentProcessState endpointDetermined(DeploymentProcessState process, URI endpoint) {
        return updateProcess(process, p -> p.setEndpoint(endpoint));
    }

    public DeploymentProcessState provisioningFinished(DeploymentProcessState process) {
        return updateProcess(process, p -> p.provisionFinished());
    }

    public DeploymentProcessState resetConfigurations(DeploymentProcessState process) {
        return updateProcess(process, p -> p.resetConfigurations());
    }
  
    public DeploymentProcessState endpointActivated(DeploymentProcessState process) {
        return updateProcess(process, p -> p.endpointActivated());
    }

    public DeploymentProcessState deploymentFinished(DeploymentProcessState process) {
        return updateProcess(process, p -> p.deploymentFinished());
    }


    public DeploymentProcessState podCreated(DeploymentProcessState process, String namespace, String name) {
        return updateProcess(process, p -> p.podCreated(namespace+"/"+name));
    }

    public DeploymentProcessState outputLogged(DeploymentProcessState process, String logChunk) {
        return updateProcess(process, p -> p.logged(logChunk));
    }
}
