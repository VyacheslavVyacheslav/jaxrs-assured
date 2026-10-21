package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Переопределение типа числового поля DTO — исправление спеки без правки спеки: {@code Схема.свойство=Тип}.
 * В обеих частях {@code *} — любые символы: {@code *.id=Long}, {@code PageDto.*=Integer}, {@code *.*Id=Long}.
 * Действует только на свойства с number или integer в спеке (у массива чисел — на элементы), остальные не трогает:
 * правило {@code *.id=Long} не превратит строковый id в Long.
 */
public record TypeOverrideRule(String rule, Pattern schema, Pattern property, TypeName type) {

    /** Допустимые типы: целые и дробные, без Number — в DTO с ним неудобно сравнивать и считать. */
    private static final Map<String, TypeName> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("Integer", TypeName.INT.box());
        TYPES.put("Long", TypeName.LONG.box());
        TYPES.put("Double", TypeName.DOUBLE.box());
        TYPES.put("BigDecimal", ClassName.get(BigDecimal.class));
    }

    /**
     * @throws IllegalArgumentException правило не в формате Схема.свойство=Тип или тип не из списка
     */
    public static TypeOverrideRule parse(String rule) {
        String text = rule == null ? "" : rule.trim();
        int equals = text.indexOf('=');
        String[] target = (equals < 0 ? "" : text.substring(0, equals)).split("\\.", -1);
        if (target.length != 2 || target[0].isEmpty() || target[1].isEmpty()) {
            throw new IllegalArgumentException("Ожидалось Схема.свойство=Тип (например *.id=Long): \"" + rule + "\"");
        }
        TypeName type = TYPES.get(text.substring(equals + 1));
        if (type == null) {
            throw new IllegalArgumentException("Тип в \"" + rule + "\" — один из " + String.join(", ", TYPES.keySet()));
        }
        return new TypeOverrideRule(text, glob(target[0]), glob(target[1]), type);
    }

    boolean matches(String schemaName, String propertyName) {
        return schema.matcher(schemaName).matches() && property.matcher(propertyName).matches();
    }

    /** {@code *} — любые символы, остальное буквально. */
    private static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    @Override
    public String toString() {
        return rule;
    }
}
