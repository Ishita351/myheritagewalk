package com.myheritagewalk.db;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JPAUtil {
    private static final Logger LOGGER = Logger.getLogger(JPAUtil.class.getName());
    private static final String PERSISTENCE_UNIT_NAME = "myheritagewalk-pu";
    private static final int MAX_ATTEMPTS = 30;
    private static final long RETRY_DELAY_MS = 2000L;

    private static volatile EntityManagerFactory factory;

    public static synchronized EntityManagerFactory getFactory() {
        if (factory != null) {
            return factory;
        }
        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
                LOGGER.info("EntityManagerFactory created on attempt " + attempt);
                return factory;
            } catch (Throwable t) {
                last = t;
                LOGGER.log(Level.WARNING, "EntityManagerFactory init failed (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + t.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Failed to create EntityManagerFactory after " + MAX_ATTEMPTS + " attempts", last);
    }

    public static EntityManager getEntityManager() {
        return getFactory().createEntityManager();
    }

    public static void shutdown() {
        if (factory != null && factory.isOpen()) {
            factory.close();
        }
    }
}
