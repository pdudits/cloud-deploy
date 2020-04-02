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
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static fish.payara.cloud.deployer.kubernetes.Template.fillTemplate;

class CreationSupport {


    DeploymentProcessState deployment;
    private final NamespacedKubernetesClient client;
    private final String domain;
    private OwnerReference owner;

    CreationSupport(DeploymentProcessState deployment, NamespacedKubernetesClient client, String domain) {
        this.deployment = deployment;
        this.client = client;
        this.domain = domain;
    }

    HasMetadata createFromTemplate(String namespace, String template) throws IOException {
        var resource = fillTemplate(getClass().getResourceAsStream("/kubernetes/templates-direct/"+template), this::variableValue);

        NamespacedKubernetesClient namespacedClient = namespace == null ? client : client.inNamespace(namespace);
        return namespacedClient.resource(applyOwner(Serialization.unmarshal(resource, HasMetadata.class))).createOrReplace();
    }

    void createNamespaced(String template) throws IOException {
        createFromTemplate(getNamespace(), template);
    }

    void createGlobal(String template) throws IOException {
        createFromTemplate(null, template);
    }

    String getNamespace() {
        return DirectProvisioner.convertNamespace(deployment.getNamespace());
    }

    NamespacedKubernetesClient namespaceClient() {
        return client.inNamespace(getNamespace());
    }

    public String variableValue(String var) {
        switch(var) {
            case "ID":
                return getId();
            case "PROJECT":
                return deployment.getNamespace().getProject();
            case "STAGE":
                return deployment.getNamespace().getStage();
            case "URL":
                return deployment.getPersistentLocation().toString();
            case "NAME":
                return getName();
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

    String getName() {
        // name is used as kubernetes object names, and those need to be lowercase
        return deployment.getConfigValue(ContextRootConfiguration.KIND, ContextRootConfiguration.APP_NAME).get().toLowerCase();
    }

    String getContextRoot() {
        return deployment.getConfigValue(ContextRootConfiguration.KIND, ContextRootConfiguration.CONTEXT_ROOT).get();
    }

    public Map<String, String> labelsForComponent(String componentName) {
        return Map.of("app.kubernetes.io/name", getName(),
                "app.kubernetes.io/component", componentName,
                "app.kubernetes.io/part-of", getId(),
                "app.kubernetes.io/managed-by", "payara-cloud");
    }

    private String getId() {
        return deployment.getId();
    }

    void setOwner(HasMetadata owner) {
        // blockOwnerDeletion=true means that owner cannot be deleted unless owned object is deleted first
        // controller=true doesn't appear to mean anything, but is set on usual ownership chains
        this.owner = new OwnerReference(owner.getApiVersion(), true, true,
                owner.getKind(), owner.getMetadata().getName(), owner.getMetadata().getUid());
    }

    <T extends HasMetadata> T applyOwner(T resource) {
        if (owner != null) {
            resource.getMetadata().setOwnerReferences(List.of(owner));
        }
        return resource;
    }
}
