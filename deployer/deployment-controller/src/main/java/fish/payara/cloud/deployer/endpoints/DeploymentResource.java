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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.mvc.Controller;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;  
import org.glassfish.jersey.media.multipart.FormDataParam;  

/**
 * Endpoint for uploading a war
 * @author jonathan coustick
 */
@Path("/deployment")
@ApplicationScoped
public class DeploymentResource {
    
    @Resource
    ManagedExecutorService concurrency;
    
    private static final Logger LOGGER = Logger.getLogger(DeploymentResource.class.getName());
    
    @Inject
    DeploymentProcess process;
    
    @Inject
    DeploymentObserver deploymentStream;

    @Inject
    MvcModels models;
    
    private Jsonb jsonb;
    
    @PostConstruct
    public void postConstruct() {
        jsonb = JsonbBuilder.create();
    }
    
    @POST
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, "application/java-archive"})
    @Path("{project}/{stage}/{name}")
    public Response uploadWar(@PathParam("project") String project, @PathParam("stage") String stage,
                              @PathParam("name") String name, @QueryParam("defaultConfig") boolean useDefaultConfiguration, 
                              InputStream uploadWar) {
        try {
            DeploymentProcessState state = startDeployment(project, stage, name, uploadWar, useDefaultConfiguration);

            return Response.status(Response.Status.CREATED).entity(state.getId()).build();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error in file upload", ex);
            return Response.serverError().build();
        }
    }

    private DeploymentProcessState startDeployment(String project, String stage, String name, InputStream uploadWar, boolean useDefaultConfiguration) throws IOException {
        java.nio.file.Path tempFile = Files.createTempFile("upload", ".war");
        long bytesRead = Files.copy(uploadWar, tempFile, StandardCopyOption.REPLACE_EXISTING);
        if (bytesRead == 0) {
            throw new BadRequestException("Empty file");
        }

        if (useDefaultConfiguration) {
            return process.startWithDefaultConfiguration(tempFile.toFile(), name, new Namespace(project, stage));
        } else {
            return process.start(tempFile.toFile(), name, new Namespace(project, stage));
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadWarForm(@FormDataParam("project") String project, @FormDataParam("stage") String stage,
                                  @FormDataParam("defaultConfig") boolean useDefaultConfiguration,
                                  @FormDataParam("artifact") InputStream artifact,
                                  @FormDataParam("artifact") FormDataContentDisposition fileDisposition, @Context UriInfo uri) {
        try {
            var state = startDeployment(project, stage, fileDisposition.getFileName(), artifact, useDefaultConfiguration);
            return Response.seeOther(uri.resolve(URI.create("deployment/"+state.getId()+"/"))).build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error in file upload", e);
            return Response.serverError().build();
        }
    }
    
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("{id}/")
    public void getDeploymentEvents(@Context SseEventSink eventSink, @Context Sse sse, @PathParam("id") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        if (state == null) {
            eventSink.close();
            return;
        }
        if (state.isComplete()) {
            try (eventSink) {
                eventSink.send(sse.newEvent("state", jsonb.toJson(process.getProcessState(id))));
                return;
            }
        }
        deploymentStream.addRequest(eventSink, id);
    }
    
    @GET
    @Path("{id}/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deploymentStatus(@PathParam("id") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        if (state == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        
        return Response.ok(jsonb.toJson(process.getProcessState(id))).build();
    }

    @GET
    @Path("{id}/")
    @Produces(MediaType.TEXT_HTML)
    @Controller
    public String displayDeployment(@PathParam("id") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        if (state == null){
            throw new NotFoundException();
        }
        models.setDeployment(state);
        return "deployment.xhtml";
    }
    
    @GET
    @Path("{id}")
    public Response redirectToDeploymentDir(@PathParam("id") String id, @Context UriInfo uriInfo) {
        // redirect to {id}/
        return Response.seeOther(uriInfo.resolve(URI.create(id+"/"))).build();
    }
    
    @DELETE
    @Path("{id}")
    public Response deleteDeployment(@PathParam("id") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        if (state == null) {
            throw new NotFoundException();
        }
        process.delete(state);
        return Response.noContent().build();
    }
    
    @GET
    @Path("{project}/{stage}/{id}")
    public Response deploymentStatus(@PathParam("project") String project,@PathParam("stage") String stage,@PathParam("id") String id) {
        return deploymentStatus(id);
    }
    
}
