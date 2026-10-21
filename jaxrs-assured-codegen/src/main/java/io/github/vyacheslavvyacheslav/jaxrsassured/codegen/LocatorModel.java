package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.TypeName;

/**
 * Локатор подресурса: метод родительского интерфейса, ведущий на дочерний.
 * Для сегмента-параметра ({id}) заданы pathParam и pathParamType — тип @PathParam на параметре локатора;
 * для статического сегмента оба null.
 */
public record LocatorModel(String methodName, String path, String pathParam, TypeName pathParamType,
                           String childPackage, String childClass) {
}
