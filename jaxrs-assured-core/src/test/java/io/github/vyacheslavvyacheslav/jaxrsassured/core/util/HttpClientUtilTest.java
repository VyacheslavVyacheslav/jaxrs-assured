package io.github.vyacheslavvyacheslav.jaxrsassured.core.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Клиент для тестов: создается без предупреждений в логах, а зависший стенд роняет запрос по таймауту,
 * а не вешает тест навсегда. «Зависший стенд» — локальный ServerSocket: соединение устанавливается
 * (очередь accept), но ответа нет. Проверка TLS включена по умолчанию и выключается только явно —
 * проверяется на локальном HTTPS-сервере с самоподписанным сертификатом (tls/self-signed.p12).
 */
class HttpClientUtilTest {

    @Test
    void tlsVerificationIsOnByDefault() throws Exception {
        HttpsServer server = selfSignedServer();
        try (ResteasyClient client = HttpClientUtil.restEasyClient()) {
            ProcessingException failure = assertThrows(ProcessingException.class,
                    () -> client.target(uri(server)).request().get().close());
            assertTrue(causedBy(failure, SSLException.class), () -> "Ожидался отказ TLS: " + failure);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledTlsVerificationAcceptsSelfSignedCertificate() throws Exception {
        HttpsServer server = selfSignedServer();
        try (ResteasyClient client = HttpClientUtil.restEasyClient(TlsVerification.DISABLED)) {
            try (Response response = client.target(uri(server)).request().get()) {
                assertEquals(200, response.getStatus());
                assertEquals("ok", response.readEntity(String.class));
            }
        } finally {
            server.stop(0);
        }
    }

    /** HTTPS-сервер на 127.0.0.1 с самоподписанным сертификатом на localhost и 127.0.0.1, отвечает "ok". */
    private static HttpsServer selfSignedServer() throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = HttpClientUtilTest.class.getResourceAsStream("/tls/self-signed.p12")) {
            keyStore.load(in, password);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, password);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String uri(HttpsServer server) {
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    /**
     * RestEasy сам подключает JSON-провайдер из META-INF/services; повторная регистрация давала WARN RESTEASY002155.
     */
    @Test
    void clientShouldBeCreatedWithJsonProviderAndWithoutWarnings() {
        Logger resteasyLog = (Logger) LoggerFactory.getLogger("org.jboss.resteasy");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        resteasyLog.addAppender(events);
        try (ResteasyClient client = HttpClientUtil.restEasyClient()) {
            assertTrue(client.getConfiguration().isRegistered(ResteasyJackson2Provider.class));
        } finally {
            resteasyLog.detachAppender(events);
        }

        List<String> warnings = events.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertEquals(List.of(), warnings);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    /**
     * Запас на случай, если таймауты не сработают: тест упадет, а не повиснет.
     */
    private static final Duration TEST_LIMIT = Duration.ofSeconds(15);

    @Test
    void silentServerShouldFailByReadTimeout() throws IOException {
        assertFailsByTimeout("http");
    }

    /**
     * TLS-рукопожатие ограничено таймаутом соединения.
     */
    @Test
    void silentServerShouldFailByTimeoutDuringTlsHandshake() throws IOException {
        assertFailsByTimeout("https");
    }

    private static void assertFailsByTimeout(String scheme) throws IOException {
        try (ServerSocket silentServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            String uri = scheme + "://127.0.0.1:" + silentServer.getLocalPort();
            ResteasyClient client = HttpClientUtil.restEasyClient(TIMEOUT, TIMEOUT);
            try {
                ProcessingException failure = assertTimeoutPreemptively(TEST_LIMIT,
                        () -> assertThrows(ProcessingException.class, () -> client.target(uri).request().get()));
                assertTrue(causedBySocketTimeout(failure), () -> "Unexpected failure: " + failure);
            } finally {
                client.close();
            }
        }
    }

    private static boolean causedBySocketTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
