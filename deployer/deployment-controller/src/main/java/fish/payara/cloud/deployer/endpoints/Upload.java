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
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Endpoint for uploading a war
 * @author jonathan coustick
 */
@Path("/deployment")
@ApplicationScoped
public class Upload {
    
    private static final Logger LOGGER = Logger.getLogger("CLOUD-DEPLOYER");
    
    @Inject
    DeploymentProcess process;
    
    @POST
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, "application/java-archive", MediaType.MULTIPART_FORM_DATA})
    @Path("{project}/{stage}")
    public Response uploadWar(@PathParam("project") String project, @PathParam("stage") String stage, InputStream uploadWar) {
        try {
            java.nio.file.Path tempFile = Files.createTempFile("upload", ".war");
            long bytesRead = Files.copy(uploadWar, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (bytesRead == 0) {
                return Response.status(400, "Empty file").build();
            }
            
            DeploymentProcessState state = process.start(tempFile.toFile(), new Namespace(project, stage));
            
            return Response.status(201).entity(state.toString()).build();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error in file upload", ex);
            return Response.serverError().build();
        }
    }
    
    
}
