package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.List;

/**
 * Ресурсный интерфейс к генерации. classLevelPath задан только у корня (rootPath),
 * у подресурсов путь приносят локаторы родителей.
 */
public record ResourceModel(String packageName, String className, String classLevelPath,
                            List<OperationModel> operations, List<LocatorModel> locators) {

    public ResourceModel {
        operations = List.copyOf(operations);
        locators = List.copyOf(locators);
    }
}
