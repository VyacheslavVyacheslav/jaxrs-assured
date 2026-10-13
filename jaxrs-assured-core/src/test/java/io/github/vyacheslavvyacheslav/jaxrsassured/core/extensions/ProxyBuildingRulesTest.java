package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.DefaultClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ProxyBuilder;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Один правильный способ построить ресурс — AssuredClientWebTarget.proxy(...), любой другой дает понятную ошибку:
 * proxy с фабрикой и без ведут себя одинаково, тип тела берется только из @Consumes,
 * «чужой» target и proxyBuilder(...) RestEasy отклоняются, простой прокси объясняет, почему нет setHeader.
 * Запросы в сеть не уходят — отвечает EchoHeadersFilter.
 */
class ProxyBuildingRulesTest {

    private static final String BASE_URI = "http://localhost";

    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void proxyWithoutFactoryShouldSupportConfiguration() {
        EchoResource resource = echoTarget().proxy(EchoResource.class);

        AssuredResponse response = resource.setHeader("X-Custom", "configured").get();

        assertEquals(List.of("configured"), header(response, "X-Custom"));
    }

    @Test
    void bodyMethodWithoutConsumesShouldFailOnBothProxyMethods() {
        AssuredClientWebTarget target = echoTarget();

        RuntimeException withoutFactory = assertThrows(RuntimeException.class, () -> target.proxy(NoConsumesResource.class));
        RuntimeException withFactory = assertThrows(RuntimeException.class,
                () -> target.proxy(NoConsumesResource.class, new ExtendedClientProxyFactory()));

        assertTrue(withoutFactory.getMessage().contains("@Consumes"), withoutFactory::getMessage);
        assertTrue(withFactory.getMessage().contains("@Consumes"), withFactory::getMessage);
    }

    @Test
    void contentTypeShouldComeFromConsumes() {
        AssuredResponse response = echoTarget().proxy(TextResource.class).post("text body");

        assertEquals(List.of("text/plain"), header(response, "Content-Type"));
    }

    @Test
    void resteasyProxyBuilderShouldPointToProxy() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                () -> echoTarget().proxyBuilder(EchoResource.class));

        assertTrue(failure.getMessage().contains("proxy(...)"), failure::getMessage);
    }

    @Test
    void foreignTargetShouldFailWhenProxyIsBuilt() {
        try (ResteasyClient foreign = (ResteasyClient) ClientBuilder.newClient()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ProxyBuilder.builder(EchoResource.class, foreign.target(BASE_URI)).build());

            assertTrue(failure.getMessage().contains("HttpClientUtil.restEasyClient()"), failure::getMessage);
        }
    }

    @Test
    void assuredTargetShouldApplyConfigurationEvenIfClientHasNoFilter() {
        try (ResteasyClient foreign = (ResteasyClient) ClientBuilder.newClient()) {
            AssuredClientWebTarget target = new AssuredClientWebTarget(foreign, BASE_URI,
                    (ClientConfiguration) foreign.getConfiguration());
            target.register(new EchoHeadersFilter());

            AssuredResponse response = target.proxy(EchoResource.class).setHeader("X-Custom", "configured").get();

            assertEquals(List.of("configured"), header(response, "X-Custom"));
        }
    }

    @Test
    void defaultProxyShouldExplainWhyConfigurationIsUnavailable() {
        EchoResource plain = echoTarget().proxy(EchoResource.class, new DefaultClientProxyFactory());

        UnsupportedOperationException setHeader = assertThrows(UnsupportedOperationException.class,
                () -> plain.setHeader("X-Custom", "value"));
        UnsupportedOperationException assured = assertThrows(UnsupportedOperationException.class,
                () -> plain.assured(r -> r.get()));

        assertTrue(setHeader.getMessage().contains("DefaultClientProxyFactory"), setHeader::getMessage);
        assertTrue(assured.getMessage().contains("DefaultClientProxyFactory"), assured::getMessage);
    }

    private AssuredClientWebTarget echoTarget() {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        target.register(new EchoHeadersFilter());
        return target;
    }

    private static List<String> header(AssuredResponse response, String name) {
        return EchoHeadersFilter.header(response.extract().asString(), name);
    }

    interface EchoResource extends ConfigurationResource<EchoResource> {

        @GET
        @Path("echo")
        <T extends ValidatableResponse> T get();
    }

    interface NoConsumesResource {

        @POST
        @Path("echo")
        <T extends ValidatableResponse> T post(String body);
    }

    @Consumes(MediaType.TEXT_PLAIN)
    interface TextResource {

        @POST
        @Path("echo")
        <T extends ValidatableResponse> T post(String body);
    }
}
