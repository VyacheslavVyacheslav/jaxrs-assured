package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.AssuredClientWebTarget;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.ExtendedClientInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Локатор подресурса: строит прокси подресурса тем же контекстом, что и родитель,
 * передавая ему настройку родителя на момент вызова. Параметры пути локатора не подставляются сразу,
 * а едут в target подресурса отложенными значениями (AssuredClientWebTarget.withPathParams).
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
                // подставятся при HTTP-вызове, после setPathParam: иначе setPathParam не смог бы их перекрыть
                target = target instanceof AssuredClientWebTarget assuredTarget
                        ? assuredTarget.withPathParams(pathParams)
                        : target.resolveTemplates(pathParams);
            }
        }
        return ProxyBuilder.proxy(iface, target, context, configuration);
    }
}
