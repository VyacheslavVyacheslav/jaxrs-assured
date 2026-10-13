package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.TypeName;

/**
 * Поле DTO: имя в JSON, имя в java, тип, обязательность и описание из спеки.
 */
public record FieldModel(String jsonName, String varName, TypeName type, boolean required,
                         String description) {
}
