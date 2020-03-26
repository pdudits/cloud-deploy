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

package fish.payara.cloud.deployer.endpoints;

import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.process.ConfigBean;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Map;

import static fish.payara.cloud.deployer.ArquillianDeployments.compose;
import static fish.payara.cloud.deployer.ArquillianDeployments.restApi;
import static fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration.APP_NAME;
import static fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration.CONTEXT_ROOT;
import static fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration.KIND;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import javax.mvc.Models;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class ConfigurationIT {
    @Deployment
    public static WebArchive deployment() {
        return compose(restApi());
    }

    @Inject
    DeploymentProcess process;

    @ArquillianResource
    URI uri;

    @Test
    public void configCanBeFetched() {
        DeploymentProcessState state = process.start(null, "test.war", new Namespace("test","test"));
        process.addConfiguration(state, new ContextRootConfiguration("test.war", "test", "/test"));

        var client = ClientBuilder.newClient().target(uri).path("api/deployment/").path(state.getId());

        var configState = client.path("configuration/").request(APPLICATION_JSON_TYPE).get(ConfigBean.class);
        assertNotNull(configState.getKind().get(KIND));

        var contextRootConfig = configState.getKind().get(KIND).get("test.war");
        assertEquals("test", contextRootConfig.getValues().get(APP_NAME));
        assertEquals("/test", contextRootConfig.getValues().get(CONTEXT_ROOT));
    }

    @Test
    public void configCanBeUpdated() {
        DeploymentProcessState state = process.start(null, "test.war", new Namespace("test","test"));
        process.addConfiguration(state, new ContextRootConfiguration("test.war", "test", "/test"));

        var client = ClientBuilder.newClient().target(uri).path("api/deployment/").path(state.getId())
                .path("configuration/").path(KIND).path("test.war/values");

        var values = Map.of(APP_NAME, "other-test", CONTEXT_ROOT, "/other");
        var configState = client.request(APPLICATION_JSON_TYPE).put(Entity.entity(values, APPLICATION_JSON_TYPE),
                ConfigBean.class);

        assertNotNull(configState.getKind().get(KIND));
        var contextRootConfig = configState.getKind().get(KIND).get("test.war");
        assertEquals("other-test", contextRootConfig.getValues().get(APP_NAME));
        assertEquals("/other", contextRootConfig.getValues().get(CONTEXT_ROOT));
    }

    @Test
    public void configCanBeSubmitted() {
        DeploymentProcessState state = process.start(null, "test.war", new Namespace("test","test"));
        process.addConfiguration(state, new ContextRootConfiguration("test.war", "test", "/test"));

        var client = ClientBuilder.newClient().target(uri).path("api/deployment/").path(state.getId());

        var submittedConfigState = client.path("configuration/").queryParam("submit", "true").request(APPLICATION_JSON_TYPE).post(null, ConfigBean.class);

        assertNotNull(submittedConfigState.getKind().get(KIND));
        var contextRootConfig = submittedConfigState.getKind().get(KIND).get("test.war");
        assertTrue(contextRootConfig.isSubmitted());
    }

    @Test
    public void configCanBeReset() {
        DeploymentProcessState state = process.start(null, "test.war", new Namespace("test","test"));
        process.addConfiguration(state, new ContextRootConfiguration("test.war", "test", "/test"));

        var client = ClientBuilder.newClient().target(uri).path("api/deployment/").path(state.getId())
                .path("configuration/");

        var values = Map.of(APP_NAME, "other-test", CONTEXT_ROOT, "/other");
        var configState = client.path(KIND).path("test.war/values").queryParam("submit", "true")
                .request(APPLICATION_JSON_TYPE)
                .put(Entity.entity(values, APPLICATION_JSON_TYPE), ConfigBean.class);

        assertNotNull(configState.getKind().get(KIND));

        var contextRootConfig = configState.getKind().get(KIND).get("test.war");
        assertTrue(contextRootConfig.isSubmitted());
        assertEquals("other-test", contextRootConfig.getValues().get(APP_NAME));
        assertEquals("/other", contextRootConfig.getValues().get(CONTEXT_ROOT));

        var resetConfigState = client.queryParam("submit", "false").request(APPLICATION_JSON_TYPE)
                .post(null, ConfigBean.class);

        contextRootConfig = resetConfigState.getKind().get(KIND).get("test.war");
        assertEquals("test", contextRootConfig.getValues().get(APP_NAME));
        assertEquals("/test", contextRootConfig.getValues().get(CONTEXT_ROOT));
    }
}
