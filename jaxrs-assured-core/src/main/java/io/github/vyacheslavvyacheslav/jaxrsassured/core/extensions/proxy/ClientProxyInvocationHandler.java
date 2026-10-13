package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import java.lang.reflect.InvocationHandler;

public interface ClientProxyInvocationHandler extends InvocationHandler {
    Class<?> getClazz();
    void setClazz(Class<?> clazz);
}
