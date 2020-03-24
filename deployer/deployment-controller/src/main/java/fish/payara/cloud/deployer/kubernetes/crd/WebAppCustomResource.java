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
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class WebAppCustomResource extends CustomResource {
    public static final String CRD_GROUP = "payara.cloud";
    public static final String CRD_NAME = "webapps."+CRD_GROUP;
    public static final String KIND = "WebApp";
    public static final String VERSION = "v1beta1";

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

    public static CustomResourceDefinition findDefinition(KubernetesClient client) {
        return client.customResourceDefinitions().withName(CRD_NAME).get();
    }

    // Wonderful return type
    public static MixedOperation<WebAppCustomResource, WebAppList, DoneableWebApp, Resource<WebAppCustomResource, DoneableWebApp>> client(KubernetesClient client, CustomResourceDefinition definition) {
        return client.customResources(definition, WebAppCustomResource.class, WebAppList.class, DoneableWebApp.class);
    }
}
