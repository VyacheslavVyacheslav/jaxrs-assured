package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.TypeName;

/**
 * Параметр сгенерированного метода: где живёт (путь/запрос/заголовок/тело) и тип.
 */
public record ParamModel(In in, String paramName, String varName, TypeName typeName) {

    /** BEAN — бин @BeanParam, в который ушли query-параметры ручки (typeName — вложенный класс интерфейса). */
    public enum In { PATH, QUERY, HEADER, BEAN, BODY }
}
