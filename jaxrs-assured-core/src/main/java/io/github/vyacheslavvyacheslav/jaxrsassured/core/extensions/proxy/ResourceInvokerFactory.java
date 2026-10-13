package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;

import java.lang.reflect.Method;

public interface ResourceInvokerFactory {

    /**
     * @param context контекст родительского прокси: через него строится прокси подресурса
     */
    MethodInvoker createSubInvoker(final ResteasyWebTarget parent, final Method method,
                                   final ProxyContext context);

}
