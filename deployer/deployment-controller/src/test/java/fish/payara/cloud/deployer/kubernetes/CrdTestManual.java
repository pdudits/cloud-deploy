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

import fish.payara.cloud.deployer.kubernetes.crd.WebAppCustomResource;
import fish.payara.cloud.deployer.kubernetes.crd.WebAppSpecConfiguration;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static fish.payara.cloud.deployer.kubernetes.crd.WebAppCustomResource.CRD_GROUP;
import static fish.payara.cloud.deployer.kubernetes.crd.WebAppCustomResource.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CrdTestManual {
    public static final String TEST_KIND = "WebappTestCrd";
    private static NamespacedKubernetesClient client;
    private static Namespace testNamespace;
    private static CustomResourceDefinition resourceDefinition;
    private static MixedOperation<TestResource, TestResourceList, DoneableResource, Resource<TestResource, DoneableResource>> crClient;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TestName name = new TestName();
    private TestResource resource;

    @BeforeClass
    public static void prepareCluster() {
        // connect
        var appClient = new KubernetesClient();
        appClient.connectApiServer();
        client = appClient.client;

        // create namespace
        testNamespace = new Namespace();
        testNamespace.setMetadata(new ObjectMeta());
        testNamespace.getMetadata().setName("crd-test");
        testNamespace = appClient.client.namespaces().createOrReplace(testNamespace);
        client = client.inNamespace("crd-test");

        // create test CRD by modifying names of original one
        resourceDefinition = client.customResourceDefinitions().load("../../install/webapp-crd.yaml").get();

        resourceDefinition.getMetadata().setName("webapp-test-crds."+ CRD_GROUP);
        var names = resourceDefinition.getSpec().getNames();
        names.setKind(TEST_KIND);
        names.setSingular("webapp-test-crd");
        names.setPlural("webapp-test-crds");

        resourceDefinition = client.customResourceDefinitions().create(resourceDefinition);

        // register deserialization data
        KubernetesDeserializer.registerCustomKind(CRD_GROUP + "/" + VERSION, TEST_KIND, TestResource.class);

        crClient = client.customResources(resourceDefinition, TestResource.class, TestResourceList.class, DoneableResource.class);
    }

    @AfterClass
    public static void cleanup() {
        if (resourceDefinition != null) {
            client.customResourceDefinitions().delete(resourceDefinition);
        }
        if (testNamespace != null) {
            client.namespaces().delete(testNamespace);
        }
        client.close();
    }

    @Before
    public void prepareResource() {
        resource = new TestResource(name.getMethodName()
                .replaceAll("([a-z])([A-Z])","$1-$2").toLowerCase());
        resource.getSpec().setArtifactUrl(URI.create("https://"+name.getMethodName()));
    }

    @Test
    public void emptySpecFails() {
        resource.setSpec(null);
        exception.expectMessage("spec in body is required");
        crClient.create(resource);
    }

    @Test
    public void minimalResourcePasses() {
        crClient.create(resource);

        var read = readFromServer(resource);
        assertEquals(resource.getSpec().getArtifactUrl(), read.getSpec().getArtifactUrl());
    }

    @Test
    public void optionalFieldsCanBeSpecified() {
        resource.getSpec().setDeploymentProcessId(UUID.randomUUID());

        crClient.create(resource);
        var read = readFromServer(resource);
        assertEquals(resource.getSpec().getDeploymentProcessId(), resource.getSpec().getDeploymentProcessId());
    }

    @Test
    public void incompleteConfigRejected() {
        resource.getSpec().addConfigurationItem(new WebAppSpecConfiguration());
        exception.expectMessage("configuration.id in body must be of type string");
        exception.expectMessage("configuration.kind in body must be of type string");
        crClient.create(resource);
    }

    @Test
    public void emptyConfigAccepted() {
        var config = new WebAppSpecConfiguration();
        config.setKind("contextRoot");
        config.setId("whatever.war");
        resource.getSpec().addConfigurationItem(config);
        crClient.create(resource);
    }

    @Test
    public void nonEmptyConfigAccepted() {
        var config = new WebAppSpecConfiguration();
        config.setKind("contextRoot");
        config.setId("whatever.war");
        config.setValues(Map.of("key1", "value1", "key 2", "value 3", "emptyKey", ""));
        resource.getSpec().addConfigurationItem(config);
        crClient.create(resource);

        var read = readFromServer(resource);
        assertEquals(config.getValues(), read.getSpec().getConfiguration().get(0).getValues());
    }

    @Test
    // doesn't seem to work with K8s 1.14
    @Ignore
    public void spaceInConfigKindRejected() {
        var config = new WebAppSpecConfiguration();
        config.setKind("context root");
        config.setId("whatever.war");
        resource.getSpec().addConfigurationItem(config);
        exception.expectMessage(".kind");
    }

    @Test
    public void colonInIdAccepted() {
        var config = new WebAppSpecConfiguration();
        config.setKind("contextRoot");
        config.setId("colons:accepted:except:for:double:");
        resource.getSpec().addConfigurationItem(config);
        crClient.create(resource);
    }

    @Test
    // doesn't seem to work with K8s 1.14
    @Ignore
    public void doublecolonInIdRejected() {
        var config = new WebAppSpecConfiguration();
        config.setKind("contextRoot");
        config.setId("reject::this");
        resource.getSpec().addConfigurationItem(config);
        crClient.create(resource);
        exception.expectMessage(".id");
    }

    @Test
    public void updatingStatusPreservesGeneration() {
        crClient.create(resource);

        var read1 = readFromServer(resource);
        assertNotNull(read1.getMetadata().getGeneration());

        read1.makeStatus().setPublicEndpoint(URI.create("https://google.com"));
        crClient.updateStatus(read1);
        var read2 = readFromServer(read1);
        assertEquals(read1.getMetadata().getGeneration(), read2.getMetadata().getGeneration());
        assertEquals(read1.getMetadata().getGeneration(), read2.getStatus().getObservedGeneration());
        assertEquals(read1.getStatus().getPublicEndpoint(), read2.getStatus().getPublicEndpoint());
    }

    @Test
    public void updatingSpecUpdatesGeneration() {
        resource.getSpec().addConfigurationItem("config", "id", Map.of("key", "value"));
        crClient.create(resource);

        var read1 = readFromServer(resource);
        assertNotNull(read1.getMetadata().getGeneration());
        var sourceGeneration = read1.getMetadata().getGeneration();

        read1.getSpec().getConfiguration().get(0).getValues().put("key", "value2");
        crClient.withName(read1.getMetadata().getName()).patch(read1);
        var read2 = readFromServer(read1);

        assertThat(read2.getMetadata().getGeneration()).isGreaterThan(read1.getMetadata().getGeneration());
    }

    private TestResource readFromServer(TestResource simpleApp) {
        return crClient.withName(simpleApp.getMetadata().getName()).fromServer().get();
    }

    // Helper types

    public static class TestResource extends WebAppCustomResource {
        public TestResource() {
            super("WebappTestCrd");
        }

        public TestResource(String name) {
            this();
            getMetadata().setName(name);
        }
    }

    public static class TestResourceList extends CustomResourceList<TestResource> {}

    public static class DoneableResource extends CustomResourceDoneable<TestResource> {
        public DoneableResource(TestResource resource, Function<TestResource, TestResource> function) {
            super(resource, function);
        }
    }
}
