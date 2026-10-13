package io.github.vyacheslavvyacheslav.jaxrsassured.core.clients;

import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.AssuredClientWebTarget;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.TlsVerification;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;

/**
 * Общая основа клиентов: владеет RestEasy-клиентом и строит прокси JAX-RS ресурсов.
 * Прокси строится через AssuredClientWebTarget: поддерживает конфигурацию запроса на ходу
 * (ConfigurationResource) и извлечение ответов в AssuredResponse.
 * Фильтры (например, BearerAuthFilter) регистрируются на таргете в момент построения прокси.
 * Клиент держит пул HTTP-соединений: создавайте один на прогон и закрывайте его в конце.
 */
public abstract class BaseClient {

    private final ResteasyClient client;

    /** Клиент с проверкой TLS-сертификата сервера. */
    protected BaseClient() {
        this(TlsVerification.ENABLED);
    }

    /** {@link TlsVerification#DISABLED} — для тестовых стендов с самоподписанным сертификатом. */
    protected BaseClient(TlsVerification tlsVerification) {
        this.client = HttpClientUtil.restEasyClient(tlsVerification);
    }

    protected <T> T proxy(Class<T> proxyClass, String absoluteURI, ClientRequestFilter... filters) {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(absoluteURI);
        for (ClientRequestFilter filter : filters) {
            target.register(filter);
        }
        return target.proxy(proxyClass, new ExtendedClientProxyFactory());
    }

    public void close() {
        client.close();
    }
}
