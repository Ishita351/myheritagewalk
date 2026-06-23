package com.myheritagewalk.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.HeritageSite;
import com.myheritagewalk.service.RedisService;

import javax.persistence.EntityManager;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class DataSeeder implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(DataSeeder.class.getName());
    private static final String SEED_RESOURCE = "/seed/heritage_sites.json";
    private static final String SITES_CACHE_KEY = "heritage:sites:all";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            Long existing = em.createQuery("SELECT COUNT(s) FROM HeritageSite s", Long.class)
                    .getSingleResult();
            if (existing != null && existing > 0) {
                LOGGER.info("Heritage sites already present (" + existing + " rows). Skipping JSON seed.");
                return;
            }

            List<HeritageSite> sites;
            try (InputStream in = DataSeeder.class.getResourceAsStream(SEED_RESOURCE)) {
                if (in == null) {
                    LOGGER.severe("Seed resource not found on classpath: " + SEED_RESOURCE);
                    return;
                }
                sites = new ObjectMapper().readValue(in, new TypeReference<List<HeritageSite>>() {});
            }

            em.getTransaction().begin();
            for (HeritageSite site : sites) {
                em.persist(site);
            }
            em.getTransaction().commit();

            RedisService.del(SITES_CACHE_KEY);
            LOGGER.info("Seeded " + sites.size() + " heritage sites from " + SEED_RESOURCE);
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            LOGGER.log(Level.SEVERE, "Failed to seed heritage sites from JSON", e);
        } finally {
            em.close();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
