package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

/**
 * Локатор подресурса: метод родительского интерфейса, ведущий на дочерний.
 * Для сегмента-параметра ({id}) задан pathParam — он же @PathParam на параметре-строке.
 */
public record LocatorModel(String methodName, String path, String pathParam,
                           String childPackage, String childClass) {
}
