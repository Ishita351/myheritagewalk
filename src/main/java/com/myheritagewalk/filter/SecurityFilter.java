package com.myheritagewalk.filter;

import com.myheritagewalk.service.RedisService;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.security.Principal;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {

    private static final String AUTH_HEADER = "X-Auth-Token";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;

        if (cleanPath.equals("auth/login") ||
            cleanPath.equals("auth/register") || 
            cleanPath.equals("sites") ||
            requestContext.getMethod().equals("OPTIONS")) {
            return;
        }

        String token = requestContext.getHeaderString(AUTH_HEADER);

        if (token == null || token.trim().isEmpty()) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"Missing authentication token in header: " + AUTH_HEADER + "\"}")
                        .type("application/json")
                        .build()
            );
            return;
        }

        String userIdStr = RedisService.get("session:" + token);
        if (userIdStr == null) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"Invalid or expired session. Please sign in again.\"}")
                        .type("application/json")
                        .build()
            );
            return;
        }

        final String userId = userIdStr;
        final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
        
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return () -> userId;
            }

            @Override
            public boolean isUserInRole(String role) {
                return true;
            }

            @Override
            public boolean isSecure() {
                return currentSecurityContext.isSecure();
            }

            @Override
            public String getAuthenticationScheme() {
                return "Token-Based";
            }
        });
    }
}
