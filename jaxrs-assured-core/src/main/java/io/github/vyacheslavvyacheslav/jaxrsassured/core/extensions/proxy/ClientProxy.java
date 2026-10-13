package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.AssuredCall;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.ConfigurableRequest;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.lang.reflect.Method;
import java.util.Map;

public class ClientProxy implements ClientProxyInvocationHandler {
    protected Map<Method, MethodInvoker> methodMap;
    protected Class<?> clazz;
    protected final WebTarget target;
    protected final ProxyContext context;

    public ClientProxy(final Map<Method, MethodInvoker> methodMap, final WebTarget target, final ProxyContext context) {
        super();
        this.methodMap = methodMap;
        this.target = target;
        this.context = context;
    }

    @Override
    public Class<?> getClazz() {
        return clazz;
    }

    @Override
    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Object invoke(Object o, Method method, Object[] args)
            throws Throwable {
        // equals and hashCode were added for cases where the proxy is added to
        // collections. The Spring transaction management, for example, adds
        // transactional Resources to a Collection, and it calls equals and
        // hashCode.

        MethodInvoker clientInvoker = methodMap.get(method);
        if (clientInvoker == null) {
            if (method.getName().equals("equals")) {
                return o == args[0];
            } else if (method.getName().equals("hashCode")) {
                return this.hashCode();
            } else if (method.getName().equals("toString") && (args == null || args.length == 0)) {
                return this.toString();
            } else if (method.getName().equals("as") && args.length == 1 && args[0] instanceof Class) {
                return ProxyBuilder.proxy((Class<?>) args[0], target, context, RequestConfiguration.EMPTY);
            } else if (method.getName().equals("getResteasyClientInvokers"))
            {
                return methodMap.values();
            } else if (method.isAnnotationPresent(ConfigurableRequest.class) || method.isAnnotationPresent(AssuredCall.class)) {
                throw new UnsupportedOperationException(method.getName() + "(...) недоступен: ресурс "
                        + clazz.getSimpleName() + " построен через DefaultClientProxyFactory, нужна ExtendedClientProxyFactory");
            }
        }

        if (clientInvoker == null) {
            throw new RuntimeException(Messages.MESSAGES.couldNotFindMethod(method));
        }
        return clientInvoker.invoke(args);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ClientProxy))
            return false;
        ClientProxy other = (ClientProxy) obj;
        if (other == this)
            return true;
        if (other.clazz != this.clazz)
            return false;
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    public String toString() {
        return Messages.MESSAGES.resteasyClientProxyFor(clazz.getName());
    }
}
