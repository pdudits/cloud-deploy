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
import fish.payara.cloud.deployer.inspection.datasource.DatasourceConfiguration;
import fish.payara.cloud.deployer.inspection.mpconfig.MicroprofileConfiguration;
import fish.payara.cloud.deployer.kubernetes.crd.WebAppCustomResource;
import fish.payara.cloud.deployer.kubernetes.crd.WebAppSpecConfiguration;
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.provisioning.DeploymentInfo;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.provisioning.ProvisioningException;
import fish.payara.cloud.deployer.setup.DirectProvisioning;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static fish.payara.cloud.deployer.kubernetes.Template.fillTemplate;
import fish.payara.cloud.deployer.process.Namespace;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

@DirectProvisioning
@ApplicationScoped
class DirectProvisioner implements Provisioner {
    public static final Logger LOGGER = Logger.getLogger(DirectProvisioner.class.getName());
    private static final String APP_KUBERNETES_IO_PART_OF = "app.kubernetes.io/part-of";

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

            var customResource = provisionCustomResource(naming);

            provisionDatagrid(naming);
            provisionService(naming);

            var deploymentResource = createBaseDeployment(naming);
            if (deployment.hasConfigurationOverrides(MicroprofileConfiguration.KIND)) {
                String configMapName = provisionSystemProperties(naming, deployment.findConfiguration(MicroprofileConfiguration.KIND).get());
                applySystemPropertyFromConfigMap(deploymentResource, configMapName);
            }
            if (deployment.hasConfigurationOverrides(DatasourceConfiguration.KIND)) {
                var dsConfigurations = deployment.findConfigurations(DatasourceConfiguration.KIND)
                        .filter(Configuration::hasOverrides)
                        .collect(Collectors.toSet());
                String postBootConfigMapName = provisionDatasourcePostBoot(naming, dsConfigurations);
                applyPostbootFromConfigMap(deploymentResource, postBootConfigMapName);
            }
            provisionDeployment(naming, deploymentResource);
            var uri = provisionIngress(naming);

