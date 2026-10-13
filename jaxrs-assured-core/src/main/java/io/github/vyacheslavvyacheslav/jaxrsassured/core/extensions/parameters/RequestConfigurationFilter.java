package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.annotation.Priority;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Применяет настройку из ConfigurationResource к итоговому запросу: заголовки, cookie, query-параметры, тело.
 * Фильтры запроса выполняются по возрастанию приоритета, поэтому этот отрабатывает после обычных
 * (BearerAuthFilter и т.п.) и перекрывает выставленные ими значения. Запас до максимума оставлен фильтрам,
 * которым нужно видеть окончательный запрос, например логирующим.
 */
@Priority(RequestConfigurationFilter.PRIORITY)
public class RequestConfigurationFilter implements ClientRequestFilter {

    public static final int PRIORITY = Integer.MAX_VALUE - 1000;

    private static final Logger log = LoggerFactory.getLogger(RequestConfigurationFilter.class);

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (requestContext.getProperty(RequestConfiguration.PROPERTY) instanceof RequestConfiguration configuration) {
            log.debug("{} {}: настройка запроса {}", requestContext.getMethod(), requestContext.getUri(), configuration);
            configuration.applyTo(requestContext);
        }
    }
}
