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

package fish.payara.cloud.deployer.kubernetes.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.PersistedDeployment;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class WebAppCustomResource extends CustomResource {
    public static final String CRD_GROUP = "payara.cloud";
    public static final String CRD_NAME = "webapps."+CRD_GROUP;
    public static final String KIND = "WebApp";
    public static final String VERSION = "v1beta1";
    public static final CustomResourceDefinitionContext DEFINITION_CONTEXT = new CustomResourceDefinitionContext.Builder()
            .withGroup(CRD_GROUP)
            .withName(CRD_NAME)
            .withPlural("webapps")
            .withVersion(VERSION)
            .withScope("Namespace")
            .build();

    private WebAppSpec spec = new WebAppSpec();
    private WebAppStatus status;

    public WebAppCustomResource() {
        super(KIND);
    }

    protected WebAppCustomResource(String customKind) {
        super(customKind);
    }


    public WebAppSpec getSpec() {
        return spec;
    }

    public void setSpec(WebAppSpec spec) {
        this.spec = spec;
    }

    public WebAppStatus getStatus() {
        return status;
    }

    public void setStatus(WebAppStatus status) {
        this.status = status;
    }

    public WebAppStatus makeStatus() {
        if (status == null) {
            setStatus(new WebAppStatus());
        }
        status.setObservedGeneration(getMetadata().getGeneration());
        return status;
    }

    @Override
    public String toString() {
        return "WebAppCustomResource{" +
                "metadata=" + getMetadata() +
                "spec=" + spec +
                '}';
    }

    public static void register() {
        KubernetesDeserializer.registerCustomKind(CRD_GROUP + "/" + VERSION, "WebApp", WebAppCustomResource.class);
    }

    // cached resource definition
    private static volatile URL LAST_SEEN_CLIENT;
    private static volatile CustomResourceDefinition CACHED_DEFINITION;

    public static CustomResourceDefinition findDefinition(KubernetesClient client) {
        if (CACHED_DEFINITION != null && client.getMasterUrl().equals(LAST_SEEN_CLIENT)) {
            return CACHED_DEFINITION;
        }
        LAST_SEEN_CLIENT = null;
        var definition = client.customResourceDefinitions().withName(CRD_NAME).get();
        synchronized (WebAppCustomResource.class) {
            CACHED_DEFINITION = definition;
            LAST_SEEN_CLIENT = client.getMasterUrl();
        }
        return definition;
    }

    // Wonderful return type
    public static MixedOperation<WebAppCustomResource, WebAppList, DoneableWebApp, Resource<WebAppCustomResource, DoneableWebApp>> client(KubernetesClient client, CustomResourceDefinition definition) {
        return client.customResources(definition, WebAppCustomResource.class, WebAppList.class, DoneableWebApp.class);
    }

    public static MixedOperation<WebAppCustomResource, WebAppList, DoneableWebApp, Resource<WebAppCustomResource, DoneableWebApp>> client(KubernetesClient client) {
        return client.customResources(findDefinition(client), WebAppCustomResource.class, WebAppList.class, DoneableWebApp.class);
    }

    /**
     * Convert to representation of persisted deployment.
     * Namespace is provided from outside, as mapping of Kubernetes namespace to deployer namespace is concern of provisioner
     * @param namespace Namespace of the deployment.
     * @return
     */
    public PersistedDeployment asPersistedDeployment(Namespace namespace) {
        return new PersistedDeployment() {
            @Override
            public String getId() {
                return getSpec().getDeploymentProcessId() != null ? getSpec().getDeploymentProcessId().toString() : getMetadata().getUid();
            }

            @Override
            public String getName() {
                return getMetadata().getName();
            }

            @Override
            public Namespace getNamespace() {
                return namespace;
            }

            @Override
            public URI getArtifactLocation() {
                return getSpec().getArtifactUrl();
            }

            @Override
            public URI getPublicEndpoint() {
                return getStatus() != null ? getStatus().getPublicEndpoint() : null;
            }

            @Override
            public Instant getProvisionedAt() {
                return Instant.parse(getMetadata().getCreationTimestamp());
            }

            @Override
            public boolean isFailed() {
                return false; // TODO: conditions
            }

            @Override
            public String getFailureMessage() {
                return null; // TODO: conditions
            }

            @Override
            public boolean isDeleted() {
                return getMetadata().getDeletionTimestamp() != null;
            }

            @Override
            public Instant getDeletedAt() {
                return getMetadata().getDeletionTimestamp() != null ? Instant.parse(getMetadata().getDeletionTimestamp()) : null;
            }

            @Override
            public Collection<PersistedConfiguration> getConfiguration() {
                return getSpec().getConfiguration().stream().map(WebAppSpecConfiguration::asPersistedConfiguration).collect(Collectors.toSet());
            }
        };
    }

}
