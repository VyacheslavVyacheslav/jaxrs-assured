package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import jakarta.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvokerFactory;
import org.jboss.resteasy.util.IsHttpMethod;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfigurationFilter;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ProxyBuilder;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Set;

/**
 * Target, на котором строятся прокси ресурсов: создает Assured-инвокеры и сам регистрирует RequestConfigurationFilter,
 * без которого настройка из ConfigurationResource не применяется к запросам.
 * Тип тела по умолчанию не задается: метод с телом обязан объявить @Consumes на себе или на интерфейсе.
 */
public class AssuredClientWebTarget extends ClientWebTarget implements ClientInvokerFactory {

    protected AssuredClientWebTarget(ResteasyClient client, ClientConfiguration configuration) {
        super(client, configuration);
        registerRequestConfigurationFilter();
    }

    public AssuredClientWebTarget(ResteasyClient client, String uri, ClientConfiguration configuration) throws IllegalArgumentException, NullPointerException {
        super(client, uri, configuration);
        registerRequestConfigurationFilter();
    }

    public AssuredClientWebTarget(ResteasyClient client, URI uri, ClientConfiguration configuration) throws NullPointerException {
        super(client, uri, configuration);
        registerRequestConfigurationFilter();
    }

    public AssuredClientWebTarget(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) throws NullPointerException {
        super(client, uriBuilder, configuration);
        registerRequestConfigurationFilter();
    }

    @Override
    public <T> T proxy(Class<T> proxyInterface) {
        return proxy(proxyInterface, new ExtendedClientProxyFactory());
    }

    public <T> T proxy(Class<T> proxyInterface, ClientProxyFactory clientProxyFactory) {
        client.abortIfClosed();
        return createCustomProxyBuilder(proxyInterface, this, clientProxyFactory).build();
    }

    /**
     * Построитель RestEasy дает его собственный прокси: без setHeader, assured и наших подресурсов.
     */
    @Override
    public <T> org.jboss.resteasy.client.jaxrs.ProxyBuilder<T> proxyBuilder(Class<T> proxyInterface) {
        throw new UnsupportedOperationException("proxyBuilder(...) не поддерживается: прокси ресурса "
                + proxyInterface.getSimpleName() + " стройте через proxy(...)");
    }

    protected <T> ProxyBuilder<T> createCustomProxyBuilder(Class<T> proxyInterface, ClientWebTarget cwt, ClientProxyFactory clientProxyFactory) {
        return ProxyBuilder.builder(proxyInterface, cwt, clientProxyFactory);
    }

    @Override
    protected ClientWebTarget newInstance(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) {
        return new AssuredClientWebTarget(client, uriBuilder, configuration);
    }

    @Override
    public ClientInvoker createClientInvoker(Class<?> clazz, Method method, ProxyConfig config) {
        Set<String> httpMethods = IsHttpMethod.getHttpMethods(method);
        if (httpMethods == null || httpMethods.size() != 1) {
            throw new RuntimeException("Method must use exactly one http method: " + method.toString());
        }
        AssuredClientInvoker invoker = new AssuredClientInvoker(this, clazz, method, config);
        invoker.setHttpMethod(httpMethods.iterator().next());
        return invoker;
    }

    /**
     * Дочерние target (path, resolveTemplate) получают копию конфигурации — фильтр в ней уже есть.
     */
    private void registerRequestConfigurationFilter() {
        if (!getConfiguration().isRegistered(RequestConfigurationFilter.class)) {
            register(RequestConfigurationFilter.class);
        }
    }
}
