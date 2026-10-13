package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Печать DTO в java-файлы JavaPoet'ом. Общее для обоих стилей — класс, Jackson-аннотации
 * (@JsonProperty для snake_case имён), приватные поля с javadoc, энумы; аксессоры, toString/equals/hashCode
 * и билдер дописывает {@link DtoStyle}: обычная Java или Lombok. Энумы хранят исходное значение
 * и умеют round-trip (@JsonValue/@JsonCreator) — константы нормализуются в UPPER_SNAKE.
 */
final class DtoWriter {

    private final DtoStyle style;

    DtoWriter(DtoStyle style) {
        this.style = style;
    }

    void write(List<DtoModel> models, Path outDir) throws IOException {
        for (DtoModel model : models) {
            javaFile(model).writeTo(outDir);
        }
    }

    /** Исходник модели строкой — для тестов. */
    String render(DtoModel model) {
        return javaFile(model).toString();
    }

    private JavaFile javaFile(DtoModel model) {
        return JavaFile.builder(model.packageName(), typeSpec(model))
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private TypeSpec typeSpec(DtoModel model) {
        if (model.isEnum()) {
            return enumSpec(model.enumSelf(), classJavadoc(model.description()));
        }
        TypeSpec.Builder builder = TypeSpec.classBuilder(model.className())
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(classJavadoc(model.description()))
                .addAnnotation(AnnotationSpec.builder(JsonInclude.class)
                        .addMember("value", "$T.NON_NULL", JsonInclude.Include.class).build())
                .addAnnotation(AnnotationSpec.builder(JsonIgnoreProperties.class)
                        .addMember("ignoreUnknown", "$L", true).build());
        for (String typeVariable : model.typeVariables()) {
            builder.addTypeVariable(TypeVariableName.get(typeVariable));
        }
        for (FieldModel field : model.fields()) {
            builder.addField(fieldSpec(field));
        }
        style.complete(builder, model);
        for (EnumModel nested : model.nestedEnums()) {
            builder.addType(enumSpec(nested, null));
        }
        return builder.build();
    }

    /** Javadoc типа верхнего уровня: описание из спеки + пометка codegen (по ней Generator узнаёт свои файлы). */
    private CodeBlock classJavadoc(String description) {
        StringBuilder javadoc = new StringBuilder();
        if (description != null && !description.isBlank()) {
            javadoc.append(sanitize(description)).append('\n');
        }
        javadoc.append(Generator.GENERATED_MARKER).append('\n');
        return CodeBlock.of("$L", javadoc.toString());
    }

    private FieldSpec fieldSpec(FieldModel field) {
        FieldSpec.Builder builder = FieldSpec.builder(field.type(), field.varName(), Modifier.PRIVATE);
        String javadoc = (field.description() == null || field.description().isBlank() ? "" : sanitize(field.description()) + " ")
                + (field.required() ? "(обязательно)" : "(опционально)");
        builder.addJavadoc("$L\n", javadoc);
        if (!field.jsonName().equals(field.varName())) {
            builder.addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                    .addMember("value", "$S", field.jsonName()).build());
        }
        return builder.build();
    }

    /**
     * Энум с исходным значением: OK("ok"), round-trip через @JsonValue/@JsonCreator; toString() — тоже значение:
     * через него JAX-RS-клиент кладёт энум-параметр в URL (иначе ушло бы имя константы E_BOOK вместо e_book).
     * javadoc null — вложенный.
     */
    private TypeSpec enumSpec(EnumModel enumModel, CodeBlock javadoc) {
        ClassName enumType = ClassName.get("", enumModel.className());
        TypeSpec.Builder builder = TypeSpec.enumBuilder(enumModel.className())
                .addModifiers(Modifier.PUBLIC);
        if (javadoc != null) {
            builder.addJavadoc(javadoc);
        }
        for (EnumModel.Constant constant : enumModel.constants()) {
            builder.addEnumConstant(constant.name(),
                    TypeSpec.anonymousClassBuilder("$S", constant.value()).build());
        }
        builder.addField(FieldSpec.builder(String.class, "value", Modifier.PRIVATE, Modifier.FINAL).build());
        builder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(String.class, "value")
                .addStatement("this.value = value")
                .build());
        builder.addMethod(MethodSpec.methodBuilder("getValue")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(JsonValue.class)
                .returns(String.class)
                .addStatement("return value")
                .build());
        builder.addMethod(MethodSpec.methodBuilder("fromValue")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(JsonCreator.class)
                .returns(enumType)
                .addParameter(ParameterSpec.builder(String.class, "value").build())
                .addStatement("for ($T constant : values()) {\n    if (constant.value.equals(value)) {\n"
                        + "        return constant;\n    }\n}", enumType)
                .addStatement("throw new $T($S + value)", IllegalArgumentException.class,
                        "Неизвестное значение " + enumModel.className() + ": ")
                .build());
        builder.addMethod(MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return value")
                .build());
        return builder.build();
    }

    /** Длина описаний из спеки произвольная: гасим завершение блока javadoc. */
    private String sanitize(String text) {
        return text.replace("*/", "* /");
    }
}
