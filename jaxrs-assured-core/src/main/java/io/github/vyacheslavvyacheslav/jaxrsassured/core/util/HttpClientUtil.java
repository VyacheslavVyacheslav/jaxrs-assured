package io.github.vyacheslavvyacheslav.jaxrsassured.core.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.builder.AssuredResteasyClientBuilderImpl;

import java.time.Duration;

public class HttpClientUtil {

    /**
     * Установка соединения со стендом, включая TLS-рукопожатие (Apache ограничивает его этим же таймаутом).
     */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Ожидание данных от сервера: начала ответа или очередной порции тела.
     */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Ожидание свободного соединения в пуле клиента.
     */
    public static final Duration POOL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Соединений в пуле клиента — всего и на один хост, как по умолчанию в RestEasy.
     */
    public static final int MAX_CONNECTIONS = 50;

    /** Клиент с таймаутами по умолчанию и проверкой TLS. */
    public static ResteasyClient restEasyClient() {
        return restEasyClient(TlsVerification.ENABLED);
    }

    /** Клиент с таймаутами по умолчанию; {@link TlsVerification#DISABLED} — для стендов с самоподписанным сертификатом. */
    public static ResteasyClient restEasyClient(TlsVerification tlsVerification) {
        return restEasyClient(CONNECT_TIMEOUT, READ_TIMEOUT, tlsVerification);
    }

    /** Клиент со своими таймаутами и проверкой TLS. */
    public static ResteasyClient restEasyClient(Duration connectTimeout, Duration readTimeout) {
        return restEasyClient(connectTimeout, readTimeout, TlsVerification.ENABLED);
    }

    /**
     * Без таймаутов Apache HttpClient ждет бесконечно: зависший стенд вешал бы тест навсегда.
     * Проверка TLS выключается только явно: без нее соединение не защищено от подмены сервера.
     */
    public static ResteasyClient restEasyClient(Duration connectTimeout, Duration readTimeout,
                                                TlsVerification tlsVerification) {
        ResteasyClientBuilderImpl resteasyClientBuilder = new AssuredResteasyClientBuilderImpl();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(toMillis(connectTimeout))
                .setSocketTimeout(toMillis(readTimeout))
                .setConnectionRequestTimeout(toMillis(POOL_TIMEOUT))
                .build();
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (tlsVerification == TlsVerification.DISABLED) {
            httpClientBuilder
                    .setSSLContext(SecurityUtility.getSslContextWithoutCertificateCheck())
                    .setSSLHostnameVerifier((s, sslSession) -> true);
        }
        httpClientBuilder
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS)
                .disableContentCompression()
                .disableDefaultUserAgent()
                // без общего хранилища cookie: Set-Cookie из ответа одного теста не уходит в запросах другого
                // (клиенты — синглтоны); cookie отправляются только явно — @CookieParam, фильтры, setCookie
                .disableCookieManagement();

        // без HttpContextProvider Apache создает свой контекст на каждый запрос;
        // пул и таймауты задаются только здесь: при готовом движке настройки билдера RestEasy не применяются
        ApacheHttpClient43Engine engine = new ApacheHttpClient43Engine(httpClientBuilder.build());

        resteasyClientBuilder.httpEngine(engine);
        // JSON (ResteasyJackson2Provider) RestEasy подключает сам из META-INF/services jar resteasy-jackson2-provider:
        // явная регистрация игнорировалась и давала WARN RESTEASY002155 на каждый клиент
        return resteasyClientBuilder.build();
    }

    private static int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
