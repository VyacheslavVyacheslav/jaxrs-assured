package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;
import io.restassured.path.json.JsonPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Числа в параметрах на проводе: генератор делает number без format параметром Number, и в него попадают
 * и целые, и дробные, в том числе Double из DTO. DecimalParamConverterProvider пишет дробные без «.0» и экспоненты.
 */
class NumberParamWireTest {

    private final WireEchoServer server = new WireEchoServer();
    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    NumberParamWireTest() throws IOException {
    }

    @AfterEach
    void close() {
        client.close();
        server.close();
    }

    @Test
    void integersShouldGoAsIs() {
        assertEquals("20", query(resource().find(20)));
        assertEquals("79991234567", query(resource().find(79991234567L)), "Телефон не помещается в Integer");
    }

    @Test
    void fractionsShouldGoWithoutTrailingZerosAndExponent() {
        assertEquals("2.5", query(resource().find(2.5)));
        assertEquals("42", query(resource().find(42.0)), "Так приходит id из DTO, где number стал Double");
        assertEquals("0.0001", query(resource().find(0.0001)));
        assertEquals("10000000", query(resource().find(10_000_000.0)));
        assertEquals("0.1", query(resource().find(0.1f)));
        assertEquals("0", query(resource().find(-0.0)));
        assertEquals("2.5", query(resource().find(new BigDecimal("2.50"))));
        assertEquals("1000", query(resource().find(new BigDecimal("1E+3"))));
    }

    @Test
    void nanAndInfinityShouldGoAsIs() {
        assertEquals("NaN", query(resource().find(Double.NaN)));
        assertEquals("Infinity", query(resource().find(Double.POSITIVE_INFINITY)));
    }

    @Test
    void parameterDeclaredAsDoubleShouldAlsoGoWithoutTrailingZero() {
        assertEquals("20", query(resource().findDouble(20.0)));
    }

    @Test
    void listOfNumbersShouldAcceptIntegerListAndConvertEachValue() {
        List<Integer> ids = List.of(1, 2);

        String integers = resource().findMany(ids).extract().asString();
        String mixed = resource().findMany(List.of(1.5, 42.0)).extract().asString();

        assertEquals(List.of("1", "2"), WireEchoServer.queryParams(integers).get("ids"));
        assertEquals(List.of("1.5", "42"), WireEchoServer.queryParams(mixed).get("ids"));
    }

    @Test
    void pathHeaderAndLocatorShouldGoWithoutTrailingZero() {
        assertEquals("/items/42", path(resource().byId(42.0)));
        assertEquals(List.of("42"), WireEchoServer.header(resource().withHeader(42.0).extract().asString(), "X-Count"));
        assertEquals("/stores/42/books", path(resource().store(42.0).books()));
    }

    @Test
    void setQueryParamShouldSendValueAsIs() {
        assertEquals("42.0", query(resource().setQueryParam("x", 42.0).find(null)));
    }

    @Test
    void setHeaderWithNumberShouldGoThroughConverterToo() {
        String echo = resource().setHeader("X-Count", 42.0).withHeader(null).extract().asString();

        assertEquals(List.of("42"), WireEchoServer.header(echo, "X-Count"));
    }

    private NumbersResource resource() {
        return ((AssuredClientWebTarget) client.target(server.baseUri())).proxy(NumbersResource.class);
    }

    private static String query(AssuredResponse response) {
        List<String> values = WireEchoServer.queryParams(response.extract().asString()).get("x");
        return values == null ? null : String.join(",", values);
    }

    private static String path(AssuredResponse response) {
        return JsonPath.from(response.extract().asString()).getString("path");
    }

    interface NumbersResource extends ConfigurationResource<NumbersResource> {

        @GET
        @Path("items")
        AssuredResponse find(@QueryParam("x") Number x);

        @GET
        @Path("items")
        AssuredResponse findDouble(@QueryParam("x") Double x);

        @GET
        @Path("items")
        AssuredResponse findMany(@QueryParam("ids") List<? extends Number> ids);

        @GET
        @Path("items/{id}")
        AssuredResponse byId(@PathParam("id") Number id);

        @GET
        @Path("items")
        AssuredResponse withHeader(@HeaderParam("X-Count") Number count);

        @Path("stores/{id}")
        StoreResource store(@PathParam("id") Number id);
    }

    interface StoreResource extends ConfigurationResource<StoreResource> {

        @GET
        @Path("books")
        AssuredResponse books();
    }
}
