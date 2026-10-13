package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Замена query-параметра в URI без UriBuilder: значения уходят как есть, без обработки шаблонов {…},
 * поэтому в негативных проверках можно отправить любую строку.
 */
final class QueryString {

    private QueryString() {
    }

    /**
     * Убирает все значения параметра и добавляет новые в конец; пустой список — просто удаление.
     */
    static URI replace(URI uri, String name, List<String> values) {
        String raw = uri.toString();
        String fragment = "";
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            fragment = raw.substring(hash);
            raw = raw.substring(0, hash);
        }
        int question = raw.indexOf('?');
        String base = question < 0 ? raw : raw.substring(0, question);
        List<String> pairs = new ArrayList<>();
        if (question >= 0) {
            for (String pair : raw.substring(question + 1).split("&")) {
                if (!pair.isEmpty() && !decodedName(pair).equals(name)) {
                    pairs.add(pair);
                }
            }
        }
        values.forEach(value -> pairs.add(encode(name) + "=" + encode(value)));
        return URI.create(pairs.isEmpty() ? base + fragment : base + "?" + String.join("&", pairs) + fragment);
    }

    private static String decodedName(String pair) {
        int equals = pair.indexOf('=');
        return URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        // пробел — %20, как у RestEasy в @QueryParam, а не «+» из URLEncoder
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
