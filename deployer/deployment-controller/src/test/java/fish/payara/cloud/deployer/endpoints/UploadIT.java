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

import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.mvc.Models;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Assert;
import org.junit.Test;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

/**
 * Tests that the deployment endpoint works
 * @author jonathan coustick
 */
@RunWith(Arquillian.class)
public class UploadIT {
    
    @Deployment
    public static WebArchive deployment() {
        WebArchive archive =  ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addPackage(Provisioner.class.getPackage())
                .addPackage(Application.class.getPackage())
                .addClass(ManagedConcurrencyProducer.class)
                .addClass(Models.class)
                .addClass(ModelsImpl.class);

        System.out.println(archive.toString(true));
        return archive;
    }
    
    @ArquillianResource
    private URL base;
    
    //Tests that a file can be uploaded. As deployment in done asynchronously all files will deploy fine, but may be fail later 
   @Test
   public void uploadTest() throws IOException {
       WebTarget jaxrstarget = ClientBuilder.newClient().
               target(URI.create(new URL(base, "api/deployment/foo/bar/README.adoc").toExternalForm()));
       System.out.println(jaxrstarget.getUri().toString());
       Response response = jaxrstarget.request(MediaType.APPLICATION_OCTET_STREAM_TYPE).
               post(Entity.entity(Files.newInputStream(Paths.get("README.adoc")),MediaType.APPLICATION_OCTET_STREAM_TYPE));
       Assert.assertEquals(201, response.getStatus());
       String id = response.readEntity(String.class);
       
       jaxrstarget = ClientBuilder.newClient().target(URI.create(new URL(base, "api/deployment/" + id).toExternalForm()));
       System.out.println(jaxrstarget.getUri().toString());
       response = jaxrstarget.request().get();
       System.out.println(response.readEntity(String.class));
       Assert.assertEquals(200, response.getStatus());
   }
    
}
