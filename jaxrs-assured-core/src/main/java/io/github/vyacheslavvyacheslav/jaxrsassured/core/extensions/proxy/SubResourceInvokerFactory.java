package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;

import java.lang.reflect.Method;

public class SubResourceInvokerFactory implements ResourceInvokerFactory {

    @Override
    public MethodInvoker createSubInvoker(ResteasyWebTarget parent, Method method, ProxyContext context) {
        return new SubResourceInvoker(parent, method, context);
    }
}
