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

import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.provisioning.DeploymentInfo;
import fish.payara.cloud.deployer.provisioning.Provisioner;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mvc.Controller;
import javax.mvc.Models;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author jonathan coustick
 */
@Path("/namespaces/")
@ApplicationScoped
public class NamespaceResource {
    
    @Inject
    Provisioner provisioner;
    
    @Inject
    private Models model;
    
    @Inject
    MvcModels domainModels;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Namespace> getAllNamespaces() {
        return provisioner.getNamespaces();
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Controller
    public String getNamespaces(@Context UriInfo uriInfo) {
        if (!uriInfo.getRequestUri().toString().endsWith("/")) {
            // there are relative links in the view, so we need to be at /namespaces/ for the to work
            throw new RedirectionException(Response.Status.MOVED_PERMANENTLY, uriInfo.getRequestUri().resolve("namespaces/"));
        }
        model.put("title", "Namespaces");
        model.put("namespaces", getAllNamespaces().stream().collect(Collectors.groupingBy(Namespace::getStage)));
        return "namespaces.xhtml";
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{stage}/{project}/")
    public List<DeploymentInfo> getDeploymentsJson(@PathParam("stage") String stage, @PathParam("project") String project) {
        return provisioner.getDeploymentsWithIngress(new Namespace(project, stage));
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Controller
    @Path("{stage}/{project}/")
    public String getDeployments(@PathParam("stage") String stage, @PathParam("project") String project) {
        final Namespace namespace = new Namespace(project, stage);
        domainModels.setNamespace(namespace);
        model.put("title", String.format("Deployments in %s/%s", stage, project));
        model.put("deployments", provisioner.getDeploymentsWithIngress(namespace));
        return "deployment_list.xhtml";
    }
}
