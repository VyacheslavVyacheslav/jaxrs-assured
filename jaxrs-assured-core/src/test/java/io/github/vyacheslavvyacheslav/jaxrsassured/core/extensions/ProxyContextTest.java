package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ResteasyClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxy;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ClientProxyInvocationHandler;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.DefaultClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ExtendedClientProxyFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ProxyBuilder;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ProxyContext;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.ResourceInvokerFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy.SubResourceInvokerFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.util.HttpClientUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Фабрики, переданные в корень, строят всё дерево прокси: подресурсы на любой глубине и as(...).
 * Заодно проверяется, что путь, настройки RestEasy (ProxyConfig) и настройка заголовков по-прежнему
 * доходят до вложенных подресурсов. Запросы в сеть не уходят — отвечает EchoHeadersFilter.
 */
class ProxyContextTest {

    private static final String BASE_URI = "http://localhost";

    private final ResteasyClient client = HttpClientUtil.restEasyClient();

    @AfterEach
    void closeClient() {
        client.close();
    }

    /**
     * Сценарий Allure-шагов: обертка над обработчиком видит каждый вызов, включая реальные HTTP-вызовы на подресурсах.
     */
    @Test
    void customClientProxyFactoryShouldSeeCallsOnAllLevels() {
        RecordingProxyFactory factory = new RecordingProxyFactory();
        RootResource root = echoTarget().proxy(RootResource.class, factory);

        root.setHeader("X-Custom", "configured");
        AssuredResponse response = root.level1().level2("42").get();

        assertEquals(List.of("ConfigurationResource.setHeader", "RootResource.level1",
                "Level1Resource.level2", "Level2Resource.get"), factory.calls);
        assertEquals(List.of("configured"), EchoHeadersFilter.header(response.extract().asString(), "X-Custom"));
    }

    @Test
    void customClientProxyFactoryShouldBuildAsProxy() {
        RecordingProxyFactory factory = new RecordingProxyFactory();
        RootResource root = echoTarget().proxy(RootResource.class, factory);

        ((ResteasyClientProxy) root).as(AsResource.class).get();

        assertEquals(List.of("ResteasyClientProxy.as", "AsResource.get"), factory.calls);
    }

    @Test
    void customResourceInvokerFactoryShouldBeUsedOnNestedLevels() {
        RecordingInvokerFactory invokerFactory = new RecordingInvokerFactory();

        RootResource root = ProxyBuilder.builder(RootResource.class, echoTarget(),
                new ExtendedClientProxyFactory(), invokerFactory).build();
        assertEquals(List.of("level1"), invokerFactory.locators);

        root.level1();
        assertEquals(List.of("level1", "level2"), invokerFactory.locators);
    }

    @Test
    void defaultClientProxyFactoryShouldBuildWholeTreeConsistently() {
        RootResource root = echoTarget().proxy(RootResource.class, new DefaultClientProxyFactory());

        Level1Resource level1 = root.level1();
        Level2Resource level2 = level1.level2("42");

        assertEquals(ClientProxy.class, Proxy.getInvocationHandler(root).getClass());
        assertEquals(ClientProxy.class, Proxy.getInvocationHandler(level1).getClass());
        assertEquals(ClientProxy.class, Proxy.getInvocationHandler(level2).getClass());
    }

    @Test
    void pathAndProxyConfigShouldReachNestedLevels() {
        RootResource root = ProxyBuilder.builder(RootResource.class, echoTarget(), new ExtendedClientProxyFactory())
                .defaultProduces(MediaType.TEXT_PLAIN_TYPE)
                .build();

        AssuredResponse response = root.level1().level2("42").get();

        String body = response.extract().asString();
        assertEquals(BASE_URI + "/root/level1/level2/42", EchoHeadersFilter.uri(body));
        // Accept по умолчанию задан только на корне и приходит на второй уровень через ProxyConfig
        assertEquals(List.of("text/plain"), EchoHeadersFilter.header(body, "Accept"));
    }

    @Test
    void requestConfigurationShouldReachNestedLevels() {
        RootResource root = echoTarget().proxy(RootResource.class, new ExtendedClientProxyFactory());
        root.setHeader("X-Root", "root");
        Level1Resource level1 = root.level1().setHeader("X-Level1", "level1");

        String body = level1.level2("42").get().extract().asString();

        assertEquals(List.of("root"), EchoHeadersFilter.header(body, "X-Root"));
        assertEquals(List.of("level1"), EchoHeadersFilter.header(body, "X-Level1"));
    }

    private AssuredClientWebTarget echoTarget() {
        AssuredClientWebTarget target = (AssuredClientWebTarget) client.target(BASE_URI);
        target.register(new EchoHeadersFilter());
        return target;
    }

    /**
     * Как будущая фабрика Allure-шагов: оборачивает стандартный обработчик и записывает каждый вызов.
     */
    static class RecordingProxyFactory implements ClientProxyFactory {

        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ClientProxyInvocationHandler createClientInvoker(Map<Method, MethodInvoker> methodMap, WebTarget target,
                                                                ProxyContext context, RequestConfiguration requestConfiguration) {
            ClientProxyInvocationHandler delegate = new ExtendedClientProxyFactory()
                    .createClientInvoker(methodMap, target, context, requestConfiguration);
            return new ClientProxyInvocationHandler() {
                @Override
                public Class<?> getClazz() {
                    return delegate.getClazz();
                }

                @Override
                public void setClazz(Class<?> clazz) {
                    delegate.setClazz(clazz);
                }

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    calls.add(method.getDeclaringClass().getSimpleName() + "." + method.getName());
                    return delegate.invoke(proxy, method, args);
                }
            };
        }
    }

    static class RecordingInvokerFactory implements ResourceInvokerFactory {

        final List<String> locators = Collections.synchronizedList(new ArrayList<>());

        @Override
        public MethodInvoker createSubInvoker(ResteasyWebTarget parent, Method method, ProxyContext context) {
            locators.add(method.getName());
            return new SubResourceInvokerFactory().createSubInvoker(parent, method, context);
        }
    }

    @Path("root")
    interface RootResource extends ConfigurationResource<RootResource> {

        @Path("level1")
        Level1Resource level1();
    }

    interface Level1Resource extends ConfigurationResource<Level1Resource> {

        @Path("level2/{id}")
        Level2Resource level2(@PathParam("id") String id);
    }

    interface Level2Resource extends ConfigurationResource<Level2Resource> {

        @GET
        <T extends ValidatableResponse> T get();
    }

    interface AsResource {

        @GET
        @Path("echo")
        <T extends ValidatableResponse> T get();
    }
}
