package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.List;
import java.util.Objects;

/**
 * Удаляет query-параметр из запроса, в том числе выставленный через @QueryParam.
 */
public class DeleteQueryParam implements RequestSetting {

    private final String name;

    public DeleteQueryParam(String name) {
        this.name = Objects.requireNonNull(name, "Query param name can not be null");
    }

    @Override
    public String key() {
        return "query:" + name;
    }

    @Override
    public void apply(ClientRequestContext request) {
        request.setUri(QueryString.replace(request.getUri(), name, List.of()));
    }

    @Override
    public String toString() {
        return "-query:" + name;
    }
}
