package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ResteasyClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.filters.BearerAuthFilter;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.DefaultClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Офлайн-проверка ConfigurationResource и AssuredResponse: запрос не уходит в сеть —
 * последний фильтр цепочки отвечает эхом итоговых заголовков через abortWith (как httpbin.org/headers).
 * Ответ из abortWith заодно проверяет, что AssuredResponse не зависит от HTTP-движка.
 */
class ConfigurationResourceTest {

    private static final String BASE_URI = "http://localhost";

    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void setHeaderShouldChangeThisResourceForFollowingCalls() {
        EchoResource resource = echo();

        EchoResource returned = resource.setHeader("X-Custom", "configured");

        assertSame(resource, returned);
        assertEquals(List.of("configured"), header(resource.get(null), "X-Custom"));
        assertEquals(List.of("configured"), header(resource.get(null), "X-Custom"));
    }

    @Test
    void deleteHeaderShouldChangeThisResourceForFollowingCalls() {
        EchoResource resource = echo(new BearerAuthFilter("token"));

        resource.deleteHeader(HttpHeaders.AUTHORIZATION);

        assertEquals(List.of(), header(resource.get(null), HttpHeaders.AUTHORIZATION));
        assertEquals(List.of(), header(resource.get(null), HttpHeaders.AUTHORIZATION));
    }

    /**
     * Сценарий из обсуждения: настройка живет на ресурсе до сброса, новый ресурс от того же родителя ее не видит.
     */
    @Test
    void configurationShouldStayOnResourceUntilReset() {
        EchoResource parent = echo();
        EchoSubResource configured = parent.sub().setHeader("X-Test", "test");

        assertEquals(List.of("test"), header(configured.get(), "X-Test"));
        assertEquals(List.of("test"), header(configured.get(), "X-Test"));

        EchoSubResource sibling = parent.sub();
        assertEquals(List.of(), header(sibling.get(), "X-Test"));
        assertEquals(List.of("test"), header(configured.get(), "X-Test"));

        configured.reset();
        assertEquals(List.of(), header(configured.get(), "X-Test"));
    }

    @Test
    void resetShouldRemoveAllConfigurationIncludingInherited() {
        EchoResource parent = echo(new BearerAuthFilter("token"));
        parent.setHeader("X-Parent", "parent");
        EchoSubResource child = parent.sub();
        child.setHeader("X-Child", "child").deleteHeader(HttpHeaders.AUTHORIZATION);

        child.reset();

        AssuredResponse response = child.get();
        assertEquals(List.of(), header(response, "X-Parent"));
        assertEquals(List.of(), header(response, "X-Child"));
        assertEquals(List.of("Bearer token"), header(response, HttpHeaders.AUTHORIZATION));
        assertEquals(List.of("parent"), header(parent.get(null), "X-Parent"));
    }

    @Test
    void setHeaderShouldReplaceHeaderAddedByFilter() {
        AssuredResponse response = echo(new BearerAuthFilter("token"))
                .setHeader(HttpHeaders.AUTHORIZATION, "Bearer configured")
                .get(null);

        assertEquals(List.of("Bearer configured"), header(response, HttpHeaders.AUTHORIZATION));
    }

    @Test
    void setHeaderShouldOverrideHeaderParamValue() {
        AssuredResponse response = echo().setHeader("X-From-Param", "configured").get("from-param");

        assertEquals(List.of("configured"), header(response, "X-From-Param"));
    }

    @Test
    void setHeaderShouldKeepValuesInDeclaredOrder() {
        AssuredResponse response = echo().setHeader("X-Multi", "first", "second", "third").get(null);

        assertEquals(List.of("first", "second", "third"), header(response, "X-Multi"));
    }

    @Test
    void repeatedConfigurationShouldReplacePreviousRegardlessOfCase() {
        EchoResource resource = echo();
        resource.setHeader("X-Custom", "old");
        resource.setHeader("x-custom", "new");

        assertEquals(List.of("new"), header(resource.get(null), "X-Custom"));
        assertEquals(List.of(), header(resource.deleteHeader("X-CUSTOM").get(null), "X-Custom"));
    }

