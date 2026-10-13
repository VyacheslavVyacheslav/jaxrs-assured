package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Печать моделей интерфейсов в java-файлы JavaPoet'ом: чистые JAX-RS-интерфейсы,
 * @Path на методах-локаторах, class-level @Path только у корня. Супер-интерфейс
 * (GenConfig.superInterface&lt;СамИнтерфейс&gt;) — только если задан.
 */
final class ResourceWriter {

    private static final TypeName INPUT_STREAM = ClassName.get(InputStream.class);

    private final GenConfig config;

    ResourceWriter(GenConfig config) {
        this.config = config;
    }

    void write(List<ResourceModel> models, java.nio.file.Path outDir) throws IOException {
        Files.createDirectories(outDir);
        for (ResourceModel model : models) {
            JavaFile.builder(model.packageName(), typeSpec(model))
                    .indent("    ")
                    .skipJavaLangImports(true)
                    .build()
                    .writeTo(outDir);
        }
    }

    private TypeSpec typeSpec(ResourceModel model) {
        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(model.className())
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("$L\n", Generator.GENERATED_MARKER);
        if (config.superInterface() != null) {
            ClassName self = ClassName.get(model.packageName(), model.className());
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.bestGuess(config.superInterface()), self));
        }
        if (model.classLevelPath() != null) {
            builder.addAnnotation(AnnotationSpec.builder(Path.class)
                    .addMember("value", "$S", model.classLevelPath()).build());
        }
        for (OperationModel operation : model.operations()) {
            builder.addMethod(operationSpec(operation));
        }
        for (LocatorModel locator : model.locators()) {
            builder.addMethod(locatorSpec(locator));
        }
        return builder.build();
    }

    private MethodSpec operationSpec(OperationModel operation) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(operation.methodName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(httpAnnotation(operation.httpMethod()));
        if (!operation.path().isEmpty()) {
            method.addAnnotation(AnnotationSpec.builder(Path.class)
                    .addMember("value", "$S", operation.path()).build());
        }
        if (operation.consumes() != null) {
            method.addAnnotation(AnnotationSpec.builder(Consumes.class)
                    .addMember("value", "$S", operation.consumes()).build());
        }
        if (operation.produces() != null) {
            method.addAnnotation(AnnotationSpec.builder(Produces.class)
                    .addMember("value", "$S", operation.produces()).build());
        }
        if (!operation.returnType().equals(TypeName.VOID)) {
            method.returns(operation.returnType());
        }
        if (operation.returnType().equals(INPUT_STREAM)) {
            // проверено на RestEasy 6.2: вызов возвращается сразу, а stream.close() дочитывает поток до конца
            method.addJavadoc("Поток $L: вызов возвращается сразу, данные читаются из потока по мере появления.\n"
                    + "Поток сам не кончается, а его close() в RestEasy дочитывает его до конца — то есть ждёт вечно.\n"
                    + "Чтобы перестать читать, закрывайте клиент, через который получен ресурс: берите на поток отдельный.\n",
                    operation.produces());
        }
        for (ParamModel param : operation.params()) {
            ParameterSpec.Builder parameter = ParameterSpec.builder(param.typeName(), param.varName());
            switch (param.in()) {
                case PATH -> parameter.addAnnotation(AnnotationSpec.builder(PathParam.class)
                        .addMember("value", "$S", param.paramName()).build());
                case QUERY -> parameter.addAnnotation(AnnotationSpec.builder(QueryParam.class)
                        .addMember("value", "$S", param.paramName()).build());
                case HEADER -> parameter.addAnnotation(AnnotationSpec.builder(HeaderParam.class)
                        .addMember("value", "$S", param.paramName()).build());
                case BODY -> {
                    // тело запроса аннотации не требует
                }
            }
            method.addParameter(parameter.build());
        }
        return method.build();
    }

    private MethodSpec locatorSpec(LocatorModel locator) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(locator.methodName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get(locator.childPackage(), locator.childClass()))
                .addAnnotation(AnnotationSpec.builder(Path.class)
                        .addMember("value", "$S", locator.path()).build());
        if (locator.pathParam() != null) {
            method.addParameter(ParameterSpec.builder(ClassName.get(String.class), Naming.parameter(locator.pathParam()))
                    .addAnnotation(AnnotationSpec.builder(PathParam.class)
                            .addMember("value", "$S", locator.pathParam()).build())
                    .build());
        }
        return method.build();
    }

    private Class<?> httpAnnotation(String method) {
        return switch (method) {
            case "GET" -> GET.class;
            case "POST" -> POST.class;
            case "PUT" -> PUT.class;
            case "PATCH" -> PATCH.class;
            case "DELETE" -> DELETE.class;
            case "HEAD" -> HEAD.class;
            case "OPTIONS" -> OPTIONS.class;
            default -> throw new IllegalStateException("HTTP-метод не поддержан: " + method);
        };
    }
}
