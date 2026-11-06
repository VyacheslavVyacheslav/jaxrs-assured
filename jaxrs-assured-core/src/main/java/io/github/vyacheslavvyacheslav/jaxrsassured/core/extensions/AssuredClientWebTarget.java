package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvokerFactory;
import org.jboss.resteasy.util.IsHttpMethod;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.DecimalParamConverterProvider;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfigurationFilter;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ProxyBuilder;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Target, на котором строятся прокси ресурсов: создает Assured-инвокеры и сам регистрирует RequestConfigurationFilter,
 * без которого настройка из ConfigurationResource не применяется к запросам, и DecimalParamConverterProvider —
 * дробные числа в параметрах без «.0» и экспоненты.
 * Тип тела по умолчанию не задается: метод с телом обязан объявить @Consumes на себе или на интерфейсе.
 * Может нести отложенные значения параметров пути (withPathParams): так локаторы передают подресурсу свой адрес.
 */
public class AssuredClientWebTarget extends ClientWebTarget implements ClientInvokerFactory {

    /**
     * Значения параметров пути, которые подставятся при HTTP-вызове; шаблоны {name} до тех пор остаются в пути.
     */
    private final Map<String, Object> pathParams;

    protected AssuredClientWebTarget(ResteasyClient client, ClientConfiguration configuration) {
        super(client, configuration);
        this.pathParams = Map.of();
        registerCoreProviders();
    }

    public AssuredClientWebTarget(ResteasyClient client, String uri, ClientConfiguration configuration) throws IllegalArgumentException, NullPointerException {
        super(client, uri, configuration);
        this.pathParams = Map.of();
        registerCoreProviders();
    }

    public AssuredClientWebTarget(ResteasyClient client, URI uri, ClientConfiguration configuration) throws NullPointerException {
        super(client, uri, configuration);
        this.pathParams = Map.of();
        registerCoreProviders();
    }

    public AssuredClientWebTarget(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) throws NullPointerException {
        this(client, uriBuilder, configuration, Map.of());
    }

    private AssuredClientWebTarget(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration,
                                   Map<String, Object> pathParams) {
        super(client, uriBuilder, configuration);
        this.pathParams = pathParams;
        registerCoreProviders();
    }

    /**
     * Тот же target с отложенными значениями параметров пути: шаблоны {name} остаются в пути, а значения подставляет
     * resolvePathParams при HTTP-вызове — уже после setPathParam, поэтому setPathParam их перекрывает.
     */
    public AssuredClientWebTarget withPathParams(Map<String, ?> values) {
        client.abortIfClosed();
        Map<String, Object> merged = new HashMap<>(pathParams);
        values.forEach((name, value) -> merged.put(Objects.requireNonNull(name, "Path param name can not be null"),
                Objects.requireNonNull(value, "Path param " + name + " can not be null")));
        return new AssuredClientWebTarget(client, uriBuilder.clone(), configuration, Map.copyOf(merged));
    }

    /**
     * Подставляет отложенные значения параметров пути. Шаблоны, подставленные раньше (setPathParam), уже не меняются.
     */
    public WebTarget resolvePathParams() {
        return pathParams.isEmpty() ? this : resolveTemplates(pathParams);
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

    /**
     * path, resolveTemplate, queryParam и т.п. сохраняют отложенные значения параметров пути.
     */
    @Override
    protected ClientWebTarget newInstance(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) {
        return new AssuredClientWebTarget(client, uriBuilder, configuration, pathParams);
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
     * Фильтр настройки запроса и конвертер дробных чисел в параметрах. Дочерние target (path, resolveTemplate)
     * получают копию конфигурации — в ней они уже есть.
     */
    private void registerCoreProviders() {
        if (!getConfiguration().isRegistered(RequestConfigurationFilter.class)) {
            register(RequestConfigurationFilter.class);
        }
        if (!getConfiguration().isRegistered(DecimalParamConverterProvider.class)) {
            register(DecimalParamConverterProvider.class);
        }
    }
}
