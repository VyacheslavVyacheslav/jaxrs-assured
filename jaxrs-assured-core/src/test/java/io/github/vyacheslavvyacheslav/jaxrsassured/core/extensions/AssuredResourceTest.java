package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.filters.BearerAuthFilter;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * assured(r -> ...): тот же метод ресурса, но вместо DTO — AssuredResponse для проверок.
 * Запросы в сеть не уходят — отвечают фильтры (EchoHeadersFilter и подмена ответа через abortWith).
 */
class AssuredResourceTest {

    private static final String BASE_URI = "http://localhost";

    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void dtoMethodShouldStillReturnDtoOutsideAssured() {
        EchoDto dto = echo().getItem();

        assertEquals(BASE_URI + "/item", dto.uri);
    }

    @Test
    void assuredShouldReturnResponseOfDtoMethod() {
        AssuredResponse response = echo().assured(r -> r.getItem());

        response.statusCode(200);
        assertEquals(BASE_URI + "/item", EchoHeadersFilter.uri(response.extract().asString()));
    }

    @Test
    void assuredShouldNotThrowOnErrorStatus() {
        ItemResource resource = resource(new NotFoundFilter());

        assertThrows(NotFoundException.class, resource::getItem);
        resource.assured(r -> r.getItem())
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void assuredShouldSupportVoidAndPrimitiveMethods() {
        ItemResource resource = echo();

        resource.assured(r -> r.deleteItem()).statusCode(200);
        resource.assured(r -> r.count()).statusCode(200);
    }

    @Test
    void assuredShouldApplyResourceConfiguration() {
        ItemResource resource = echo();
        resource.setHeader("X-Custom", "configured");

        AssuredResponse response = resource.assured(r -> r.getItem());

        assertEquals(List.of("configured"), header(response, "X-Custom"));
    }

    @Test
    void configurationInsideAssuredShouldApplyOnlyToThisCall() {
        ItemResource resource = resource(new BearerAuthFilter("token"), new EchoHeadersFilter());
        resource.setHeader("X-Custom", "resource");

        AssuredResponse response = resource.assured(r -> r.deleteHeader(HttpHeaders.AUTHORIZATION)
                .setHeader("X-Custom", "call")
                .getItem());

        assertEquals(List.of(), header(response, HttpHeaders.AUTHORIZATION));
        assertEquals(List.of("call"), header(response, "X-Custom"));
        assertEquals("ItemResource [X-Custom=resource]", resource.toString());
        AssuredResponse after = resource.assured(r -> r.getItem());
        assertEquals(List.of("Bearer token"), header(after, HttpHeaders.AUTHORIZATION));
        assertEquals(List.of("resource"), header(after, "X-Custom"));
    }

    @Test
    void assuredWithoutHttpCallShouldFail() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> echo().assured(r -> r.setHeader("X-Custom", "value")));

