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

package fish.payara.cloud.deployer.process;

import java.io.File;
import java.util.Map;

/**
 * Methods for managing deployment processes and deployments.
 * This interface lists the actions that are carried out by user, namely creating starting new deployments, configuring
 * them, deleting them and obtaining current state of a process
 */
public interface DeploymentManagement {
    DeploymentProcessState start(File artifactLocation, String name, Namespace target);

    /**
     * Start new deployment process.
     * @param artifactLocation non-null local location of deployment artifact
     * @param name the name of the upload
     * @param target namespace to deploy to
     * @return new state representing the deployment process
     */
    DeploymentProcessState startWithDefaultConfiguration(File artifactLocation, String name, Namespace target);

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
    DeploymentProcessState setConfiguration(DeploymentProcessState process, String kind, String id, Map<String, String> values);

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
    DeploymentProcessState setConfiguration(DeploymentProcessState process, String kind, String id, boolean submit, Map<String, String> values);

    /**
     * Submit all configurations of a process.
     * @param process process to update
     * @throws IllegalStateException when some of the configurations are incomplete
     * @return
     */
    DeploymentProcessState submitConfigurations(DeploymentProcessState process);

    DeploymentProcessState delete(DeploymentProcessState process);

    DeploymentProcessState resetConfigurations(DeploymentProcessState process);
    
    DeploymentProcessState getProcessState(String id);
}
