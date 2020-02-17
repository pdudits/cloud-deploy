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

import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.provisioning.ProvisioningException;
import fish.payara.cloud.deployer.setup.DirectProvisioning;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import static fish.payara.cloud.deployer.kubernetes.Template.fillTemplate;
import java.util.logging.Level;

@DirectProvisioning
@ApplicationScoped
class DirectProvisioner implements Provisioner {
    public static final Logger LOGGER = Logger.getLogger(DirectProvisioner.class.getName());

    @Inject
    NamespacedKubernetesClient client;

    @Inject
    @ConfigProperty(name="kubernetes.direct.ingressdomain")
    String domain;

    @Inject
    DeploymentProcess process;


    @Override
    public void provision(DeploymentProcessState deployment) throws ProvisioningException {
        var naming = new Naming(deployment);
        try {
            provisionNamespace(naming);
            provisionDatagrid(naming);
            provisionService(naming);
            provisionDeployment(naming);
            var uri = provisionIngress(naming);
            process.endpointDetermined(deployment, uri);
            LOGGER.log(Level.INFO, "Provisioned {0} at {1}", new Object[]{deployment.getId(), uri});
        } catch (Exception e) {
            throw new ProvisioningException("Failed to provision "+deployment.getId(), e);
        }
    }

    private URI provisionIngress(Naming naming) throws IOException {
        naming.createNamespaced("ingress.yaml");
        // in real world, we should wait until ingress is actually ready by watching its state.
        return URI.create(String.format("http://%s.%s%s", naming.getNamespace(), domain, naming.getContextRoot()));
    }

    private void provisionService(Naming naming) throws IOException {
        naming.createNamespaced("service.yaml");
    }

    private void provisionDatagrid(Naming naming) throws IOException {
        naming.createNamespaced("datagrid.yaml");
    }

    private void provisionDeployment(Naming naming) throws IOException {
        createFromTemplate(naming.getNamespace(), naming, "deployment.yaml");
    }

    private void provisionNamespace(Naming n) throws IOException {
        var serverNamespace = client.namespaces().withName(n.getNamespace()).get();
        if (serverNamespace == null) {
            n.createGlobal("namespace.yaml");
        }
    }

    private HasMetadata createFromTemplate(String namespace, Naming naming, String template) throws IOException {
        var resource = fillTemplate(getClass().getResourceAsStream("/kubernetes/templates-direct/"+template), naming::variableValue);
        var namespacedClient = namespace == null ? client : client.inNamespace(namespace);
        return namespacedClient.resource((HasMetadata) Serialization.unmarshal(resource, KubernetesResource.class)).createOrReplace();
    }

    @Override
    public boolean delete(DeploymentProcessState deployment) {
        var deployments = client.apps().deployments().withLabel("app.kubernetes.io/part-of", deployment.getId());
        return deployments.delete();
    }

    class Naming {
        DeploymentProcessState deployment;
        private Naming(DeploymentProcessState deployment) {
            this.deployment = deployment;
        }

        private void createNamespaced(String template) throws IOException {
            createFromTemplate(getNamespace(), this, template);
        }

        private void createGlobal(String template) throws IOException {
            createFromTemplate(null, this, template);
        }

        String getNamespace() {
            return deployment.getNamespace().getProject()+"-"+deployment.getNamespace().getStage();
        }

        public String variableValue(String var) {
            switch(var) {
                case "ID":
                    return deployment.getId();
                case "PROJECT":
                    return deployment.getNamespace().getProject();
                case "STAGE":
                    return deployment.getNamespace().getStage();
                case "URL":
                    return deployment.getPersistentLocation().toString();
                case "NAME":
                    return deployment.getConfigValue(ContextRootConfiguration.KIND, ContextRootConfiguration.APP_NAME).get();
                case "DOMAIN":
                    return domain;
                case "PATH":
                    return getContextRoot();
                case "VERSION":
                    return "VERSION";
                default:
                    throw new IllegalArgumentException("No value provided for variable "+var);
            }
        }

        private String getContextRoot() {
            return deployment.getConfigValue(ContextRootConfiguration.KIND, ContextRootConfiguration.CONTEXT_ROOT).get();
        }
    }

}
