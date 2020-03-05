/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
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
 *  file and include the License.
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
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.mvc.Models;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import org.junit.Assert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for the namespaces endpoint
 * @author jonathan coustick
 */
@RunWith(Arquillian.class)
public class NamespaceIT {
    
    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addPackage(Application.class.getPackage())
                .addPackage(Provisioner.class.getPackage())
                .addClass(ContextRootConfiguration.class)
                .addClass(ManagedConcurrencyProducer.class)
                .addClass(Models.class)
                .addClass(ModelsImpl.class);
    }

    @Inject
    DeploymentProcess process;

    @ArquillianResource
    URI uri;
    
    @Test
    public void testNamespacesList() {
        var client = ClientBuilder.newClient().target(uri).path("api/namespaces/");
        var response = client.request(MediaType.APPLICATION_JSON).get(JsonArray.class);
        Assert.assertEquals(1, response.size());
        JsonObject content =  response.getJsonObject(0);
        Assert.assertEquals("foo", content.getString("project"));
        Assert.assertEquals("bar", content.getString("stage"));
    }
    
    @Test
    public void testDeploymentsList() {
        var client = ClientBuilder.newClient().target(uri).path("api/namespaces/bar/foo");
        var response = client.request(MediaType.APPLICATION_JSON).get(Map.class);
        Assert.assertEquals(1, response.size());
        List array = (List) response.get("foo");
        Assert.assertEquals(1, array.size());
        Assert.assertEquals("http://www.example.com", array.get(0).toString());
    }
    
}
