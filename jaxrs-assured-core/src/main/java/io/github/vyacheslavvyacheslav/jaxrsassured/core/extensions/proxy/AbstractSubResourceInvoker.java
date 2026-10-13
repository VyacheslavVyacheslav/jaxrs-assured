package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public abstract class AbstractSubResourceInvoker implements MethodInvoker {

    ProxyContext context;
    Class<?> iface;
    Method method;
    ResteasyWebTarget parent;
    Annotation[] jaxParams;
    boolean hasJaxParams;

    public AbstractSubResourceInvoker(final ResteasyWebTarget parent, final Method method, final ProxyContext context) {
        this.context = context;
        this.method = method;
        this.iface = method.getReturnType();
        jaxParams = new Annotation[method.getParameterCount()];
        for (int i = 0; i < jaxParams.length; i++) {
            Annotation[] paramAnnotations = method.getParameterAnnotations()[i];
            for (Annotation annotation : paramAnnotations) {
                if (annotation instanceof PathParam || annotation instanceof MatrixParam) {
                    jaxParams[i] = annotation;
                    hasJaxParams = true;
                    break;
                }
            }
        }
        if (method.isAnnotationPresent(Path.class)) {
            this.parent = parent.path(method.getAnnotation(Path.class).value());
        } else {
            this.parent = parent;
        }
    }
}
