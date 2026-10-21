package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.WebTarget;

/**
 * Одна настройка запроса из ConfigurationResource: заголовок, cookie, query-параметр, параметр пути или тело.
 * Применяется RequestConfigurationFilter к итоговому запросу — после параметров метода и обычных фильтров.
 * Исключение — параметр пути: к готовому запросу его уже не подставить, он идет в шаблон пути раньше (resolvePath).
 */
public interface RequestSetting {

    /**
     * Повторная настройка с тем же ключом заменяет предыдущую.
     */
    String key();

    void apply(ClientRequestContext request);

    /**
     * Подставляет значения в шаблон пути до создания запроса — раньше параметров метода и значений локаторов,
     * поэтому перекрывает их. По умолчанию путь не меняется.
     */
    default WebTarget resolvePath(WebTarget target) {
        return target;
    }

    /**
     * Настройки тела применяются после остальных — с итоговым Content-Type.
     */
    default boolean isBody() {
        return false;
    }
}
