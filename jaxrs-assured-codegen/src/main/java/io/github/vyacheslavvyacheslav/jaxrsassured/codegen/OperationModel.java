package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.TypeName;

import java.util.List;

/**
 * HTTP-ручка на интерфейсе: путь относительно базового пути интерфейса (пустой — сама ручка узла).
 * produces — media type ответа для @Produces, только если ответ не JSON (JSON — умолчание, без аннотации).
 */
public record OperationModel(String httpMethod, String path, String methodName,
                             List<ParamModel> params, String consumes, String produces, TypeName returnType) {
}
