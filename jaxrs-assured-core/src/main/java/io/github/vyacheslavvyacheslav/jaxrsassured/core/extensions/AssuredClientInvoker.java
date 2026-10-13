package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.RxInvoker;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocationBuilder;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.ClientContext;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.EntityExtractor;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.extractors.AssuredEntityExtractor;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.extractors.AssuredResponseExtractorFactory;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutorService;

public class AssuredClientInvoker extends ClientInvoker implements ExtendedClientInvoker {

    private static final AssuredEntityExtractor ASSURED_EXTRACTOR = new AssuredEntityExtractor();

    public AssuredClientInvoker(ResteasyWebTarget parent, Class<?> declaring, Method method, ProxyConfig config) {
        super(parent, declaring, method, config);
        entityExtractorFactory = new AssuredResponseExtractorFactory();
        this.extractor = entityExtractorFactory.createExtractor(method);
    }

    /**
     * Все пути отправки (RestEasy и наши sendSync/sendAsync) создают запрос здесь: отметка времени —
     * начало отсчета времени ответа в AssuredResponse.
     */
    @Override
    protected ClientInvocation createRequest(Object[] args) {
        ClientInvocation request = super.createRequest(args);
        request.property(AssuredEntityExtractor.REQUEST_STARTED_NANOS, System.nanoTime());
        return request;
    }

    /**
     * Настройка кладется в свойства запроса, применяет ее RequestConfigurationFilter —
     * уже после остальных фильтров, чтобы перекрывать и выставленные ими заголовки.
     * Родительские invokeSync/invokeAsync создают запрос сами, поэтому для запроса с настройкой
     * отправка повторена здесь — копия ClientInvoker из RestEasy 6.2.12, сверять при обновлении.
     */
    @Override
    public Object invoke(Object[] args, RequestConfiguration configuration) {
        if (configuration.isEmpty()) {
            return invoke(args);
        }
        ClientInvocation request = createRequest(args);
        request.property(RequestConfiguration.PROPERTY, configuration);
        return rxInvokerProvider != null ? sendAsync(request) : sendSync(request, extractor);
    }

    /**
     * Для assured(...): выполняет запрос и возвращает ответ для проверок, какой бы тип ни объявлял метод.
     * DTO не разбирается, поэтому на 4xx/5xx исключений нет.
     */
    public AssuredResponse invokeAssured(Object[] args, RequestConfiguration configuration) {
        if (rxInvokerProvider != null) {
            throw new UnsupportedOperationException("assured(...) не поддерживает реактивные методы: " + method.getName());
        }
        ClientInvocation request = createRequest(args);
        if (!configuration.isEmpty()) {
            request.property(RequestConfiguration.PROPERTY, configuration);
        }
        return (AssuredResponse) sendSync(request, ASSURED_EXTRACTOR);
    }

    private Object sendSync(ClientInvocation request, EntityExtractor<?> responseExtractor) {
        ClientResponse response = (ClientResponse) request.invoke();
        ClientContext context = new ClientContext(request, response, entityExtractorFactory);
        return responseExtractor.extractEntity(context);
    }

    /**
     * Реактивный метод (например, CompletionStage): RestEasy копирует запрос вместе со свойствами,
     * поэтому настройка доходит до RequestConfigurationFilter и здесь.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object sendAsync(ClientInvocation request) {
        WebTarget t = request.getActualTarget();
        ClientInvocationBuilder builder = (ClientInvocationBuilder) (t != null ? t : webTarget).request();
        builder.setClientInvocation(request);
        ExecutorService executor = webTarget.getResteasyClient().getScheduledExecutor();
        if (executor == null) {
            executor = request.asyncInvocationExecutor();
        }
        RxInvoker<?> rxInvoker = (RxInvoker<?>) rxInvokerProvider.getRxInvoker(builder, executor);
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType) {
            type = ((ParameterizedType) type).getActualTypeArguments()[0];
        }
        GenericType<?> gt = new GenericType(type);
        Object e = request.getEntity();
        if (e != null) {
            return rxInvoker.method(getHttpMethod(),
                    Entity.entity(e, request.getHeaders().getMediaType(), request.getEntityAnnotations()), gt);
        }
        return rxInvoker.method(getHttpMethod(), gt);
    }
}
