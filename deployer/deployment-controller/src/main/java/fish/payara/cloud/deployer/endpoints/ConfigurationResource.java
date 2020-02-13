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

import fish.payara.cloud.deployer.process.ConfigBean;
import fish.payara.cloud.deployer.process.ConfigurationValidationException;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import java.util.Map;
import static java.util.stream.Collectors.toMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mvc.Controller;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

/**
 * JAX-RS endpoint for dealing with the application configuration
 *
 * @author jonathan coustick
 */
@Path("/deployment/{deploymentId}/configuration/")
@ApplicationScoped
public class ConfigurationResource {

    @Inject
    DeploymentProcess process;
    
    @Inject
    MvcModels models;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigBean getConfiguration(@PathParam("deploymentId") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        return ConfigBean.forDeploymentProcess(state);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigBean submitAll(@PathParam("deploymentId") String deploymentId, @QueryParam("submit") boolean submit) {
        DeploymentProcessState state = findDeployment(deploymentId);
        if (submit) {
            state = process.submitConfigurations(state);
        } else {
            state = process.resetConfigurations(state);
        }
        return ConfigBean.forDeploymentProcess(state);
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Controller
    public Response getConfiguration(@PathParam("deploymentId") String id, @Context UriInfo uriInfo) {
        // you might get here when refreshing invalid input.
        DeploymentProcessState state = process.getProcessState(id);
        return redirectToDeployment(uriInfo, state);
    }    
    
    @POST
    @Produces(MediaType.TEXT_HTML)
    @Controller
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response configAll(@PathParam("deploymentId") String deploymentId, FormDataMultiPart form, @Context UriInfo uriInfo) {
        DeploymentProcessState state = findDeployment(deploymentId);
        
        Map<String,String> singleValues = form.getFields().entrySet().stream()
                .collect(toMap(e -> e.getKey(), e -> e.getValue().get(0).getValue()));
        
        var configBean = ConfigBean.forDeploymentProcess(state);
        
        configBean.applyValuesFrom(singleValues);
        
        state = configBean.configStream().filter(ConfigBean.Config::hasUpdates)
                .reduce(state, 
                        (st, config) -> {
                            try {
                                return process.setConfiguration(st, config.getId().getKind(), config.getId().getId(), false, config.updates());
                            } catch (ConfigurationValidationException cve) {
                                config.setMessages(cve);
                                return st;
                            }
                        },
                        (s1, s2) -> s1.getVersion() > s2.getVersion() ? s1 : s2);
        
        if (configBean.hasErrors()) {
            models.setConfig(configBean);
            models.setDeployment(state);
            return Response.status(Status.BAD_REQUEST).entity("deployment.xhtml").build();
        } else {
            if (state.isConfigurationComplete()) {
                process.submitConfigurations(state);
            }
            return redirectToDeployment(uriInfo, state);
        }
    }    

    private DeploymentProcessState findDeployment(String deploymentId) throws NotFoundException {
        DeploymentProcessState state = process.getProcessState(deploymentId);
        if (state == null) {
            throw new NotFoundException();
        }
        return state;
    }

    @Path("{kind}/{id}/values")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ConfigBean setConfiguration(@PathParam("id") String id,
                                     @PathParam("kind") String kind,
                                     @PathParam("deploymentId") String deploymentId,
                                     @QueryParam("submit") boolean submit, Map<String,String> values) {

        DeploymentProcessState state = findDeployment(deploymentId);
        try {
            state = process.setConfiguration(state, kind, id, submit, values);
            return ConfigBean.forConfiguration(state.getConfigurations(), state.getId());
        } catch (ConfigurationValidationException e) {
            throw new BadRequestException(Response.ok(e.getValidationErrors()).build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e);
        }
    }

    @Path("{kind}/{id}")
    @GET
    @Controller
    @Produces(MediaType.TEXT_HTML)
    public String showConfigurationForm(@PathParam("id") String id,
                                     @PathParam("kind") String kind,
                                     @PathParam("deploymentId") String deploymentId) {
        DeploymentProcessState state = findDeployment(deploymentId);                            
        models.setDeployment(state);
        models.setConfigKind(kind);
        models.setConfigId(id);
        return "edit-configuration.xhtml";
    }
    
    @Path("{kind}/{id}")
    @POST
    @Controller
    @Produces(MediaType.TEXT_HTML)
    public Response editConfiguration(@PathParam("id") String id,
                                     @PathParam("kind") String kind,
                                     @PathParam("deploymentId") String deploymentId,
                                     Form form,
                                     @Context UriInfo uriInfo) {
        DeploymentProcessState state = findDeployment(deploymentId);             
        Map<String, String> values = form.asMap().entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
        
        process.setConfiguration(state, kind, id, false, values);
        return redirectToDeployment(uriInfo, state);
    }  
    
    @Path("{kind}/{id}")
    @POST
    @Controller
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response editConfigurationMultipart(@PathParam("id") String id,
                                     @PathParam("kind") String kind,
                                     @PathParam("deploymentId") String deploymentId,
                                     FormDataMultiPart form,
                                     @Context UriInfo uriInfo) {
        DeploymentProcessState state = findDeployment(deploymentId);             
        Map<String, String> values = form.getFields().entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().get(0).getValue()));
        
        process.setConfiguration(state, kind, id, false, values);
        return redirectToDeployment(uriInfo, state);
    }      

    private static Response redirectToDeployment(UriInfo uriInfo, DeploymentProcessState state) throws UriBuilderException, IllegalArgumentException {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("deployment/{id}/").build(state.getId())).build();
    }
    
    @Path("submit")
    @POST
    @Controller
    public Response submitAllConfiguration(@PathParam("deploymentId") String deploymentId,@Context UriInfo uriInfo) {
        DeploymentProcessState state = findDeployment(deploymentId);    
        process.submitConfigurations(state);
        return redirectToDeployment(uriInfo, state);
    }
    
    @Path("reset")
    @POST
    @Controller
    public Response resetAllConfiguration(@PathParam("deploymentId") String deploymentId,@Context UriInfo uriInfo) {
        DeploymentProcessState state = findDeployment(deploymentId);    
        process.resetConfigurations(state);
        return redirectToDeployment(uriInfo, state);
    }    
}
