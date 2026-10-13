package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response;

import io.restassured.config.LogConfig;

/**
 * Описание отправленного запроса для лога rest-assured: печатается вместе с ответом, когда проверка AssuredResponse
 * падает и в LogConfig включено enableLoggingOfRequestAndResponseIfValidationFails.
 */
@FunctionalInterface
public interface RequestLog {

    RequestLog NONE = logConfig -> null;

    /**
     * @return текст в формате RequestPrinter rest-assured или null, если запрос описать нечем
     */
    String print(LogConfig logConfig);
}
