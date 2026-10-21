package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.List;

/**
 * Ресурсный интерфейс к генерации. classLevelPath задан только у корня (rootPath),
 * у подресурсов путь приносят локаторы родителей. enums — вложенные энумы встроенных enum параметров;
 * beanParams — вложенные бины @BeanParam: в них уходят query-параметры ручек, у которых их больше порога
 * (поле FieldModel: jsonName — имя query-параметра).
 */
public record ResourceModel(String packageName, String className, String classLevelPath,
                            List<OperationModel> operations, List<LocatorModel> locators, List<EnumModel> enums,
                            List<DtoModel> beanParams) {

    public ResourceModel {
        operations = List.copyOf(operations);
        locators = List.copyOf(locators);
        enums = List.copyOf(enums);
        beanParams = List.copyOf(beanParams);
    }
}
