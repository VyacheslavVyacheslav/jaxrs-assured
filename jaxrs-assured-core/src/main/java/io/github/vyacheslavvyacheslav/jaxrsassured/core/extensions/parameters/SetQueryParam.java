package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.List;
import java.util.Objects;

/**
 * Задает значения query-параметра, заменяя все его значения, в том числе из @QueryParam.
 * Значения — строки как есть: можно отправить то, что не пропускает типизированный параметр метода.
 */
public class SetQueryParam implements RequestSetting {

    private final String name;
    private final List<String> values;

    public SetQueryParam(String name, List<?> values) {
        this.name = Objects.requireNonNull(name, "Query param name can not be null");
        this.values = values.stream()
                .map(value -> String.valueOf(Objects.requireNonNull(value, "Query param value can not be null")))
                .toList();
    }

    @Override
    public String key() {
        return "query:" + name;
    }

    @Override
    public void apply(ClientRequestContext request) {
        request.setUri(QueryString.replace(request.getUri(), name, values));
    }

    @Override
    public String toString() {
        return "query:" + name + "=" + String.join(", ", values);
    }
}
