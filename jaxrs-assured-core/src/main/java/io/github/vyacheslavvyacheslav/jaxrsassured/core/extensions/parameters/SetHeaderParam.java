package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Жестко задает значения заголовка: заменяет все существующие значения этого заголовка в запросе.
 */
public class SetHeaderParam extends HeaderSetting {

    private final List<Object> values;

    public SetHeaderParam(String paramName, List<?> values) {
        super(paramName);
        values.forEach(value -> Objects.requireNonNull(value, "Header value can not be null"));
        this.values = List.copyOf(values);
    }

    @Override
    public void apply(ClientRequestContext request) {
        removeHeader(request.getHeaders(), paramName);
        request.getHeaders().put(paramName, new ArrayList<>(values));
    }

    @Override
    public String toString() {
        return paramName + "=" + values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
