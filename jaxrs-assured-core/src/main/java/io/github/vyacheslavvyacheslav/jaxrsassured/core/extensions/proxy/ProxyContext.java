package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import org.jboss.resteasy.client.jaxrs.ProxyConfig;

import java.util.Objects;

/**
 * Как строить прокси ресурса на любом уровне дерева: настройки RestEasy и фабрики.
 * Создается один раз на корне и передается подресурсам и as(...), поэтому фабрики,
 * переданные в корень, действуют на всё дерево. Один экземпляр фабрики строит все прокси дерева,
 * в том числе из разных потоков, поэтому фабрика должна быть потокобезопасной.
 */
public record ProxyContext(ProxyConfig config, ClientProxyFactory clientProxyFactory,
                           ResourceInvokerFactory invokerFactory) {

    public ProxyContext {
        Objects.requireNonNull(config, "ProxyConfig can not be null");
        Objects.requireNonNull(clientProxyFactory, "ClientProxyFactory can not be null");
        Objects.requireNonNull(invokerFactory, "ResourceInvokerFactory can not be null");
    }
}
