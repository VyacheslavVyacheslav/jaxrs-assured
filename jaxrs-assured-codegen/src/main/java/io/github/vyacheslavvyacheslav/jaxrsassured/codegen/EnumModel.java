package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.List;

/**
 * Энум: имя типа и константы. Вложенные энумы живут внутри POJO,
 * чисто-энумные схемы components — отдельными классами пакета model.
 */
public record EnumModel(String className, List<Constant> constants) {

    public EnumModel {
        constants = List.copyOf(constants);
    }

    public record Constant(String name, String value) {
    }
}
