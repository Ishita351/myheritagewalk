package com.myheritagewalk.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.HeritageSite;
import com.myheritagewalk.service.RedisService;

import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/sites")
@Produces(MediaType.APPLICATION_JSON)
public class SiteResource {
    private static final Logger LOGGER = Logger.getLogger(SiteResource.class.getName());
    private static final String CACHE_KEY = "heritage:sites:all";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @GET
    public Response getAllSites() {
        try {
            String cachedJson = RedisService.get(CACHE_KEY);
            if (cachedJson != null && !cachedJson.trim().isEmpty()) {
                LOGGER.info("Serving heritage sites from Redis cache.");
                return Response.ok(cachedJson).build();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading from Redis cache, falling back to database", e);
        }

        LOGGER.info("Redis cache miss. Querying database for heritage sites.");
        EntityManager em = JPAUtil.getEntityManager();
        try {
            List<HeritageSite> sites = em.createQuery("SELECT s FROM HeritageSite s", HeritageSite.class)
                    .getResultList();

            try {
                String json = OBJECT_MAPPER.writeValueAsString(sites);
                RedisService.set(CACHE_KEY, json);
                LOGGER.info("Successfully populated Redis cache with heritage sites.");
                return Response.ok(json).build();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to serialize and write sites to Redis", e);
                return Response.ok(sites).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to retrieve heritage sites from database: " + e.getMessage() + "\"}")
                    .build();
        } finally {
            em.close();
        }
    }
}
