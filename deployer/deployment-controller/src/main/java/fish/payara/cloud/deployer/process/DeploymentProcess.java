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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.File;
import java.util.Map;
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
     * @param target namespace to deploy to
     * @return new state representing the deployment process
     */
    public DeploymentProcessState start(File artifactLocation, Namespace target) {
        var process = new DeploymentProcessState(target, artifactLocation);
        runningProcesses.put(process.getId(), process);
        process.fireAsync(deploymentEvent, ChangeKind.PROCESS_STARTED);
        return process;
    }

    public void addConfiguration(DeploymentProcessState process, Configuration configuration) {
        updateProcess(process, p -> p.addConfiguration(configuration));
    }

    public void setConfiguration(DeploymentProcessState process, String kind, String id, Map<String,String> values) {
        setConfiguration(process, kind, id, true, values);
    }

    public void setConfiguration(DeploymentProcessState process, String kind, String id, boolean submit, Map<String,String> values) {
        updateProcess(process, p -> p.setConfiguration(kind, id, submit, values));
        if (submit) {
            updateProcess(process, p -> p.submitConfigurations(false));
        }
    }

    public void submitConfiguration(DeploymentProcessState process) {
        updateProcess(process, p -> p.submitConfigurations(true));
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

    private DeploymentProcessState updateProcess(DeploymentProcessState process, Function<DeploymentProcessState, ChangeKind> update) {
        var storedProcess = runningProcesses.get(process.getId());
        ChangeKind eventKind = null;
        synchronized (storedProcess) {
            eventKind = update.apply(storedProcess);
        }
        if (eventKind != null) {
            storedProcess.fireAsync(deploymentEvent, eventKind);
        }
        return storedProcess;
    }

    /**
     *
     * @param process
     */
    public void inspectionFinished(DeploymentProcessState process) {
        updateProcess(process, p -> ChangeKind.INSPECTION_FINISHED);
    }
}
