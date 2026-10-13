package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.extractors;

import io.restassured.RestAssured;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.ClientContext;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.EntityExtractor;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;

import java.util.concurrent.TimeUnit;

/**
 * Превращает ответ RestEasy в AssuredResponse только через публичный API ClientResponse:
 * работает с любым движком и с ответом, подмененным фильтром (abortWith).
 */
public class AssuredEntityExtractor implements EntityExtractor<AssuredResponse> {

    /**
     * Свойство запроса: System.nanoTime() в момент его создания (ставит AssuredClientInvoker) — начало отсчета времени ответа.
     */
    public static final String REQUEST_STARTED_NANOS = AssuredEntityExtractor.class.getName() + ".requestStartedNanos";

    private static final byte[] NO_CONTENT = new byte[0];

    @Override
    public AssuredResponse extractEntity(ClientContext context, Object... args) {
        ClientResponse response = context.getClientResponse();
        try {
            // тело вычитывается сразу и целиком через RestEasy (с ReaderInterceptor'ами),
            // после чего соединение освобождается, а rest-assured работает с копией в памяти
            byte[] content = readBody(response);
            // время ответа — от создания запроса до полностью прочитанного тела, как у TimingFilter rest-assured
            long responseTimeMillis = responseTimeMillis(context.getInvocation());
            return AssuredResponse.of(toHttpResponse(response), content, RestAssured.config(),
                    new ClientInvocationRequestLog(context.getInvocation()), responseTimeMillis);
        } finally {
            response.close();
        }
    }

    private static byte[] readBody(ClientResponse response) {
        if (response.hasEntity()) {
            byte[] body = response.readEntity(byte[].class);
            return body != null ? body : NO_CONTENT;
        }
        if (response.getMediaType() != null) {
            return NO_CONTENT;
        }
        // hasEntity() у RestEasy видит только тело с Content-Type: тело без него читаем явно,
        // а его отсутствие (304, ответ на HEAD) RestEasy сообщает через IllegalStateException
        try {
            byte[] body = response.readEntity(byte[].class);
            return body != null ? body : NO_CONTENT;
        } catch (IllegalStateException noEntity) {
            return NO_CONTENT;
        }
    }

    private static long responseTimeMillis(ClientInvocation invocation) {
        return invocation.getMutableProperties().get(REQUEST_STARTED_NANOS) instanceof Long startedNanos
                ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
                : AssuredResponse.NO_RESPONSE_TIME;
    }

    private static BasicHttpResponse toHttpResponse(ClientResponse response) {
        BasicHttpResponse httpResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, response.getStatus(),
                response.getStatusInfo().getReasonPhrase());
        response.getStringHeaders().forEach((name, values) -> values.forEach(value -> httpResponse.addHeader(name, value)));
        return httpResponse;
    }
}
