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
import fish.payara.cloud.deployer.process.ConfigBean;
import fish.payara.cloud.deployer.process.ConfigBean.Config;
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import java.util.Map;
import java.util.Map.Entry;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

/**
 * JAX-RS endpoint for dealing with the application configuration
 *
 * @author jonathan coustick
 */
@Path("/deployment/{id}/configuration/")
@ApplicationScoped
public class ConfigurationResource {

    @Inject
    DeploymentProcess process;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getConfiguration(@PathParam("id") String id) {
        DeploymentProcessState state = process.getProcessState(id);
        return state.getConfigurationAsJson();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setConfiguration(@PathParam("id") String id, @QueryParam("submit") boolean submit, ConfigBean body) {
        DeploymentProcessState state = process.getProcessState(id);
        if (submit) {
            for (Entry<String, Map<String, Config>> entryKind : body.getKind().entrySet()) {
                if (entryKind.getKey().equals(ContextRootConfiguration.KIND)) {
                    for (Entry<String, Config> configEntry : entryKind.getValue().entrySet()) {
                        try {
                            Map<String, String> configValues = configEntry.getValue().getValues();
                            Configuration config = new ContextRootConfiguration(configEntry.getKey(),
                                    configValues.get(ContextRootConfiguration.APP_NAME), configValues.get(ContextRootConfiguration.CONTEXT_ROOT));
                            state.addConfiguration(config);
                            return Response.ok(getConfiguration(id)).build();
                        } catch (IllegalArgumentException | NullPointerException e) {
                            return Response.status(Response.Status.BAD_REQUEST).build();
                        }
                    }
                }
            }
        } else {
            state.clearConfigurations();
            return Response.ok().build();
        }
        //Other kinds of Config??
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

}