        assertTrue(failure.getMessage().contains("не было HTTP-вызова"), failure::getMessage);
    }

    @Test
    void assuredWithTwoHttpCallsShouldFailBeforeSecondRequest() {
        CountingFilter counter = new CountingFilter();
        ItemResource resource = resource(counter, new EchoHeadersFilter());

        assertThrows(IllegalStateException.class, () -> resource.assured(r -> {
            r.getItem();
            r.getItem();
        }));
        assertEquals(1, counter.requests.get());
    }

    @Test
    void subResourceLocatorInsideAssuredShouldFailButAssuredOnSubResourceShouldWork() {
        ItemResource resource = echo();

        assertThrows(UnsupportedOperationException.class, () -> resource.assured(r -> r.sub()));

        AssuredResponse response = resource.sub().assured(r -> r.getItem());

        assertEquals(BASE_URI + "/sub/item", EchoHeadersFilter.uri(response.extract().asString()));
    }

    @Test
    void nestedAssuredShouldFail() {
        assertThrows(IllegalStateException.class, () -> echo().assured(r -> r.assured(inner -> inner.getItem())));
    }

    @Test
    void reactiveMethodInsideAssuredShouldFail() {
        assertThrows(UnsupportedOperationException.class, () -> echo().assured(r -> r.getItemAsync()));
    }

    /** Форма обычного ресурса-справочника: выборка с query-параметром и запись по ключу. */
    interface StatusesResource extends ConfigurationResource<StatusesResource> {

        @GET
        EchoDto getStatuses(@QueryParam("ids") String ids);

        @GET
        @Path("{id}")
        EchoDto getStatus(@PathParam("id") String id);
    }

    @Test
    void realResourceShouldSupportAssured() {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        target.register(new EchoHeadersFilter());
        StatusesResource statuses = target.proxy(StatusesResource.class, new ExtendedClientProxyFactory());

        AssuredResponse response = statuses.assured(r -> r.getStatus("some-id"));

        response.statusCode(200);
        assertEquals(BASE_URI + "/some-id", EchoHeadersFilter.uri(response.extract().asString()));
    }

    @Test
    void responseTimeShouldBeMeasured() {
        ItemResource resource = echo();

        assertTrue(resource.assured(r -> r.getItem()).extract().time() >= 0);
        assertTrue(resource.getItemResponse().extract().time() >= 0);
    }

    /**
     * Как enableLoggingOfRequestAndResponseIfValidationFails: при упавшей проверке печатается запрос с итоговыми заголовками
     * (после фильтров и setHeader, Authorization скрыт черным списком) и телом, затем ответ.
     * Конфиг задается одному ответу через modifiedResponse, а не глобальному RestAssured.config:
     * подмена общего статического поля мешала бы тестам в соседних потоках.
     */
    @Test
    void failedCheckShouldPrintRequestWithFinalHeadersAndBody() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RestAssuredConfig logOnFailure = RestAssuredConfig.config().logConfig(LogConfig.logConfig()
                .defaultStream(new PrintStream(output, true, StandardCharsets.UTF_8))
                .enableLoggingOfRequestAndResponseIfValidationFails()
                .blacklistHeader(HttpHeaders.AUTHORIZATION));
        ItemResource resource = resource(new BearerAuthFilter("token"), new EchoHeadersFilter());
        resource.setHeader("X-Custom", "configured");
        EchoDto item = new EchoDto();
        item.uri = "sent-in-body";

        AssuredResponse response = resource.assured(r -> r.createItem(item)).modifiedResponse(logOnFailure);
        response.statusCode(200);
        assertEquals("", output.toString(StandardCharsets.UTF_8));

        assertThrows(AssertionError.class, () -> response.statusCode(500));

        String printed = output.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("HTTP/1.1 200"), printed);
        String request = printed.substring(0, printed.indexOf("HTTP/1.1 200"));
        assertTrue(request.contains("Request method:\tPOST"), printed);
        assertTrue(request.contains("Request URI:\t" + BASE_URI + "/item"), printed);
        assertTrue(request.contains("X-Custom=configured"), printed);
        assertTrue(request.contains("Authorization=[ BLACKLISTED ]"), printed);
        assertFalse(request.contains("Bearer token"), printed);
        assertTrue(request.contains("sent-in-body"), printed);
    }

    private ItemResource echo() {
        return resource(new EchoHeadersFilter());
    }

    private ItemResource resource(ClientRequestFilter... filters) {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        for (ClientRequestFilter filter : filters) {
            target.register(filter);
        }
        return target.proxy(ItemResource.class, new ExtendedClientProxyFactory());
    }

    private static List<String> header(AssuredResponse response, String name) {
        return EchoHeadersFilter.header(response.extract().asString(), name);
    }

    @Priority(Integer.MAX_VALUE)
    static class NotFoundFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) {
            requestContext.abortWith(Response.status(404)
                    .entity("{\"code\":\"NOT_FOUND\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build());
        }
    }

    static class CountingFilter implements ClientRequestFilter {

        final AtomicInteger requests = new AtomicInteger();

        @Override
        public void filter(ClientRequestContext requestContext) {
            requests.incrementAndGet();
        }
    }

    public static class EchoDto {
        public String uri;
        public Map<String, List<String>> headers;
    }

    interface ItemResource extends ConfigurationResource<ItemResource> {

        @GET
        @Path("item")
        EchoDto getItem();

        @GET
        @Path("item")
        AssuredResponse getItemResponse();

        @POST
        @Path("item")
        @Consumes(MediaType.APPLICATION_JSON)
        EchoDto createItem(EchoDto item);

        @DELETE
        @Path("item")
        void deleteItem();

        @GET
        @Path("count")
        int count();

        @GET
        @Path("item")
        CompletionStage<String> getItemAsync();

        @Path("sub")
        ItemResource sub();
    }
}
