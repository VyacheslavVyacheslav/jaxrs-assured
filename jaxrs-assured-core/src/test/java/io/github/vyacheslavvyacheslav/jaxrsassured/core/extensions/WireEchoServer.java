package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.path.json.JsonPath;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Настоящий HTTP-сервер на 127.0.0.1 (JDK HttpServer): отвечает JSON с тем, что пришло по сети, —
 * метод, путь, сырая query-строка, заголовки (имена в нижнем регистре), тело строкой.
 * По пути .../set-cookie дополнительно отдает Set-Cookie. Нужен там, где важен запрос «на проводе»:
 * query-строка, заголовок Cookie, сырое тело, поведение Apache HttpClient.
 */
final class WireEchoServer implements AutoCloseable {

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    WireEchoServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::echo);
        server.start();
    }

    String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void echo(HttpExchange exchange) throws IOException {
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("method", exchange.getRequestMethod());
        echo.put("path", exchange.getRequestURI().getRawPath());
        echo.put("query", exchange.getRequestURI().getRawQuery());
        Map<String, List<String>> headers = new TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values));
        echo.put("headers", headers);
        byte[] body = exchange.getRequestBody().readAllBytes();
        echo.put("body", new String(body, StandardCharsets.UTF_8));
        echo.put("bodyBase64", Base64.getEncoder().encodeToString(body));
        if (exchange.getRequestURI().getPath().endsWith("/set-cookie")) {
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=from-server; Path=/");
        }
        byte[] response = mapper.writeValueAsBytes(echo);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    static String body(String echo) {
        return JsonPath.from(echo).getString("body");
    }

    /**
     * Тело байтами — для проверок кодировки.
     */
    static byte[] bodyBytes(String echo) {
        return Base64.getDecoder().decode(JsonPath.from(echo).getString("bodyBase64"));
    }

    static List<String> header(String echo, String name) {
        Map<String, List<String>> headers = JsonPath.from(echo).getMap("headers");
        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    /**
     * Параметры query-строки, декодированные, в порядке появления.
     */
    static Map<String, List<String>> queryParams(String echo) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        String query = JsonPath.from(echo).getString("query");
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            params.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return params;
    }

    /**
     * Cookie из заголовка Cookie (атрибуты старого формата $Version и т.п. пропускаются).
     */
    static Map<String, String> cookies(String echo) {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String headerValue : header(echo, "Cookie")) {
            for (String part : headerValue.split("[;,]")) {
                String cookie = part.trim();
                int equals = cookie.indexOf('=');
                if (equals > 0 && !cookie.startsWith("$")) {
                    cookies.put(cookie.substring(0, equals), cookie.substring(equals + 1));
                }
            }
        }
        return cookies;
    }
}
