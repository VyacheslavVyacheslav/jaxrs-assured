package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.core.MultivaluedMap;

import java.util.Locale;
import java.util.Objects;

/**
 * Настройка заголовка. Имена заголовков регистронезависимы — и в ключе, и при удалении из запроса.
 */
public abstract class HeaderSetting implements RequestSetting {

    protected final String paramName;

    protected HeaderSetting(String paramName) {
        this.paramName = Objects.requireNonNull(paramName, "Header name can not be null");
    }

    public String getParamName() {
        return paramName;
    }

    @Override
    public String key() {
        return "header:" + paramName.toLowerCase(Locale.ROOT);
    }

    /**
     * Удаляем все варианты написания имени, не полагаясь на реализацию карты.
     */
    static void removeHeader(MultivaluedMap<String, Object> headers, String name) {
        headers.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
    }
}
