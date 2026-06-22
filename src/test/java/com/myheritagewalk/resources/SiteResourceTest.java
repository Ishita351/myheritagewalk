package com.myheritagewalk.resources;

import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.HeritageSite;
import com.myheritagewalk.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SiteResourceTest {

    private static final String CACHE_KEY = "heritage:sites:all";

    private SiteResource resource;
    private EntityManager em;

    @BeforeEach
    void setup() {
        resource = new SiteResource();
        em = mock(EntityManager.class);
    }

    @Test
    void getAllSites_returnsCachedJson_onRedisHit() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class);
             MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {

            String cached = "[{\"id\":1,\"name\":\"Taj\"}]";
            redis.when(() -> RedisService.get(CACHE_KEY)).thenReturn(cached);

            Response res = resource.getAllSites();
            assertEquals(200, res.getStatus());
            assertEquals(cached, res.getEntity());

            jpa.verifyNoInteractions();
        }
    }

    @Test
    void getAllSites_queriesDbAndPopulatesCache_onRedisMiss() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class);
             MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {

            redis.when(() -> RedisService.get(CACHE_KEY)).thenReturn(null);
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            HeritageSite taj = new HeritageSite("Taj Mahal", "d", 27.1, 78.0, "UP", "Cultural");
            taj.setId(1L);

            TypedQuery<HeritageSite> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(HeritageSite.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.singletonList(taj));

            Response res = resource.getAllSites();
            assertEquals(200, res.getStatus());

            assertTrue(res.getEntity() instanceof String);
            String json = (String) res.getEntity();
            assertTrue(json.contains("Taj Mahal"), "JSON should contain entity name: " + json);

            redis.verify(() -> RedisService.set(eq(CACHE_KEY), eq(json)));
            verify(em).close();
        }
    }

    @Test
    void getAllSites_treatsBlankCacheAsMiss() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class);
             MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {

            redis.when(() -> RedisService.get(CACHE_KEY)).thenReturn("   ");
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<HeritageSite> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(HeritageSite.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            Response res = resource.getAllSites();
            assertEquals(200, res.getStatus());
            redis.verify(() -> RedisService.set(eq(CACHE_KEY), anyString()));
        }
    }

    @Test
    void getAllSites_returns500_onDbFailure_afterCacheMiss() {
        try (MockedStatic<RedisService> redis = mockStatic(RedisService.class);
             MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {

            redis.when(() -> RedisService.get(CACHE_KEY)).thenReturn(null);
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            when(em.createQuery(anyString(), eq(HeritageSite.class)))
                    .thenThrow(new RuntimeException("db down"));

            Response res = resource.getAllSites();
            assertEquals(500, res.getStatus());
            verify(em).close();
        }
    }
}
