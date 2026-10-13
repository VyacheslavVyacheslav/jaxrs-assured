package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;

public abstract class ProxyBuilder<T> {

    public static <T> ProxyBuilder<T> builder(Class<T> iface, WebTarget webTarget) {
        return builder(iface, webTarget, new ExtendedClientProxyFactory());
    }

    public static <T> ProxyBuilder<T> builder(Class<T> iface, WebTarget webTarget, ClientProxyFactory clientProxyFactory) {
        return builder(iface, webTarget, clientProxyFactory, new SubResourceInvokerFactory());
    }

    public static <T> ProxyBuilder<T> builder(Class<T> iface, WebTarget webTarget,
                                              ClientProxyFactory clientProxyFactory,
                                              ResourceInvokerFactory invokerFactory) {
        return new ProxyBuilderImplementation<>(iface, webTarget, clientProxyFactory, invokerFactory);
    }

    /**
     * Корень дерева прокси с фабриками по умолчанию.
     */
    public static <T> T proxy(final Class<T> iface, WebTarget base, final ProxyConfig config) {
        return builder(iface, base).build(config);
    }

    /**
     * Подресурс или as(...): строится тем же контекстом, что и родитель, — с его фабриками и настройками RestEasy.
     */
    public static <T> T proxy(final Class<T> iface, WebTarget base, ProxyContext context,
                              RequestConfiguration requestConfiguration) {
        return builder(iface, base, context.clientProxyFactory(), context.invokerFactory())
                .build(context.config(), requestConfiguration);
    }

    public abstract ProxyBuilder<T> classloader(ClassLoader cl);

    public abstract ProxyBuilder<T> defaultProduces(MediaType type);

    public abstract ProxyBuilder<T> defaultConsumes(MediaType type);

    public abstract ProxyBuilder<T> defaultProduces(String type);

    public abstract ProxyBuilder<T> defaultConsumes(String type);

    public abstract T build(ProxyConfig config);

    public abstract T build(ProxyConfig config, RequestConfiguration requestConfiguration);

    public abstract T build();

}
