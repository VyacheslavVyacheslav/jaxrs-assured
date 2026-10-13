package io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.filters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import java.io.IOException;

/**
 * Подставляет Bearer-токен в заголовок Authorization каждого запроса.
 * Токен приходит из проперти сервиса (ключ <service>.token в per-stand файле).
 */
public class BearerAuthFilter implements ClientRequestFilter {

    public static final String AUTH_HEADER_PREFIX = "Bearer ";

    private final String tokenString;

    public BearerAuthFilter(String tokenString) {
        this.tokenString = tokenString;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (tokenString == null || tokenString.isBlank()) {
            return;
        }
        String authHeader = tokenString.startsWith(AUTH_HEADER_PREFIX) ? tokenString : AUTH_HEADER_PREFIX + tokenString;
        requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authHeader);
    }
}
