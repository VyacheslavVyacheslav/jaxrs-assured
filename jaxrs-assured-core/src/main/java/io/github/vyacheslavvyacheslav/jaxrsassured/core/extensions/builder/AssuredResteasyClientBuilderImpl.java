package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.builder;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.concurrent.ContextualExecutors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class AssuredResteasyClientBuilderImpl extends ResteasyClientBuilderImpl {

    @Override
    protected ResteasyClient createResteasyClient(ClientHttpEngine engine, ExecutorService executor, boolean cleanupExecutor,
                                                  ScheduledExecutorService scheduledExecutorService, ClientConfiguration config) {
        return new AssuredRestEasyClient(engine, executor, cleanupExecutor,
                ContextualExecutors.wrapOrLookup(scheduledExecutorService), config);
    }
}
