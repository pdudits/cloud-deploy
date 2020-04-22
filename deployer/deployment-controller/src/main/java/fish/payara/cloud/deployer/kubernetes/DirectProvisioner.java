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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
        var support = new CreationSupport(deployment, client, domain);
        try {
            provisionNamespace(support);

            var customResource = provisionCustomResource(support);

            provisionDatagrid(support);
            provisionService(support);

            var deploymentResource = createBaseDeployment(support);
            if (deployment.hasConfigurationOverrides(MicroprofileConfiguration.KIND)) {
                String configMapName = provisionSystemProperties(support, deployment.findConfiguration(MicroprofileConfiguration.KIND).get());
                applySystemPropertyFromConfigMap(deploymentResource, configMapName);
            }
            if (deployment.hasConfigurationOverrides(DatasourceConfiguration.KIND)) {
                var dsConfigurations = deployment.findConfigurations(DatasourceConfiguration.KIND)
                        .filter(Configuration::hasOverrides)
                        .collect(Collectors.toSet());
                String postBootConfigMapName = provisionDatasourcePostBoot(support, dsConfigurations);
                applyPostbootFromConfigMap(deploymentResource, postBootConfigMapName);
            }
            provisionDeployment(support, deploymentResource);
            var uri = provisionIngress(support);

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

    private WebAppCustomResource provisionCustomResource(CreationSupport support) {
        WebAppCustomResource result = makeCustomResource(support.deployment);
        // k8s names must be lowercase
        result.getMetadata().setName(support.getName());
        result.getMetadata().setNamespace(support.getNamespace());
        result.getMetadata().setLabels(support.labelsForComponent("webapp"));

        var didExist = WebAppCustomResource.client(client).delete(result);
        if (didExist) {
            // we need to wait until everything gets deleted. TODO: Obviously properly refresh status
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
        }
        result = WebAppCustomResource.client(client).createOrReplace(result);
        support.setOwner(result);
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
    private String provisionDatasourcePostBoot(CreationSupport support, Set<Configuration> dsConfigurations) {
        var postboot = new DatasourcePostbootCommands();
        dsConfigurations.forEach(postboot::addDatasource);
        var configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(support.getName()+"-postboot")
                .withLabels(support.labelsForComponent("postboot"))
                .endMetadata()
                .withData(Map.of("postboot", postboot.toString()))
                .build();
        configMap = support.applyOwner(configMap);
        var created = support.namespaceClient().resource(configMap).createOrReplace();
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

    private String provisionSystemProperties(CreationSupport support, Configuration configuration) {
        var configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(support.getName()+"-mpconfig")
                .withLabels(support.labelsForComponent("mpconfig"))
                .endMetadata()
                .withData(Map.of("microprofile-config.properties", buildPropertyFile(configuration)))
                .build();
        support.applyOwner(configMap);
        var created = support.namespaceClient().resource(configMap).createOrReplace();
        return created.getMetadata().getName();
    }

    private String buildPropertyFile(Configuration configuration) {
        return
            configuration.getKeys().stream()
                    .map(key -> key+"="+configuration.getValue(key).orElse(""))
                    .collect(Collectors.joining("\n"));
    }

    private URI provisionIngress(CreationSupport support) throws IOException {
        support.createNamespaced("ingress.yaml");
        // in real world, we should wait until ingress is actually ready by watching its state.
        return URI.create(String.format("http://%s.%s%s", support.getNamespace(), domain, support.getContextRoot()));
    }

    private void provisionService(CreationSupport support) throws IOException {
        support.createNamespaced("service.yaml");
    }

    private void provisionDatagrid(CreationSupport support) throws IOException {
        support.createNamespaced("datagrid.yaml");
    }

    private void provisionDeployment(CreationSupport support, Deployment deployment) throws IOException {
        support.namespaceClient().resource(deployment).deletingExisting().createOrReplace();
    }

    private Deployment createBaseDeployment(CreationSupport support) {
        var template = fillTemplate(getClass().getResourceAsStream("/kubernetes/templates-direct/deployment.yaml"), support::variableValue);
        return support.applyOwner(Serialization.unmarshal(template, Deployment.class));
    }

    private void provisionNamespace(CreationSupport n) throws IOException {
        var serverNamespace = client.namespaces().withName(n.getNamespace()).get();
        if (serverNamespace == null) {
            n.createGlobal("namespace.yaml");
        }
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

}
