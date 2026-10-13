package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.Objects;

/**
 * Задает cookie запроса, заменяя cookie с тем же именем (имена cookie чувствительны к регистру).
 */
public class SetCookieParam implements RequestSetting {

    private final String name;
    private final String value;

    public SetCookieParam(String name, String value) {
        this.name = Objects.requireNonNull(name, "Cookie name can not be null");
        this.value = Objects.requireNonNull(value, "Cookie value can not be null");
    }

    @Override
    public String key() {
        return "cookie:" + name;
    }

    @Override
    public void apply(ClientRequestContext request) {
        CookieHeader.rewrite(request, cookies -> cookies.put(name, value));
    }

    @Override
    public String toString() {
        return "cookie:" + name + "=" + value;
    }
}
