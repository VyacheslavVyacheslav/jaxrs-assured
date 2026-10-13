package io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Метод ресурса, который выполняет HTTP-вызов из лямбды и возвращает AssuredResponse (см. AssuredResource).
 * Не является HTTP-эндпоинтом: прокси перехватывает его по этой аннотации.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AssuredCall {
}
