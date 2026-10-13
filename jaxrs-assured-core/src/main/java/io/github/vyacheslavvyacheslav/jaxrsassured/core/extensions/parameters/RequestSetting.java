package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

/**
 * Одна настройка запроса из ConfigurationResource: заголовок, cookie, query-параметр или тело.
 * Применяется RequestConfigurationFilter к итоговому запросу — после параметров метода и обычных фильтров.
 */
public interface RequestSetting {

    /**
     * Повторная настройка с тем же ключом заменяет предыдущую.
     */
    String key();

    void apply(ClientRequestContext request);

    /**
     * Настройки тела применяются после остальных — с итоговым Content-Type.
     */
    default boolean isBody() {
        return false;
    }
}
