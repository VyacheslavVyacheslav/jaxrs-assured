package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Прокси без поддержки ConfigurationResource: настройка запроса игнорируется.
 */
public class DefaultClientProxyFactory implements ClientProxyFactory {
    @Override
    public ClientProxyInvocationHandler createClientInvoker(Map<Method, MethodInvoker> methodMap, final WebTarget target,
                                                            final ProxyContext context, RequestConfiguration requestConfiguration) {
        return new ClientProxy(methodMap, target, context);
    }
}
