package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Какие методы ресурса считаются локаторами подресурса: без HTTP-метода, возвращают интерфейс,
 * путь задан на методе или на возвращаемом интерфейсе. Метод с HTTP-аннотацией — всегда HTTP-вызов.
 * Запросы в сеть не уходят — отвечает EchoHeadersFilter.
 */
class SubResourceLocatorTest {

    private static final String BASE_URI = "http://localhost";

    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void locatorWithPathOnMethodShouldBuildSubResource() {
        AssuredResponse response = root().byMethodPath().get();

        assertEquals(BASE_URI + "/method-path", EchoHeadersFilter.uri(response.extract().asString()));
    }

    @Test
    void locatorWithPathOnReturnedInterfaceShouldBuildSubResource() {
        AssuredResponse response = root().byInterfacePath().get();

        assertEquals(BASE_URI + "/interface-path", EchoHeadersFilter.uri(response.extract().asString()));
    }

    @Test
    void httpMethodShouldStayHttpCallEvenIfReturnTypeHasPath() {
        EchoBody body = root().getBody();

        assertEquals(BASE_URI + "/echo", body.uri);
    }

    private RootResource root() {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        target.register(new EchoHeadersFilter());
        return target.proxy(RootResource.class, new ExtendedClientProxyFactory());
    }

    interface RootResource {

        @Path("method-path")
        PlainSubResource byMethodPath();

        PathSubResource byInterfacePath();

        @GET
        @Path("echo")
        EchoBody getBody();
    }

    interface PlainSubResource {

        @GET
        <T extends ValidatableResponse> T get();
    }

    @Path("interface-path")
    interface PathSubResource {

        @GET
        <T extends ValidatableResponse> T get();
    }

    /**
     * DTO с @Path на классе: раньше из-за условия без скобок такой метод становился локатором.
     */
    @Path("not-a-locator")
    public static class EchoBody {
        public String uri;
        public Map<String, List<String>> headers;
    }
}
