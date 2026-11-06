package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Энум: имя типа и константы. Вложенные энумы живут внутри POJO (встроенный enum свойства) и интерфейсов ресурсов
 * (встроенный enum параметра), чисто-энумные схемы components — отдельными классами пакета model.
 */
public record EnumModel(String className, List<Constant> constants) {

    public EnumModel {
        constants = List.copyOf(constants);
    }

    public record Constant(String name, String value) {
    }

    /**
     * Значения энума → константы; коллизии после нормализации получают суффикс _2, _3...
     * Пропуски и коллизии пишутся в warnings, context — где энум (для сообщения).
     */
    static List<Constant> constantsOf(List<?> values, String context, List<String> warnings) {
        List<Constant> constants = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (Object value : values) {
            if (value == null) {
                warnings.add("Энум " + context + " содержит null — константа пропущена");
                continue;
            }
            String name = Naming.enumConstant(value.toString());
            if (!Naming.isUsableIdentifier(name)) {
                name = "_" + name;
            }
            String unique = name;
            int suffix = 2;
            while (!taken.add(unique)) {
                unique = name + "_" + suffix++;
            }
            if (!unique.equals(name)) {
                warnings.add("Константа энума " + context + " дублируется после нормализации: "
                        + value + " → " + unique);
            }
            constants.add(new Constant(unique, String.valueOf(value)));
        }
        return constants;
    }
}
