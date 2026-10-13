package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cookie запроса живут в одном заголовке «Cookie: a=1; b=2». Разбираем его, меняем и собираем заново,
 * сохраняя cookie из @CookieParam и фильтров. Атрибуты старого формата ($Version, $Path, $Domain) отбрасываются.
 */
final class CookieHeader {

    private CookieHeader() {
    }

    static void rewrite(ClientRequestContext request, Consumer<Map<String, String>> change) {
        Map<String, String> cookies = new LinkedHashMap<>();
        List<String> headerValues = request.getStringHeaders().get(HttpHeaders.COOKIE);
        if (headerValues != null) {
            headerValues.forEach(value -> parse(value, cookies));
        }
        change.accept(cookies);
        HeaderSetting.removeHeader(request.getHeaders(), HttpHeaders.COOKIE);
        if (!cookies.isEmpty()) {
            request.getHeaders().putSingle(HttpHeaders.COOKIE, cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
    }

    private static void parse(String headerValue, Map<String, String> cookies) {
        for (String part : headerValue.split("[;,]")) {
            String cookie = part.trim();
            int equals = cookie.indexOf('=');
            if (equals <= 0 || cookie.startsWith("$")) {
                continue;
            }
            cookies.put(cookie.substring(0, equals).trim(), cookie.substring(equals + 1).trim());
        }
    }
}
