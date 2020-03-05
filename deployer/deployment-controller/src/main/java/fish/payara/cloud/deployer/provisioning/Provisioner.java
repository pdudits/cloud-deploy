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

import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import java.util.List;
import java.util.Map;

/**
 * Provisions application resources for a deployment.
 *
 * Provisioner is invoked by Provisioning controller after artifact is uploaded and provisioning is started.
 * The implementation is responsible for communicating with cloud backend, creating necessary resources and
 * informing the system about progress of provisioning.
 *
 * It should also inform when provisioning is finished and application is available.
 */
public interface Provisioner {
    /**
     * Provision the deployment. Check for any definition trouble, initiate the provisioning with the backend
     * and asynchronously update the deployment with information. At the end, invoke
     * {@link fish.payara.cloud.deployer.process.DeploymentProcess#provisioningFinished(DeploymentProcessState)}
     * to signal end of provisioning.
     *
     * @param deployment deployment to provision
     * @throws ProvisioningException in case of deployment misconfiguration or backend error
     */
    void provision(DeploymentProcessState deployment) throws ProvisioningException;
    
    /**
     * Gets the provisioned namespaces
     * @return 
     */
    List<Namespace> getNamespaces();
    
    /* Unprovision the deployment.
     * @param deployment deployment to delete
     * @return state of the deployment process with last change of
     * {@link fish.payara.cloud.deployer.process.ChangeKind#DELETION_FINISHED}
     */
    DeploymentProcessState delete(DeploymentProcessState deployment);

     /**
     * Gets a map of all deployments in a namespace
     * @param namespace name of Namespace to get deployments in
     * @return map where key is the deployment id and the values being a list
     * of ingress URLs.
     */
    Map<String, List<String>> getDeploymentsWithIngress(Namespace namespace);
}