            updateCustomResourceEndpoint(customResource, uri);
            process.endpointDetermined(deployment, uri);
            LOGGER.log(Level.INFO, "Provisioned {0} at {1}", new Object[]{deployment.getId(), uri});
        } catch (Exception e) {
            throw new ProvisioningException("Failed to provision "+deployment.getId(), e);
        }
    }

    private void updateCustomResourceEndpoint(WebAppCustomResource customResource, URI uri) {
        customResource.makeStatus().setPublicEndpoint(uri);
        WebAppCustomResource.client(client).updateStatus(customResource);
    }

    private WebAppCustomResource provisionCustomResource(Naming naming) {
        WebAppCustomResource result = makeCustomResource(naming.deployment);
        // k8s names must be lowercase
        result.getMetadata().setName(naming.getName());
        result.getMetadata().setNamespace(naming.getNamespace());
        result.getMetadata().setLabels(naming.labelsForComponent("webapp"));

        result = WebAppCustomResource.client(client).create(result);
        naming.setOwner(result);
        return result;
    }

    private void applyPostbootFromConfigMap(Deployment deploymentResource, String postBootConfigMapName) {
        // add config map volume
        var podTemplate = deploymentResource.getSpec().getTemplate().getSpec();
        podTemplate.getVolumes()
                .add(new VolumeBuilder()
                        .withName("postboot")
                        .withNewConfigMap()
                        .withName(postBootConfigMapName)
                        .endConfigMap()
                        .build());
        // mount the volume into first, main container
        var mainContainer = podTemplate.getContainers().get(0);
        mainContainer.getVolumeMounts()
                .add(new VolumeMountBuilder()
                        .withName("postboot")
                        .withMountPath("/postboot")
                        .build());
        // add command line argument
        mainContainer.getArgs().addAll(List.of("--postbootcommandfile","/postboot/postboot"));
    }
    private String provisionDatasourcePostBoot(Naming naming, Set<Configuration> dsConfigurations) {
        var postboot = new DatasourcePostbootCommands();
        dsConfigurations.forEach(postboot::addDatasource);
        var configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(naming.getName()+"-postboot")
                .withLabels(naming.labelsForComponent("postboot"))
                .endMetadata()
                .withData(Map.of("postboot", postboot.toString()))
                .build();
        configMap = naming.applyOwner(configMap);
        var created = naming.namespaceClient().resource(configMap).createOrReplace();
        return created.getMetadata().getName();
    }

    private void applySystemPropertyFromConfigMap(Deployment deploymentResource, String configMapName) {
        // add config map volume
        var podTemplate = deploymentResource.getSpec().getTemplate().getSpec();
        podTemplate.getVolumes()
                .add(new VolumeBuilder()
                        .withName("mp-config")
                        .withNewConfigMap()
                            .withName(configMapName)
                        .endConfigMap()
                .build());
        // mount the volume into first, main container
        var mainContainer = podTemplate.getContainers().get(0);
        mainContainer.getVolumeMounts()
                .add(new VolumeMountBuilder()
                        .withName("mp-config")
                        .withMountPath("/mp-config")
                    .build());
        // add command line argument
        mainContainer.getArgs().addAll(List.of("--systemproperties","/mp-config/microprofile-config.properties"));
    }

    private String provisionSystemProperties(Naming naming, Configuration configuration) {
        var configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(naming.getName()+"-mpconfig")
                .withLabels(naming.labelsForComponent("mpconfig"))
                .endMetadata()
                .withData(Map.of("microprofile-config.properties", buildPropertyFile(configuration)))
                .build();
        naming.applyOwner(configMap);
        var created = naming.namespaceClient().resource(configMap).createOrReplace();
        return created.getMetadata().getName();
    }

    private String buildPropertyFile(Configuration configuration) {
        return
            configuration.getKeys().stream()
                    .map(key -> key+"="+configuration.getValue(key).orElse(""))
                    .collect(Collectors.joining("\n"));
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

    private void provisionDeployment(Naming naming, Deployment deployment) throws IOException {
        naming.namespaceClient().resource(deployment).deletingExisting().createOrReplace();
    }

    private Deployment createBaseDeployment(Naming naming) {
        var template = fillTemplate(getClass().getResourceAsStream("/kubernetes/templates-direct/deployment.yaml"), naming::variableValue);
        return naming.applyOwner(Serialization.unmarshal(template, Deployment.class));
    }

    private void provisionNamespace(Naming n) throws IOException {
        var serverNamespace = client.namespaces().withName(n.getNamespace()).get();
        if (serverNamespace == null) {
            n.createGlobal("namespace.yaml");
        }
    }

    private HasMetadata createFromTemplate(String namespace, Naming naming, String template) throws IOException {
        var resource = fillTemplate(getClass().getResourceAsStream("/kubernetes/templates-direct/"+template), naming::variableValue);

        NamespacedKubernetesClient namespacedClient = namespace == null ? client : client.inNamespace(namespace);
        return namespacedClient.resource(naming.applyOwner(Serialization.unmarshal(resource, HasMetadata.class))).createOrReplace();
    }

    /**
     * Gets a list of namespaces that have been provisioned, in JSON format
     * @return provisioned namespaces
     */
    @Override
    public List<Namespace> getNamespaces() {
        List<Namespace> namespacesList = new ArrayList<>();
        for (var namespace: client.namespaces().list().getItems()) {
            var labels = namespace.getMetadata().getLabels();
            if (labels != null && "payara-cloud".equals(labels.get("app.kubernetes.io/managed-by"))) {
                var project = labels.get("app.kubernetes.io/name");
                var stage = labels.get("app.kubernetes.io/part-of");;
                namespacesList.add(new Namespace(project, stage));
            }
        }
        return namespacesList;
    }

    @Override
    public DeploymentProcessState delete(DeploymentProcessState deployment) {
        String id = deployment.getId();
        WebAppCustomResource.client(client).withLabel(APP_KUBERNETES_IO_PART_OF, id).delete();
        return process.deletionFinished(deployment);
    }

    @Override
    public List<DeploymentInfo> getDeploymentsWithIngress(Namespace namespace) {
        List<DeploymentInfo> deployments = new ArrayList<>();
        for (Ingress ingress : client.extensions().ingresses().inNamespace(convertNamespace(namespace))
                .withLabel("app.kubernetes.io/managed-by", "payara-cloud").list().getItems()) {
            var info = new DeploymentInfo(namespace, ingress.getMetadata().getLabels().get(APP_KUBERNETES_IO_PART_OF),
                    ingress.getMetadata().getName());
            List<String> pathList = new ArrayList<>();
            for (var rule: ingress.getSpec().getRules()) {
                var prefix = "http://"+rule.getHost();
                for (var path : rule.getHttp().getPaths()) {
                    info.addUrl(prefix+path.getPath());
                }
            }
            deployments.add(info);
        }
        return deployments;
    }

    static String convertNamespace(Namespace namespace) {
        return namespace.getProject()+"-"+namespace.getStage();
    }

    public static WebAppCustomResource makeCustomResource(DeploymentProcessState state) {
        WebAppCustomResource result = new WebAppCustomResource();
        var spec = result.getSpec();
        spec.setDeploymentProcessId(UUID.fromString(state.getId()));
        spec.setArtifactUrl(state.getPersistentLocation());
        state.getConfigurations().stream().map(DirectProvisioner::makeConfiguration).forEach(spec::addConfigurationItem);

        var status = result.makeStatus();
        status.setPublicEndpoint(state.getEndpoint());

        return result;
    }

    public static WebAppSpecConfiguration makeConfiguration(Configuration configuration) {
        WebAppSpecConfiguration result = new WebAppSpecConfiguration();
        result.setKind(configuration.getKind());
        result.setId(configuration.getId());
        var values = new HashMap<String, String>();
        var defaultValues = new HashMap<String, String>();
        for (String key : configuration.getKeys()) {
            var defaultValue = configuration.getDefaultValue(key);
            defaultValue.ifPresent(value -> defaultValues.put(key, value));
            configuration.getValue(key).ifPresent(value -> {
                if (defaultValue.isEmpty() || !value.equals(defaultValue.get())) {
                    values.put(key, value);
                }
            });
        }
        result.setValues(values);
        result.setDefaultValues(defaultValues);
        return result;
    }

    class Naming {


        DeploymentProcessState deployment;
        private OwnerReference owner;

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
            return convertNamespace(deployment.getNamespace());
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

        private String getName() {
            // name is used as kubernetes object names, and those need to be lowercase
            return deployment.getConfigValue(ContextRootConfiguration.KIND, ContextRootConfiguration.APP_NAME).get().toLowerCase();
        }

        private String getContextRoot() {
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

}
