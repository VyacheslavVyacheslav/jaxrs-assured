package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

/**
 * Инвокер, принимающий настройку запроса при вызове. Сам инвокер настройку не хранит:
 * она живет в прокси, поэтому один инвокер обслуживает прокси с разными настройками.
 */
public interface ExtendedClientInvoker extends MethodInvoker {

    Object invoke(Object[] args, RequestConfiguration configuration);

    @Override
    default Object invoke(Object[] args) {
        return invoke(args, RequestConfiguration.EMPTY);
    }
}
