package com.myheritagewalk.resources;

import com.myheritagewalk.db.JPAUtil;
import com.myheritagewalk.model.HeritageSite;
import com.myheritagewalk.model.User;
import com.myheritagewalk.model.UserSelection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SelectionResourceTest {

    private SelectionResource resource;
    private EntityManager em;
    private EntityTransaction tx;
    private SecurityContext sc;

    @BeforeEach
    void setup() {
        resource = new SelectionResource();
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        sc = scWithPrincipal("5");
    }

    private static SecurityContext scWithPrincipal(String name) {
        SecurityContext sc = mock(SecurityContext.class);
        Principal p = mock(Principal.class);
        when(p.getName()).thenReturn(name);
        when(sc.getUserPrincipal()).thenReturn(p);
        return sc;
    }

    private UserSelection selection(long id, long userId, long siteId, boolean visited) {
        User u = new User("u", "h", "u@x.com"); u.setId(userId);
        HeritageSite s = new HeritageSite("Site", "d", 1.0, 2.0, "S", "C"); s.setId(siteId);
        UserSelection sel = new UserSelection(u, s, visited);
        sel.setId(id);
        return sel;
    }

    @Test
    void getUserSelections_returnsFlattenedMaps() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<UserSelection> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(UserSelection.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.singletonList(selection(99L, 5L, 12L, true)));

            Response res = resource.getUserSelections(sc);
            assertEquals(200, res.getStatus());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) res.getEntity();
            assertEquals(1, body.size());
            Map<String, Object> row = body.get(0);
            assertEquals(99L, row.get("selectionId"));
            assertEquals(true, row.get("isVisited"));

            @SuppressWarnings("unchecked")
            Map<String, Object> site = (Map<String, Object>) row.get("site");
            assertEquals(12L, site.get("id"));
            assertEquals("Site", site.get("name"));
            verify(em).close();
        }
    }

    @Test
    void selectSite_returns404_whenUserOrSiteMissing() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            when(em.find(User.class, 5L)).thenReturn(null);
            when(em.find(HeritageSite.class, 12L)).thenReturn(null);

            Response res = resource.selectSite(12L, sc);
            assertEquals(404, res.getStatus());
            verify(em, never()).persist(any());
        }
    }

    @Test
    void selectSite_persistsNew_whenNotPreviouslySelected() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            User u = new User("u", "h", "u@x.com"); u.setId(5L);
            HeritageSite s = new HeritageSite("S", "d", 1.0, 2.0, "S", "C"); s.setId(12L);
            when(em.find(User.class, 5L)).thenReturn(u);
            when(em.find(HeritageSite.class, 12L)).thenReturn(s);

            TypedQuery<UserSelection> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(UserSelection.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenThrow(new NoResultException());

            Response res = resource.selectSite(12L, sc);
            assertEquals(200, res.getStatus());

            verify(em).persist(argThat((UserSelection sel) ->
                    sel.getUser() == u && sel.getHeritageSite() == s && Boolean.FALSE.equals(sel.getIsVisited())));
            verify(tx).commit();
        }
    }

    @Test
    void selectSite_isIdempotent_whenAlreadySelected() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            User u = new User("u", "h", "u@x.com"); u.setId(5L);
            HeritageSite s = new HeritageSite("S", "d", 1.0, 2.0, "S", "C"); s.setId(12L);
            when(em.find(User.class, 5L)).thenReturn(u);
            when(em.find(HeritageSite.class, 12L)).thenReturn(s);

            UserSelection existing = selection(7L, 5L, 12L, true);
            TypedQuery<UserSelection> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(UserSelection.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(existing);

            Response res = resource.selectSite(12L, sc);
            assertEquals(200, res.getStatus());
            verify(em, never()).persist(any());
            verify(tx).commit();
        }
    }

    @Test
    void deselectSite_returns200_whenRowDeleted() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            Query q = mock(Query.class);
            when(em.createQuery(anyString())).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.executeUpdate()).thenReturn(1);

            Response res = resource.deselectSite(12L, sc);
            assertEquals(200, res.getStatus());
            verify(tx).commit();
        }
    }

    @Test
    void deselectSite_returns404_whenNothingDeleted() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            Query q = mock(Query.class);
            when(em.createQuery(anyString())).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.executeUpdate()).thenReturn(0);

            Response res = resource.deselectSite(12L, sc);
            assertEquals(404, res.getStatus());
        }
    }

    @Test
    void toggleVisitedSite_flipsExistingSelection() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            UserSelection existing = selection(7L, 5L, 12L, false);
            TypedQuery<UserSelection> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(UserSelection.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(existing);

            Response res = resource.toggleVisitedSite(12L, sc);
            assertEquals(200, res.getStatus());
            assertTrue(existing.getIsVisited(), "isVisited should have been flipped to true");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getEntity();
            assertEquals(true, body.get("isVisited"));
            verify(em, never()).persist(any());
            verify(tx).commit();
        }
    }

    @Test
    void toggleVisitedSite_autoSelectsAndMarksVisited_whenNoSelectionExists() {
        try (MockedStatic<JPAUtil> jpa = mockStatic(JPAUtil.class)) {
            jpa.when(JPAUtil::getEntityManager).thenReturn(em);

            TypedQuery<UserSelection> q = mock(TypedQuery.class);
            when(em.createQuery(anyString(), eq(UserSelection.class))).thenReturn(q);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getSingleResult()).thenThrow(new NoResultException());

            User u = new User("u", "h", "u@x.com"); u.setId(5L);
            HeritageSite s = new HeritageSite("S", "d", 1.0, 2.0, "S", "C"); s.setId(12L);
            when(em.find(User.class, 5L)).thenReturn(u);
            when(em.find(HeritageSite.class, 12L)).thenReturn(s);

            Response res = resource.toggleVisitedSite(12L, sc);
            assertEquals(200, res.getStatus());

            verify(em).persist(argThat((UserSelection sel) ->
                    sel.getUser() == u && sel.getHeritageSite() == s && Boolean.TRUE.equals(sel.getIsVisited())));
            verify(tx).commit();
        }
    }
}
