package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.Objects;

/**
 * Удаляет cookie из запроса, кем бы она ни была выставлена: @CookieParam, фильтром или другой настройкой.
 */
public class DeleteCookieParam implements RequestSetting {

    private final String name;

    public DeleteCookieParam(String name) {
        this.name = Objects.requireNonNull(name, "Cookie name can not be null");
    }

    @Override
    public String key() {
        return "cookie:" + name;
    }

    @Override
    public void apply(ClientRequestContext request) {
        CookieHeader.rewrite(request, cookies -> cookies.remove(name));
    }

    @Override
    public String toString() {
        return "-cookie:" + name;
    }
}
