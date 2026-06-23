package com.myheritagewalk.config;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
import com.myheritagewalk.resources.AuthResource;
import com.myheritagewalk.resources.SiteResource;
import com.myheritagewalk.resources.SelectionResource;
import com.myheritagewalk.filter.SecurityFilter;

public class JaxRsApplication extends Application {
    private final Set<Object> singletons = new HashSet<>();
    private final Set<Class<?>> classes = new HashSet<>();

    public JaxRsApplication() {
        classes.add(AuthResource.class);
        classes.add(SiteResource.class);
        classes.add(SelectionResource.class);
        classes.add(SecurityFilter.class);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return classes;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
