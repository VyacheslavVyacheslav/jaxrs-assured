package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import jakarta.annotation.Priority;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тестовый «сервер»: последний фильтр цепочки отвечает эхом URL и итоговых заголовков запроса через abortWith
 * (как httpbin.org/headers), запрос в сеть не уходит. Видит заголовки после всех остальных фильтров,
 * включая RequestConfigurationFilter.
 */
@Priority(Integer.MAX_VALUE)
class EchoHeadersFilter implements ClientRequestFilter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        requestContext.getStringHeaders().forEach(headers::put);
        String body = mapper.writeValueAsString(Map.of("uri", requestContext.getUri().toString(), "headers", headers));
        requestContext.abortWith(Response.ok(body, MediaType.APPLICATION_JSON_TYPE).build());
    }

    static String uri(String echoBody) {
        return JsonPath.from(echoBody).getString("uri");
    }

    /**
     * Значения заголовка из тела эха; имя без учета регистра, отсутствующий заголовок — пустой список.
     */
    static List<String> header(String echoBody, String name) {
        Map<String, List<String>> headers = JsonPath.from(echoBody).getMap("headers");
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }
}
