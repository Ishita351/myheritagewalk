package com.myheritagewalk.resources;

import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.HeritageSite;
import com.myheritagewalk.model.User;
import com.myheritagewalk.model.UserSelection;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/selections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SelectionResource {

    @GET
    public Response getUserSelections(@Context SecurityContext sc) {
        String userIdStr = sc.getUserPrincipal().getName();
        Long userId = Long.parseLong(userIdStr);

        EntityManager em = JPAUtil.getEntityManager();
        try {
            List<UserSelection> selections = em.createQuery(
                    "SELECT us FROM UserSelection us WHERE us.user.id = :userId", UserSelection.class)
                    .setParameter("userId", userId)
                    .getResultList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (UserSelection sel : selections) {
                Map<String, Object> map = new HashMap<>();
                map.put("selectionId", sel.getId());
                map.put("isVisited", sel.getIsVisited());
                
                Map<String, Object> siteMap = new HashMap<>();
                HeritageSite site = sel.getHeritageSite();
                siteMap.put("id", site.getId());
                siteMap.put("name", site.getName());
                siteMap.put("description", site.getDescription());
                siteMap.put("latitude", site.getLatitude());
                siteMap.put("longitude", site.getLongitude());
                siteMap.put("state", site.getState());
                siteMap.put("category", site.getCategory());
                
                map.put("site", siteMap);
                result.add(map);
            }

            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to load selections: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/{siteId}")
    public Response selectSite(@PathParam("siteId") Long siteId, @Context SecurityContext sc) {
        String userIdStr = sc.getUserPrincipal().getName();
        Long userId = Long.parseLong(userIdStr);

        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            User user = em.find(User.class, userId);
            HeritageSite site = em.find(HeritageSite.class, siteId);

            if (user == null || site == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"User or Site not found.\"}")
                        .build();
            }

            UserSelection selection = null;
            try {
                selection = em.createQuery(
                        "SELECT us FROM UserSelection us WHERE us.user.id = :userId AND us.heritageSite.id = :siteId", UserSelection.class)
                        .setParameter("userId", userId)
                        .setParameter("siteId", siteId)
                        .getSingleResult();
            } catch (NoResultException e) {
                selection = new UserSelection(user, site, false);
                em.persist(selection);
            }

            em.getTransaction().commit();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Site saved to your profile.");
            response.put("isVisited", selection.getIsVisited());
            return Response.ok(response).build();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to save site selection: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }

    @DELETE
    @Path("/{siteId}")
    public Response deselectSite(@PathParam("siteId") Long siteId, @Context SecurityContext sc) {
        String userIdStr = sc.getUserPrincipal().getName();
        Long userId = Long.parseLong(userIdStr);

        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            
            int rowsDeleted = em.createQuery(
                    "DELETE FROM UserSelection us WHERE us.user.id = :userId AND us.heritageSite.id = :siteId")
                    .setParameter("userId", userId)
                    .setParameter("siteId", siteId)
                    .executeUpdate();
            
            em.getTransaction().commit();

            if (rowsDeleted > 0) {
                return Response.ok("{\"message\":\"Site removed from your profile.\"}").build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Site selection not found for this user.\"}")
                        .build();
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to remove site selection: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }

    @PUT
    @Path("/{siteId}/visit")
    public Response toggleVisitedSite(@PathParam("siteId") Long siteId, @Context SecurityContext sc) {
        String userIdStr = sc.getUserPrincipal().getName();
        Long userId = Long.parseLong(userIdStr);

        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            UserSelection selection = null;
            try {
                selection = em.createQuery(
                        "SELECT us FROM UserSelection us WHERE us.user.id = :userId AND us.heritageSite.id = :siteId", UserSelection.class)
                        .setParameter("userId", userId)
                        .setParameter("siteId", siteId)
                        .getSingleResult();

                selection.setIsVisited(!selection.getIsVisited());
            } catch (NoResultException e) {
                User user = em.find(User.class, userId);
                HeritageSite site = em.find(HeritageSite.class, siteId);
                
                if (user == null || site == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\":\"User or Site not found.\"}")
                            .build();
                }
                
                selection = new UserSelection(user, site, true);
                em.persist(selection);
            }

            em.getTransaction().commit();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Site visited status toggled.");
            response.put("isVisited", selection.getIsVisited());
            return Response.ok(response).build();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to update visited status: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }
}
