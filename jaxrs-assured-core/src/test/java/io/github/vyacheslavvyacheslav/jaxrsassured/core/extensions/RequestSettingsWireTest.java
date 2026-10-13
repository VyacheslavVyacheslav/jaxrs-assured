package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cookie, query-параметры и тело из ConfigurationResource — на уровне сети: запрос уходит через настоящий
 * Apache HttpClient на локальный WireEchoServer, проверяется то, что сервер реально получил.
 */
class RequestSettingsWireTest {

    private final WireEchoServer server = new WireEchoServer();
    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    RequestSettingsWireTest() throws IOException {
    }

    @AfterEach
    void close() {
        client.close();
        server.close();
    }

    @Test
    void setQueryParamShouldSendValueRejectedByTypedParameter() {
        String echo = resource().setQueryParam("limit", "abc").list(10, 1).extract().asString();

        assertEquals(Map.of("limit", List.of("abc"), "page", List.of("1")), WireEchoServer.queryParams(echo));
    }

    @Test
    void deleteQueryParamShouldRemoveParameterFromMethodArgument() {
        String echo = resource().deleteQueryParam("page").list(10, 1).extract().asString();

        assertEquals(Map.of("limit", List.of("10")), WireEchoServer.queryParams(echo));
    }

    @Test
    void setQueryParamShouldSendSeveralValuesAndSpecialCharactersAsIs() {
        WireResource resource = resource().setQueryParam("extra", "old");
        resource.setQueryParam("extra", "a b&c=d", "{x}", 5);

        String echo = resource.list(null, null).extract().asString();

        assertEquals(Map.of("extra", List.of("a b&c=d", "{x}", "5")), WireEchoServer.queryParams(echo));
    }

    @Test
    void cookieParamShouldReachServerWithoutCookieManagement() {
        String echo = resource().withCookie("param-value").extract().asString();

        assertEquals(Map.of("fromParam", "param-value"), WireEchoServer.cookies(echo));
    }

    @Test
    void setCookieShouldReplaceCookieParamAndAddNewCookie() {
        String echo = resource().setCookie("fromParam", "configured").setCookie("session", "abc")
                .withCookie("param-value").extract().asString();

        assertEquals(Map.of("fromParam", "configured", "session", "abc"), WireEchoServer.cookies(echo));
    }

    @Test
    void setCookieShouldKeepOtherCookiesFromCookieParam() {
        String echo = resource().setCookie("session", "abc").withCookie("param-value").extract().asString();

        assertEquals(Map.of("fromParam", "param-value", "session", "abc"), WireEchoServer.cookies(echo));
    }

    @Test
    void deleteCookieShouldRemoveCookieParam() {
        String echo = resource().deleteCookie("fromParam").withCookie("param-value").extract().asString();

        assertEquals(List.of(), WireEchoServer.header(echo, "Cookie"));
    }

    @Test
    void cookiesFromResponsesShouldNotLeakIntoNextRequests() {
        WireResource resource = resource();
        resource.login();

        String echo = resource.plain().extract().asString();

        assertEquals(List.of(), WireEchoServer.header(echo, "Cookie"));
    }

    @Test
    void bodyShouldBeSerializedDtoWithoutSetBody() {
        String echo = resource().create(item("dto")).extract().asString();

        assertEquals("{\"name\":\"dto\"}", WireEchoServer.body(echo));
    }

    @Test
    void setBodyShouldSendRawStringWithContentTypeFromConsumes() {
        String echo = resource().setBody("{bad json").create(item("dto")).extract().asString();

        assertEquals("{bad json", WireEchoServer.body(echo));
        assertEquals(List.of("application/json"), WireEchoServer.header(echo, "Content-Type"));
    }

    /**
     * Тело применяется последним: Content-Type, заданный позже setBody, все равно определяет кодировку тела.
     */
    @Test
    void setBodyShouldBeEncodedWithFinalContentTypeCharset() {
        String contentType = "text/plain;charset=ISO-8859-1";

        String echo = resource().setBody("café").setHeader("Content-Type", contentType)
                .create(item("dto")).extract().asString();

        assertEquals(List.of(contentType), WireEchoServer.header(echo, "Content-Type"));
        assertArrayEquals("café".getBytes(StandardCharsets.ISO_8859_1), WireEchoServer.bodyBytes(echo));
    }

    @Test
    void setBodyInsideAssuredShouldApplyOnlyToThisCall() {
        WireResource resource = resource();

        String inside = resource.assured(r -> r.setBody("{bad").create(item("dto"))).extract().asString();
        String after = resource.create(item("dto")).extract().asString();

        assertEquals("{bad", WireEchoServer.body(inside));
        assertEquals("{\"name\":\"dto\"}", WireEchoServer.body(after));
    }

    @Test
    void resetShouldRemoveCookieQueryAndBodySettings() {
        WireResource resource = resource().setCookie("session", "abc").setQueryParam("limit", "abc").setBody("{bad");

        resource.reset();

        String list = resource.list(10, 1).extract().asString();
        String create = resource.create(item("dto")).extract().asString();
        assertEquals(Map.of("limit", List.of("10"), "page", List.of("1")), WireEchoServer.queryParams(list));
        assertEquals(List.of(), WireEchoServer.header(list, "Cookie"));
        assertEquals("{\"name\":\"dto\"}", WireEchoServer.body(create));
    }

    @Test
    void toStringShouldShowAllSettings() {
        WireResource resource = resource().setHeader("X-Actor", "qa").setCookie("session", "abc")
                .setQueryParam("limit", "abc").deleteQueryParam("page").setBody("{}");

        assertEquals("WireResource [X-Actor=qa, cookie:session=abc, query:limit=abc, -query:page, body={}]",
                resource.toString());
    }

    @Test
    void failedCheckShouldPrintFinalQueryAndBody() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RestAssuredConfig logOnFailure = RestAssuredConfig.config().logConfig(LogConfig.logConfig()
                .defaultStream(new PrintStream(output, true, StandardCharsets.UTF_8))
                .enableLoggingOfRequestAndResponseIfValidationFails());
        AssuredResponse response = resource().setQueryParam("limit", "abc").setBody("{bad json")
                .create(item("dto")).modifiedResponse(logOnFailure);

        assertThrows(AssertionError.class, () -> response.statusCode(500));

        String printed = output.toString(StandardCharsets.UTF_8);
        String request = printed.substring(0, printed.indexOf("HTTP/1.1 200"));
        assertTrue(request.contains("Request URI:\t" + server.baseUri() + "/items?limit=abc"), printed);
        assertTrue(request.contains("{bad json"), printed);
        assertFalse(request.contains("\"dto\""), printed);
    }

    private WireResource resource() {
        return ((AssuredClientWebTarget) client.target(server.baseUri())).proxy(WireResource.class);
    }

    private static Item item(String name) {
        Item item = new Item();
        item.name = name;
        return item;
    }

    public static class Item {
        public String name;
    }

    interface WireResource extends ConfigurationResource<WireResource> {

        @GET
        @Path("items")
        AssuredResponse list(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page);

        @GET
        @Path("items")
        AssuredResponse withCookie(@CookieParam("fromParam") String fromParam);

        @GET
        @Path("items")
        AssuredResponse plain();

        @GET
        @Path("set-cookie")
        AssuredResponse login();

        @POST
        @Path("items")
        @Consumes(MediaType.APPLICATION_JSON)
        AssuredResponse create(Item item);
    }
}
