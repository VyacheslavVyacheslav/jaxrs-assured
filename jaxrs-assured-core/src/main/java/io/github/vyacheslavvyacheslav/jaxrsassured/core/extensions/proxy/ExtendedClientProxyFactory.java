package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.lang.reflect.Method;
import java.util.Map;

public class ExtendedClientProxyFactory implements ClientProxyFactory {
    @Override
    public ClientProxyInvocationHandler createClientInvoker(Map<Method, MethodInvoker> methodMap, WebTarget target,
                                                            ProxyContext context, RequestConfiguration requestConfiguration) {
        return new ExtendedClientProxy(methodMap, target, context, requestConfiguration);
    }
}
