package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.proxy;

import jakarta.ws.rs.client.WebTarget;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.AssuredCall;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.AssuredClientInvoker;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters.RequestConfiguration;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Ресурс внутри assured(...): HTTP-вызов не разбирает DTO, а запоминает ответ как AssuredResponse.
 * Начинает со снимка настройки ресурса, поэтому set.../delete.../reset внутри лямбды
 * касаются только этого вызова. Живет в пределах одного assured(...).
 */
class AssuredCallProxy extends ExtendedClientProxy {

    private AssuredResponse response;
    private int httpCalls;

    AssuredCallProxy(Map<Method, MethodInvoker> methodMap, WebTarget target, ProxyContext context,
                     RequestConfiguration requestConfiguration) {
        super(methodMap, target, context, requestConfiguration);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodInvoker invoker = methodMap.get(method);
        if (invoker instanceof AssuredClientInvoker assuredInvoker) {
            if (++httpCalls > 1) {
                throw new IllegalStateException("В лямбде assured(...) должен быть один HTTP-вызов, лишний вызов: "
                        + method.getName());
            }
            response = assuredInvoker.invokeAssured(args, currentConfiguration());
            return defaultValue(method.getReturnType());
        }
        if (invoker instanceof SubResourceInvoker) {
            throw new UnsupportedOperationException("Переходы по подресурсам внутри assured(...) не поддерживаются: "
                    + "вызовите assured(...) у нужного подресурса, лишний переход: " + method.getName());
        }
        if (invoker != null) {
            throw new UnsupportedOperationException("assured(...) не поддерживает метод " + method.getName()
                    + ": прокси построен не через AssuredClientWebTarget");
        }
        if (method.isAnnotationPresent(AssuredCall.class)) {
            throw new IllegalStateException("assured(...) нельзя вызывать внутри assured(...)");
        }
        return super.invoke(proxy, method, args);
    }

    AssuredResponse response() {
        if (httpCalls == 0) {
            throw new IllegalStateException("В лямбде assured(...) не было HTTP-вызова ресурса");
        }
        return response;
    }

    /**
     * Что метод ресурса возвращает внутри лямбды вместо DTO: null, для примитивов — 0 или false.
     */
    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }
}
