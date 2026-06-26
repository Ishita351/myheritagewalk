package com.myheritagewalk.filter;

import com.myheritagewalk.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityFilterTest {

    private SecurityFilter filter;
    private ContainerRequestContext ctx;
    private UriInfo uriInfo;

    @BeforeEach
    void setup() {
        filter = new SecurityFilter();
        ctx = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(ctx.getSecurityContext()).thenReturn(mock(SecurityContext.class));
    }

    private void requestFor(String path, String method, String token) {
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getMethod()).thenReturn(method);
        when(ctx.getHeaderString("X-Auth-Token")).thenReturn(token);
    }

    @Test
    void bypassesAuthLogin() throws Exception {
        requestFor("auth/login", "POST", null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
        verify(ctx, never()).setSecurityContext(any());
    }

    @Test
    void bypassesAuthRegister() throws Exception {
        requestFor("auth/register", "POST", null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
    }

    @Test
    void bypassesSites() throws Exception {
        requestFor("sites", "GET", null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
    }

    @Test
    void bypassesAnyOptionsRequest() throws Exception {
        requestFor("selections/42/visit", "OPTIONS", null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
    }

    @Test
    void aborts401_whenTokenHeaderMissing() throws Exception {
        requestFor("selections", "GET", null);
        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void aborts401_whenTokenInvalid() throws Exception {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class)) {
            redis.when(() -> RedisService.get("session:bad")).thenReturn(null);
            requestFor("selections", "GET", "bad");

            filter.filter(ctx);

            ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
            verify(ctx).abortWith(captor.capture());
            assertEquals(401, captor.getValue().getStatus());
            verify(ctx, never()).setSecurityContext(any());
        }
    }

    @Test
    void installsSecurityContext_whenTokenValid() throws Exception {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class)) {
            redis.when(() -> RedisService.get("session:good")).thenReturn("42");
            requestFor("selections", "GET", "good");

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
            ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
            verify(ctx).setSecurityContext(captor.capture());
            SecurityContext installed = captor.getValue();
            assertEquals("42", installed.getUserPrincipal().getName());
            assertEquals("Token-Based", installed.getAuthenticationScheme());
        }
    }

    @Test
    void handlesPathsWithLeadingSlash() throws Exception {
        requestFor("/auth/login", "POST", null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
    }
}
