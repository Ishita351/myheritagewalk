package com.myheritagewalk.resources;

import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.User;
import com.myheritagewalk.service.RedisService;
import org.mindrot.jbcrypt.BCrypt;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    public static class AuthRequest implements Serializable {
        public String username;
        public String password;
        public String email;
    }

    @POST
    @Path("/register")
    public Response register(AuthRequest request) {
        if (request.username == null || request.username.trim().isEmpty() ||
            request.password == null || request.password.trim().isEmpty() ||
            request.email == null || request.email.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"All fields (username, password, email) are required.\"}")
                    .build();
        }

        EntityManager em = JPAUtil.getEntityManager();
        try {
            Long countUser = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = :uname OR u.email = :email", Long.class)
                    .setParameter("uname", request.username.trim())
                    .setParameter("email", request.email.trim())
                    .getSingleResult();

            if (countUser > 0) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("{\"error\":\"Username or Email already registered.\"}")
                        .build();
            }

            String hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt());
            User user = new User(request.username.trim(), hashedPassword, request.email.trim());

            em.getTransaction().begin();
            em.persist(user);
            em.getTransaction().commit();

            Map<String, String> response = new HashMap<>();
            response.put("message", "User registered successfully! Please log in.");
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Database registration failed: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/login")
    public Response login(AuthRequest request) {
        if (request.username == null || request.username.trim().isEmpty() ||
            request.password == null || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Username and password are required.\"}")
                    .build();
        }

        EntityManager em = JPAUtil.getEntityManager();
        try {
            User user = em.createQuery("SELECT u FROM User u WHERE u.username = :uname", User.class)
                    .setParameter("uname", request.username.trim())
                    .getSingleResult();

            if (!BCrypt.checkpw(request.password, user.getPassword())) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"Invalid username or password.\"}")
                        .build();
            }

            String token = UUID.randomUUID().toString();
            RedisService.setex("session:" + token, 7200, user.getId().toString());

            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("userId", user.getId().toString());

            return Response.ok(response).build();
        } catch (NoResultException nre) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Invalid username or password.\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Database login failed: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/logout")
    public Response logout(@Context ContainerRequestContext requestContext) {
        String token = requestContext.getHeaderString("X-Auth-Token");
        if (token != null) {
            RedisService.del("session:" + token);
        }
        return Response.ok("{\"message\":\"Logged out successfully.\"}").build();
    }

    @GET
    @Path("/me")
    public Response getMe(@Context SecurityContext sc) {
        String userIdStr = sc.getUserPrincipal().getName();
        Long userId = Long.parseLong(userIdStr);

        EntityManager em = JPAUtil.getEntityManager();
        try {
            User user = em.find(User.class, userId);
            if (user == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"User not found.\"}")
                        .build();
            }

            Map<String, String> response = new HashMap<>();
            response.put("userId", user.getId().toString());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());

            return Response.ok(response).build();
        } finally {
            em.close();
        }
    }
}
