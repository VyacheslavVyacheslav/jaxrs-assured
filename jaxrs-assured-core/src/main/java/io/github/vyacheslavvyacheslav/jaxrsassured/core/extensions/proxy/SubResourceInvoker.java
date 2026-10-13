package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.ExtendedClientInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Локатор подресурса: строит прокси подресурса тем же контекстом, что и родитель,
 * передавая ему настройку родителя на момент вызова.
 */
public class SubResourceInvoker extends AbstractSubResourceInvoker implements ExtendedClientInvoker {

    public SubResourceInvoker(final ResteasyWebTarget parent, final Method method, final ProxyContext context) {
        super(parent, method, context);
    }

    @Override
    public Object invoke(Object[] args, RequestConfiguration configuration) {
        WebTarget target = parent;
        if (hasJaxParams) {
            HashMap<String, Object> pathParams = new HashMap<String, Object>();
            for (int i = 0; i < jaxParams.length; i++) {
                if (jaxParams[i] instanceof PathParam) {
                    pathParams.put(((PathParam) jaxParams[i]).value(), args[i]);
                } else if (jaxParams[i] instanceof MatrixParam) {
                    target = target.matrixParam(((MatrixParam) jaxParams[i]).value(), args[i]);
                }
            }
            if (!pathParams.isEmpty()) {
                target = target.resolveTemplates(pathParams);
            }
        }
        return ProxyBuilder.proxy(iface, target, context, configuration);
    }
}
