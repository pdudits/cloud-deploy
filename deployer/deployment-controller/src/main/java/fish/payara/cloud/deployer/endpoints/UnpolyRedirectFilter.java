/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fish.payara.cloud.deployer.endpoints;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;

/**
 *
 * @author patrik
 */
public class UnpolyRedirectFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (responseContext.getStatusInfo().equals(Response.Status.SEE_OTHER) && requestContext.getHeaders().containsKey("X-Up-Target")) {
            // If it is a redirect response to request made by Unpoly, flag this in Matrix parameter
            var location = responseContext.getHeaderString("Location");
            responseContext.getHeaders().putSingle("Location", location+REDIRECT_FLAG);
        } else if (requestContext.getUriInfo().getPath().contains(REDIRECT_FLAG)) {
            // if it is subsequent request from above, add X-Up-Loadtion so unpoly keeps correct header
            responseContext.getHeaders().putSingle("X-Up-Location", requestContext.getUriInfo().getRequestUri().toString().replace(REDIRECT_FLAG, ""));
        }
    }
    private static final String REDIRECT_FLAG = ";redirected=true";
    
}
