package com.myheritagewalk.model;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "user_selections", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "site_id"})
})
public class UserSelection implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id", nullable = false)
    private HeritageSite heritageSite;

    @Column(name = "is_visited", nullable = false)
    private Boolean isVisited = false;

    public UserSelection() {}

    public UserSelection(User user, HeritageSite heritageSite, Boolean isVisited) {
        this.user = user;
        this.heritageSite = heritageSite;
        this.isVisited = isVisited;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public HeritageSite getHeritageSite() {
        return heritageSite;
    }

    public void setHeritageSite(HeritageSite heritageSite) {
        this.heritageSite = heritageSite;
    }

    public Boolean getIsVisited() {
        return isVisited;
    }

    public void setIsVisited(Boolean visited) {
        isVisited = visited;
    }
}
