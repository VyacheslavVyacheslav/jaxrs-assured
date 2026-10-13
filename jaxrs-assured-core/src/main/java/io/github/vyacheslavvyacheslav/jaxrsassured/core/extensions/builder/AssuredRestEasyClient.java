package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.builder;

import jakarta.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientImpl;
import org.jboss.resteasy.concurrent.ContextualScheduledExecutorService;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.AssuredClientWebTarget;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class AssuredRestEasyClient extends ResteasyClientImpl {

    protected AssuredRestEasyClient(ClientHttpEngine httpEngine, ExecutorService asyncInvocationExecutor, boolean cleanupExecutor, ContextualScheduledExecutorService scheduledExecutorService, ClientConfiguration configuration) {
        super(httpEngine, asyncInvocationExecutor, cleanupExecutor, scheduledExecutorService, configuration);
    }

    @Override
    protected ResteasyWebTarget createClientWebTarget(ResteasyClientImpl client, String uri,
                                                      ClientConfiguration configuration) {
        return new AssuredClientWebTarget(client, uri, configuration);
    }

    @Override
    protected ResteasyWebTarget createClientWebTarget(ResteasyClientImpl client, URI uri, ClientConfiguration configuration) {
        return new AssuredClientWebTarget(client, uri, configuration);
    }

    @Override
    protected ResteasyWebTarget createClientWebTarget(ResteasyClientImpl client, UriBuilder uriBuilder,
                                                      ClientConfiguration configuration) {
        return new AssuredClientWebTarget(client, uriBuilder, configuration);
    }
}
