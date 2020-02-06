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
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigBean getConfiguration(@PathParam("deploymentId") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        return ConfigBean.forDeploymentProcess(state);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigBean submitAll(@PathParam("deploymentId") String deploymentId, @QueryParam("submit") boolean submit) {
        DeploymentProcessState state = process.getProcessState(deploymentId);
        if (state == null) {
            throw new NotFoundException();
        }
        if (submit) {
            state = process.submitConfigurations(state);
        } else {
            state = process.resetConfigurations(state);
        }
        return ConfigBean.forDeploymentProcess(state);
    }

    @Path("{kind}/{id}/values")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ConfigBean setConfiguration(@PathParam("id") String id,
                                     @PathParam("kind") String kind,
                                     @PathParam("deploymentId") String deploymentId,
                                     @QueryParam("submit") boolean submit, Map<String,String> values) {

        DeploymentProcessState state = process.getProcessState(deploymentId);
        if (state == null) {
            throw new NotFoundException();
        }
        try {
            state = process.setConfiguration(state, kind, id, submit, values);
            return ConfigBean.forConfiguration(state.getConfigurations(), state.getId());
        } catch (ConfigurationValidationException e) {
            throw new BadRequestException(Response.ok(e.getValidationErrors()).build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e);
        }
    }

}
