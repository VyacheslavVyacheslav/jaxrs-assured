package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

/**
 * Удаляет заголовок из запроса, кем бы он ни был выставлен: @HeaderParam, фильтром или другой настройкой.
 */
public class DeleteHeaderParam extends HeaderSetting {

    public DeleteHeaderParam(String paramName) {
        super(paramName);
    }

    @Override
    public void apply(ClientRequestContext request) {
        removeHeader(request.getHeaders(), paramName);
    }

    @Override
    public String toString() {
        return "-" + paramName;
    }
}