    @Test
    void subResourceShouldTakeParentConfigurationAtTheMomentOfCreation() {
        EchoResource parent = echo();
        EchoSubResource createdBefore = parent.sub();
        parent.setHeader("X-Custom", "parent");
        EchoSubResource createdAfter = parent.sub();

        parent.setHeader("X-Custom", "changed later");

        assertEquals(List.of("parent"), header(createdAfter.get(), "X-Custom"));
        assertEquals(List.of(), header(createdBefore.get(), "X-Custom"));
    }

    @Test
    void subResourceConfigurationShouldNotAffectParent() {
        EchoResource parent = echo();

        EchoSubResource sub = parent.sub();
        sub.setHeader("X-Custom", "sub");

        assertEquals(List.of("sub"), header(sub.get(), "X-Custom"));
        assertEquals(List.of(), header(parent.sub().get(), "X-Custom"));
        assertEquals(List.of(), header(parent.get(null), "X-Custom"));
    }

    @Test
    void configurationShouldApplyToReactiveMethods() throws Exception {
        EchoResource resource = echo(new BearerAuthFilter("token"));
        resource.setHeader("X-Custom", "configured").deleteHeader(HttpHeaders.AUTHORIZATION);

        String body = resource.getAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(List.of("configured"), headerFromBody(body, "X-Custom"));
        assertEquals(List.of(), headerFromBody(body, HttpHeaders.AUTHORIZATION));
    }

    @Test
    void reactiveMethodsShouldWorkWithoutConfiguration() throws Exception {
        String body = echo(new BearerAuthFilter("token")).getAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(List.of("Bearer token"), headerFromBody(body, HttpHeaders.AUTHORIZATION));
    }

    @Test
    void toStringShouldShowConfiguration() {
        EchoResource resource = echo();

        resource.setHeader("X-Actor", "qa").deleteHeader(HttpHeaders.AUTHORIZATION);
        assertEquals("EchoResource [X-Actor=qa, -Authorization]", resource.toString());

        resource.reset();
        assertEquals("EchoResource []", resource.toString());
    }

    @Test
    void asShouldBuildAssuredProxyWithSameConfiguration() {
        EchoResource configured = echo().setHeader("X-Custom", "configured");

        AssuredResponse response = ((ResteasyClientProxy) configured).as(EchoAsResource.class).get();

        assertEquals(List.of("configured"), header(response, "X-Custom"));
    }

    @Test
    void asOnDefaultProxyShouldBuildAssuredProxy() {
        EchoResource plain = echo(new DefaultClientProxyFactory());

        AssuredResponse response = ((ResteasyClientProxy) plain).as(EchoAsResource.class).get();

        assertEquals(200, response.extract().statusCode());
    }

    @Test
    void noContentResponseShouldBeExtractedWithEmptyBody() {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        target.register(new NoContentFilter());

        AssuredResponse response = target.proxy(EchoAsResource.class, new ExtendedClientProxyFactory()).get();

        assertEquals(204, response.extract().statusCode());
        assertEquals(0, response.extract().asByteArray().length);
    }

    private EchoResource echo(ClientRequestFilter... filters) {
        return echo(new ExtendedClientProxyFactory(), filters);
    }

    private EchoResource echo(ClientProxyFactory proxyFactory, ClientRequestFilter... filters) {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        for (ClientRequestFilter filter : filters) {
            target.register(filter);
        }
        target.register(new EchoHeadersFilter());
        return target.proxy(EchoResource.class, proxyFactory);
    }

    private static List<String> header(AssuredResponse response, String name) {
        assertEquals(200, response.extract().statusCode(), () -> response.extract().asString());
        return EchoHeadersFilter.header(response.extract().asString(), name);
    }

    private static List<String> headerFromBody(String echoBody, String name) {
        return EchoHeadersFilter.header(echoBody, name);
    }

    static class NoContentFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) {
            requestContext.abortWith(Response.noContent().build());
        }
    }

    interface EchoResource extends ConfigurationResource<EchoResource> {

        @GET
        @Path("echo")
        <T extends ValidatableResponse> T get(@HeaderParam("X-From-Param") String value);

        @GET
        @Path("echo")
        CompletionStage<String> getAsync();

        @Path("sub")
        EchoSubResource sub();
    }

    interface EchoSubResource extends ConfigurationResource<EchoSubResource> {

        @GET
        <T extends ValidatableResponse> T get();
    }

    interface EchoAsResource {

        @GET
        @Path("echo")
        <T extends ValidatableResponse> T get();
    }
}
