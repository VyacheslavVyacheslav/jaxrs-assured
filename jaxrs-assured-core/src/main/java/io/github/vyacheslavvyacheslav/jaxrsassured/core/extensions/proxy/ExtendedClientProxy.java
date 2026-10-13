package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.AssuredCall;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.ConfigurableRequest;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.ExtendedClientInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.DeleteCookieParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.DeleteHeaderParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.DeleteQueryParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestSetting;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.SetBody;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.SetCookieParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.SetHeaderParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.SetQueryParam;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Прокси с поддержкой ConfigurationResource. Хранит ссылку на неизменяемую настройку:
 * set.../delete.../reset подменяют ссылку в этом прокси и возвращают его же.
 * Инвокеры общие для всех прокси одного ресурса и не меняются: каждый вызов получает снимок настройки.
 */
public class ExtendedClientProxy extends ClientProxy {

    private final AtomicReference<RequestConfiguration> requestConfiguration;

    public ExtendedClientProxy(Map<Method, MethodInvoker> methodMap, WebTarget target, ProxyContext context,
                               RequestConfiguration requestConfiguration) {
        super(methodMap, target, context);
        this.requestConfiguration = new AtomicReference<>(requestConfiguration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodInvoker clientInvoker = methodMap.get(method);
        if (clientInvoker instanceof ExtendedClientInvoker extendedInvoker) {
            return extendedInvoker.invoke(args, requestConfiguration.get());
        }
        if (clientInvoker == null) {
            if (method.isAnnotationPresent(AssuredCall.class)) {
                return assured(proxy, (Consumer<Object>) args[0]);
            }
            ConfigurableRequest configurable = method.getAnnotation(ConfigurableRequest.class);
            if (configurable != null) {
                switch (configurable.value()) {
                    case HEADER -> addSetting(new SetHeaderParam((String) args[0], values(args)));
                    case DELETE_HEADER -> addSetting(new DeleteHeaderParam((String) args[0]));
                    case COOKIE -> addSetting(new SetCookieParam((String) args[0], (String) args[1]));
                    case DELETE_COOKIE -> addSetting(new DeleteCookieParam((String) args[0]));
                    case QUERY_PARAM -> addSetting(new SetQueryParam((String) args[0], values(args)));
                    case DELETE_QUERY_PARAM -> addSetting(new DeleteQueryParam((String) args[0]));
                    case BODY -> addSetting(new SetBody((String) args[0]));
                    case RESET -> requestConfiguration.set(RequestConfiguration.EMPTY);
                }
                return proxy;
            }
            if (method.getName().equals("as") && args != null && args.length == 1 && args[0] instanceof Class) {
                return ProxyBuilder.proxy((Class<?>) args[0], target, context, requestConfiguration.get());
            }
        }
        return super.invoke(proxy, method, args);
    }

    @Override
    public String toString() {
        return clazz.getSimpleName() + " " + requestConfiguration.get();
    }

    protected RequestConfiguration currentConfiguration() {
        return requestConfiguration.get();
    }

    /**
     * Лямбда получает отдельный прокси того же ресурса (AssuredCallProxy) со снимком настройки этого ресурса:
     * он выполняет HTTP-вызов и запоминает ответ, а настройка, измененная внутри лямбды, сюда не возвращается.
     */
    private AssuredResponse assured(Object proxy, Consumer<Object> call) {
        Objects.requireNonNull(call, "assured(...) call can not be null");
        AssuredCallProxy handler = new AssuredCallProxy(methodMap, target, context, requestConfiguration.get());
        handler.setClazz(clazz);
        call.accept(Proxy.newProxyInstance(proxy.getClass().getClassLoader(), proxy.getClass().getInterfaces(), handler));
        return handler.response();
    }

    private void addSetting(RequestSetting setting) {
        requestConfiguration.updateAndGet(current -> current.with(setting));
    }

    /**
     * Значения в порядке объявления: setHeader/setQueryParam(name, value, additionalValue...).
     */
    private static List<Object> values(Object[] args) {
        List<Object> values = new ArrayList<>();
        values.add(Objects.requireNonNull(args[1], "Value can not be null"));
        if (args[2] != null) {
            values.addAll(Arrays.asList((Object[]) args[2]));
        }
        return values;
    }
}
