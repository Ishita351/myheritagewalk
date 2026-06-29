package com.myheritagewalk.resources;

import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.User;
import com.myheritagewalk.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.MockedStatic;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthResourceTest {

    private AuthResource resource;
    private EntityManager em;
    private EntityTransaction tx;

    @BeforeEach
    void setup() {
        resource = new AuthResource();
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
    }

    private AuthResource.AuthRequest req(String username, String password, String email) {
        AuthResource.AuthRequest r = new AuthResource.AuthRequest();
        r.username = username;
        r.password = password;
        r.email = email;
        return r;
    }

    @Test
    void register_returns400_whenAnyFieldMissing() {
        Response empty = resource.register(req("", "p", "e@x.com"));
        assertEquals(400, empty.getStatus());

        Response noPwd = resource.register(req("u", null, "e@x.com"));
        assertEquals(400, noPwd.getStatus());

        Response noEmail = resource.register(req("u", "p", "  "));
        assertEquals(400, noEmail.getStatus());
    }

    @Test
    void register_returns409_whenUserAlreadyExists() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<Long> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(Long.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(1L);

            Response res = resource.register(req("u", "p", "e@x.com"));
            assertEquals(409, res.getStatus());
            verify(em, never()).persist(any());
            verify(em).close();
        }
    }

    @Test
    void register_returns201_andPersistsHashedPassword() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<Long> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(Long.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(0L);

            Response res = resource.register(req("alice", "secret", "alice@x.com"));
            assertEquals(201, res.getStatus());

            verify(tx).begin();
            verify(tx).commit();
            verify(em).persist(argThat((User u) ->
                    "alice".equals(u.getUsername())
                            && "alice@x.com".equals(u.getEmail())
                            && !"secret".equals(u.getPassword())
                            && BCrypt.checkpw("secret", u.getPassword())
            ));
            verify(em).close();
        }
    }

    @Test
    void login_returns400_whenMissingCredentials() {
        Response res = resource.login(req("", "p", null));
        assertEquals(400, res.getStatus());
    }

    @Test
    void login_returns401_whenUserNotFound() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<User> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(User.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenThrow(new NoResultException());

            Response res = resource.login(req("u", "p", null));
            assertEquals(401, res.getStatus());
        }
    }

    @Test
    void login_returns401_whenPasswordMismatch() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            User u = new User("alice", BCrypt.hashpw("right", BCrypt.gensalt()), "a@x.com");
            u.setId(7L);

            TypedQuery<User> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(User.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(u);

            Response res = resource.login(req("alice", "wrong", null));
            assertEquals(401, res.getStatus());
        }
    }

    @Test
    void login_returns200_andWritesSessionToRedis() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class);
             MockedStatic<RedisService> redis = mockStatic(RedisService.class)) {

            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            User u = new User("alice", BCrypt.hashpw("right", BCrypt.gensalt()), "a@x.com");
            u.setId(7L);

            TypedQuery<User> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(User.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(u);

            Response res = resource.login(req("alice", "right", null));
            assertEquals(200, res.getStatus());

            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) res.getEntity();
            assertEquals("alice", body.get("username"));
            assertEquals("7", body.get("userId"));
            assertNotNull(body.get("token"));

            redis.verify(() -> RedisService.setex(startsWith("session:"), eq(7200), eq("7")));
        }
    }

    @Test
    void logout_delsRedisKey_whenTokenHeaderPresent() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class)) {
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getHeaderString("X-Auth-Token")).thenReturn("abc-123");

            Response res = resource.logout(ctx);
            assertEquals(200, res.getStatus());
            redis.verify(() -> RedisService.del("session:abc-123"));
        }
    }

    @Test
    void logout_isNoOp_whenTokenHeaderMissing() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class)) {
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getHeaderString("X-Auth-Token")).thenReturn(null);

            Response res = resource.logout(ctx);
            assertEquals(200, res.getStatus());
            redis.verifyNoInteractions();
        }
    }

    @Test
    void getMe_returns404_whenUserMissing() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);
            when(em.find(User.class, 42L)).thenReturn(null);

            Response res = resource.getMe(scWithPrincipal("42"));
            assertEquals(404, res.getStatus());
        }
    }

    @Test
    void getMe_returns200_withUserPayload() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            User u = new User("alice", "hash", "a@x.com");
            u.setId(42L);
            when(em.find(User.class, 42L)).thenReturn(u);

            Response res = resource.getMe(scWithPrincipal("42"));
            assertEquals(200, res.getStatus());

            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) res.getEntity();
            assertEquals("alice", body.get("username"));
            assertEquals("a@x.com", body.get("email"));
            assertEquals("42", body.get("userId"));
        }
    }

    private static SecurityContext scWithPrincipal(String name) {
        SecurityContext sc = mock(SecurityContext.class);
        Principal p = mock(Principal.class);
        when(p.getName()).thenReturn(name);
        when(sc.getUserPrincipal()).thenReturn(p);
        return sc;
    }
}
