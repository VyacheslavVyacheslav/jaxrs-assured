package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * DTO на обычной Java, без зависимостей: всё, что в Lombok-стиле дают аннотации, генератор пишет сам
 * и в той же форме — геттеры/сеттеры, {@code toString()} вида {@code X(a=1, b=null)}, equals/hashCode по полям,
 * {@code builder()} → вложенный {@code XBuilder} с методами-полями и {@code build()}, {@code toBuilder()}.
 */
final class PlainDtoStyle implements DtoStyle {

    @Override
    public void complete(TypeSpec.Builder type, DtoModel model) {
        for (FieldModel field : model.fields()) {
            type.addMethod(getter(field));
            type.addMethod(setter(field));
        }
        type.addMethod(equalsMethod(model));
        type.addMethod(hashCodeMethod(model));
        type.addMethod(toStringMethod(model));
        type.addMethod(builderMethod(model));
        type.addMethod(toBuilderMethod(model));
        type.addType(builderType(model));
    }

    private MethodSpec getter(FieldModel field) {
        return MethodSpec.methodBuilder("get" + Naming.pascalParam(field.varName()))
                .addModifiers(Modifier.PUBLIC)
                .returns(field.type())
                .addStatement("return $N", field.varName())
                .build();
    }

    private MethodSpec setter(FieldModel field) {
        return MethodSpec.methodBuilder("set" + Naming.pascalParam(field.varName()))
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(field.type(), field.varName()).build())
                .addStatement("this.$N = $N", field.varName(), field.varName())
                .build();
    }

    /** Равны, если тот же класс и все поля попарно равны. */
    private MethodSpec equalsMethod(DtoModel model) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("equals")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addParameter(Object.class, "o")
                .beginControlFlow("if (this == o)")
                .addStatement("return true")
                .endControlFlow()
                .beginControlFlow("if (o == null || getClass() != o.getClass())")
                .addStatement("return false")
                .endControlFlow();
        if (model.fields().isEmpty()) {
            return method.addStatement("return true").build();
        }
        TypeName other = wildcardSelf(model);
        method.addStatement("$T other = ($T) o", other, other);
        CodeBlock.Builder comparison = CodeBlock.builder().add("return ");
        for (int i = 0; i < model.fields().size(); i++) {
            String name = model.fields().get(i).varName();
            // this. обязателен: поле может называться other или o и уступить локальной переменной
            comparison.add(i == 0 ? "" : "\n&& ").add("$T.equals(this.$N, other.$N)", Objects.class, name, name);
        }
        return method.addStatement(comparison.build()).build();
    }

    private MethodSpec hashCodeMethod(DtoModel model) {
        String fields = model.fields().stream().map(FieldModel::varName).collect(Collectors.joining(", "));
        return MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.INT)
                .addStatement("return $T.hash($L)", Objects.class, fields)
                .build();
    }

    /** Формат Lombok @ToString: X(a=1, b=null) — вывод не зависит от стиля. */
    private MethodSpec toStringMethod(DtoModel model) {
        CodeBlock.Builder body = CodeBlock.builder().add("return $S", model.className() + "(");
        for (int i = 0; i < model.fields().size(); i++) {
            String name = model.fields().get(i).varName();
            body.add("\n+ $S + $N", (i == 0 ? "" : ", ") + name + "=", name);
        }
        body.add("\n+ $S", ")");
        return MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement(body.build())
                .build();
    }

    private MethodSpec builderMethod(DtoModel model) {
        List<TypeVariableName> variables = DtoStyle.typeVariables(model);
        return MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(variables)
                .returns(builderTypeName(model))
                .addStatement(variables.isEmpty() ? "return new $T()" : "return new $T<>()", DtoStyle.builderClass(model))
                .build();
    }

    /** Билдер, заполненный текущими значениями: поменять одно поле у готового DTO. */
    private MethodSpec toBuilderMethod(DtoModel model) {
        CodeBlock.Builder body = CodeBlock.builder().add("return new $T()", builderTypeName(model));
        for (FieldModel field : model.fields()) {
            body.add("\n.$N(this.$N)", field.varName(), field.varName());
        }
        return MethodSpec.methodBuilder("toBuilder")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderTypeName(model))
                .addStatement(body.build())
                .build();
    }

    /** XBuilder как у Lombok @Builder: приватные поля, fluent-методы по именам полей, build(). */
    private TypeSpec builderType(DtoModel model) {
        TypeName builderType = builderTypeName(model);
        TypeName self = DtoStyle.selfType(model);
        TypeSpec.Builder builder = TypeSpec.classBuilder(DtoStyle.builderClass(model).simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(DtoStyle.typeVariables(model))
                .addMethod(MethodSpec.constructorBuilder().build());
        MethodSpec.Builder build = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(self)
                .addStatement("$T result = new $T()", self, self);
        for (FieldModel field : model.fields()) {
            String name = field.varName();
            builder.addField(FieldSpec.builder(field.type(), name, Modifier.PRIVATE).build());
            builder.addMethod(MethodSpec.methodBuilder(name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderType)
                    .addParameter(field.type(), name)
                    .addStatement("this.$N = $N", name, name)
                    .addStatement("return this")
                    .build());
            // this. обязателен: поле может называться result (OperationStatusDto в спеке bookstore)
            build.addStatement("result.$N = this.$N", name, name);
        }
        return builder.addMethod(build.addStatement("return result").build()).build();
    }

    private TypeName builderTypeName(DtoModel model) {
        return DtoStyle.parameterized(DtoStyle.builderClass(model), model);
    }

    /** X или X&lt;?&gt; — для приведения в equals. */
    private TypeName wildcardSelf(DtoModel model) {
        ClassName raw = ClassName.get(model.packageName(), model.className());
        if (model.typeVariables().isEmpty()) {
            return raw;
        }
        TypeName[] wildcards = model.typeVariables().stream()
                .map(v -> WildcardTypeName.subtypeOf(Object.class))
                .toArray(TypeName[]::new);
        return ParameterizedTypeName.get(raw, wildcards);
    }
}
