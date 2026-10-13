package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Единый реестр типов схем: имена схем components → классы DTO (или внешние классы
 * из importMappings). Им пользуются и билдер DTO (поля), и билдер интерфейсов
 * (тела запросов и возвраты ручек).
 * <p>
 * Правила резолвинга:
 * <ul>
 *     <li>$ref → класс из importMappings либо сгенерированный в пакете model; allOf из единственного
 *         $ref без собственных свойств (ссылка с description/nullable) — так же, как сам $ref;</li>
 *     <li>allOf [$ref-обёртка W, inline {P: array of $ref X}] → W&lt;X&gt; — форма выдачи постраничных
 *         выборок (PageDto&lt;RegionResponseDto&gt;). Сгенерированная W, которую ответы сужают так,
 *         становится дженериком W&lt;T&gt; с полем List&lt;T&gt; P; прямой $ref на неё — W&lt;JsonNode&gt;;</li>
 *     <li>прочие allOf (настоящий merge/наследование), oneOf/anyOf без дискриминатора → JsonNode;</li>
 *     <li>array → List&lt;тип items&gt;; string → String (в том числе date-time); integer → Integer/Long(int64);
 *         number → Double (с int32 → Integer, int64 → Long); boolean → Boolean;
 *         безтиповые и free-form схемы → JsonNode.</li>
 *     <li>исключение — параметры: number без format → Integer. Параметр уходит в URL через toString(),
 *         и Double дал бы "20.0" вместо "20" для id в пути, page, limit.</li>
 * </ul>
 */
final class SchemaTypes {

    /** Типовая переменная дженерик-обёртки: PageDto&lt;T&gt;. */
    static final String WRAPPER_TYPE_VARIABLE = "T";

    private static final TypeName JSON_NODE = ClassName.get("com.fasterxml.jackson.databind", "JsonNode");
    private static final TypeName STRING = TypeName.get(String.class);
    private static final TypeName BOXED_LONG = TypeName.LONG.box();
    private static final TypeName BOXED_INTEGER = TypeName.INT.box();
    private static final TypeName BOXED_DOUBLE = TypeName.DOUBLE.box();
    private static final TypeName BOXED_BOOLEAN = TypeName.BOOLEAN.box();
    private static final TypeName BYTE_ARRAY = ArrayTypeName.of(TypeName.BYTE);

    private final Map<String, String> importMappings;
    private final Components components;
    private final ModelPackages modelPackages;
    private final Set<String> generated = new HashSet<>();
    /** Сгенерированная схема-обёртка → её свойство-массив, ставшее List&lt;T&gt;. */
    private final Map<String, String> genericWrappers = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    SchemaTypes(GenConfig config, OpenAPI spec) {
        this.importMappings = config.importMappings();
        this.components = spec.getComponents();
        this.modelPackages = ModelPackages.of(config, spec);
        warnings.addAll(modelPackages.warnings());
        registerGeneratable();
        registerGenericWrappers(spec);
    }

    List<String> warnings() {
        return warnings;
    }

    /** Схема станет классом в пакете model (её можно резолвить как DTO). */
    boolean generated(String schemaName) {
        return generated.contains(schemaName);
    }

    /** Пакет класса схемы: model или, с splitModel, model.&lt;группа&gt; — одна таблица и для классов, и для ссылок. */
    String dtoPackage(String schemaName) {
        return modelPackages.packageOf(schemaName);
    }

    Set<String> generatedNames() {
        return Set.copyOf(generated);
    }

    /** Свойство, ставшее List&lt;T&gt; у дженерик-обёртки (PageDto → data), иначе null. */
    String genericProperty(String schemaName) {
        return genericWrappers.get(schemaName);
    }

    /** Схема → тип для поля DTO или тела запроса. */
    TypeName fieldType(Schema<?> schema) {
        return schemaType(schema, false);
    }

    /**
     * Схема → тип возврата ручки: поверх fieldType работает allOf-обёртка PageDto&lt;X&gt;,
     * а файл (string/binary: zip, octet-stream) — byte[]: в String байты испортились бы при декодировании.
     */
    TypeName responseType(Schema<?> schema) {
        if (schema != null && "string".equals(schema.getType()) && "binary".equals(schema.getFormat())) {
            return BYTE_ARRAY;
        }
        return schemaType(schema, true);
    }

    /** Схема → тип query/header/path-параметра. */
    TypeName paramType(Schema<?> schema) {
        if (schema == null) {
            return STRING;
        }
        if (schema.get$ref() != null) {
            return resolveRef(schema.get$ref(), "параметр");
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return STRING;
        }
        String type = schema.getType();
        if (type == null) {
            return STRING;
        }
        if ("array".equals(type)) {
            return listOf(paramType(schema.getItems()));
        }
        if ("number".equals(type) && schema.getFormat() == null) {
            // в URL параметр уходит через toString(): Double дал бы /drivers/1.0 и ?limit=20.0
            return BOXED_INTEGER;
        }
        return primitive(type, schema.getFormat());
    }

    private void registerGeneratable() {
        if (components == null || components.getSchemas() == null) {
            return;
        }
        for (Map.Entry<String, Schema> entry : components.getSchemas().entrySet()) {
            String name = entry.getKey();
            if (importMappings.containsKey(name)) {
                continue;
            }
            if (!Naming.isIdentifier(name)) {
                warnings.add("Схема " + name + " не станет классом (имя не идентификатор) — будет JsonNode");
                continue;
            }
            Schema<?> schema = entry.getValue();
            if (isObjectWithProperties(schema) || isPureEnum(schema)) {
                generated.add(name);
            } else {
                warnings.add("Схема " + name + " без свойств или не объект — будет JsonNode");
            }
        }
    }

    /**
     * Дженерик-обёртки ищем по ответам ручек: allOf [$ref W, {P: array}] сужает свойство P схемы W.
     * W становится дженериком, если она генерируется, сама содержит массив P и все ответы сужают
     * одно и то же свойство; иначе такие ответы — JsonNode + WARN.
     */
    private void registerGenericWrappers(OpenAPI spec) {
        if (spec.getPaths() == null) {
            return;
        }
        Map<String, Set<String>> narrowed = new LinkedHashMap<>();
        for (PathItem item : spec.getPaths().values()) {
            for (Operation operation : item.readOperations()) {
                if (operation.getResponses() == null) {
                    continue;
                }
                for (ApiResponse response : operation.getResponses().values()) {
                    if (response == null || response.getContent() == null) {
                        continue;
                    }
                    for (MediaType mediaType : response.getContent().values()) {
                        Wrapper wrapper = mediaType == null ? null : wrapperOf(mediaType.getSchema());
                        if (wrapper != null && generated.contains(wrapper.name())) {
                            narrowed.computeIfAbsent(wrapper.name(), k -> new LinkedHashSet<>())
                                    .add(wrapper.property());
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : narrowed.entrySet()) {
            String name = entry.getKey();
            if (entry.getValue().size() > 1) {
                warnings.add("Обёртка " + name + " сужает разные свойства " + entry.getValue()
                        + " — не станет дженериком");
                continue;
            }
            String property = entry.getValue().iterator().next();
            Schema<?> own = components.getSchemas().get(name);
            Schema<?> ownProperty = own.getProperties() == null ? null : own.getProperties().get(property);
            if (ownProperty == null || !"array".equals(ownProperty.getType())) {
                warnings.add("Обёртка " + name + " без собственного массива " + property
                        + " — не станет дженериком");
                continue;
            }
            genericWrappers.put(name, property);
        }
    }

    private boolean isObjectWithProperties(Schema<?> schema) {
        return schema != null && "object".equals(schema.getType())
                && schema.getProperties() != null && !schema.getProperties().isEmpty();
    }

    private boolean isPureEnum(Schema<?> schema) {
        return schema != null && "string".equals(schema.getType())
                && schema.getEnum() != null && !schema.getEnum().isEmpty();
    }

    private TypeName schemaType(Schema<?> schema, boolean allowWrapper) {
        if (schema == null) {
            return JSON_NODE;
        }
        if (schema.get$ref() != null) {
            return resolveRef(schema.get$ref(), "схема");
        }
        String annotatedRef = annotatedRef(schema);
        if (annotatedRef != null) {
            return resolveRef(annotatedRef, "схема");
        }
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            return allowWrapper ? allOfWrapper(schema) : unsupportedMerge(schema);
        }
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()
                || schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return unsupportedMerge(schema);
        }
        String type = schema.getType();
        if (type == null) {
            return JSON_NODE;
        }
        if ("array".equals(type)) {
            return listOf(schemaType(schema.getItems(), false));
        }
        // инлайн-объекты и free-form (additionalProperties без свойств) не генерим — JsonNode
        return "object".equals(type) ? JSON_NODE : primitive(type, schema.getFormat());
    }

    /**
     * Ссылка с описанием: allOf из единственного $ref без собственных свойств. Так NestJS оформляет
     * $ref, которому нужны description/nullable (в OAS 3.0 соседи голого $ref игнорируются) —
     * по смыслу это сам $ref. С соседними properties это уже merge — не сюда.
     */
    private String annotatedRef(Schema<?> schema) {
        if (schema.getAllOf() == null || schema.getAllOf().size() != 1
                || schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return null;
        }
        return schema.getAllOf().get(0).get$ref();
    }

    /** Ответ-выборка: allOf [$ref W, {P: array of $ref X}] → W&lt;X&gt; (W сгенерирована или из importMappings). */
    private TypeName allOfWrapper(Schema<?> schema) {
        Wrapper wrapper = wrapperOf(schema);
        if (wrapper == null) {
            return unsupportedMerge(schema);
        }
        TypeName element = schemaType(wrapper.items(), false);
        if (importMappings.containsKey(wrapper.name())) {
            return ParameterizedTypeName.get(ClassName.bestGuess(importMappings.get(wrapper.name())), element);
        }
        if (genericWrappers.containsKey(wrapper.name())) {
            return ParameterizedTypeName.get(ClassName.get(dtoPackage(wrapper.name()), wrapper.name()), element);
        }
        warnings.add("allOf-обёртка " + wrapper.name() + " не стала дженериком — ответ будет JsonNode");
        return JSON_NODE;
    }

    /** Форма ответа-выборки: ровно две ветки allOf — $ref W и inline с единственным свойством-массивом. */
    private Wrapper wrapperOf(Schema<?> schema) {
        if (schema == null || schema.getAllOf() == null || schema.getAllOf().size() != 2) {
            return null;
        }
        String ref = null;
        Map.Entry<String, Schema> narrowed = null;
        for (Schema<?> branch : schema.getAllOf()) {
            if (branch.get$ref() != null) {
                if (ref != null) {
                    return null;
                }
                ref = branch.get$ref();
                continue;
            }
            Map<String, Schema> properties = branch.getProperties();
            if (narrowed != null || properties == null || properties.size() != 1) {
                return null;
            }
            narrowed = properties.entrySet().iterator().next();
            if (narrowed.getValue() == null || !"array".equals(narrowed.getValue().getType())) {
                return null;
            }
        }
        if (ref == null || narrowed == null) {
            return null;
        }
        return new Wrapper(ref.substring(ref.lastIndexOf('/') + 1), narrowed.getKey(),
                narrowed.getValue().getItems());
    }

    private TypeName unsupportedMerge(Schema<?> schema) {
        warnings.add("Схема allOf/oneOf/anyOf без распознанной обёртки (наследование и merge "
                + "не поддержаны) — будет JsonNode");
        return JSON_NODE;
    }

    private TypeName listOf(TypeName element) {
        return ParameterizedTypeName.get(ClassName.get(List.class), element);
    }

    private TypeName primitive(String type, String format) {
        return switch (type) {
            case "integer" -> "int64".equals(format) ? BOXED_LONG : BOXED_INTEGER;
            // number в OpenAPI — любое число: без format дробная часть возможна, Integer её молча отрежет
            case "number" -> "int64".equals(format) ? BOXED_LONG
                    : "int32".equals(format) ? BOXED_INTEGER
                    : BOXED_DOUBLE;
            case "boolean" -> BOXED_BOOLEAN;
            case "string" -> STRING;
            default -> JSON_NODE;
        };
    }

    private TypeName resolveRef(String ref, String context) {
        String name = ref.substring(ref.lastIndexOf('/') + 1);
        if (importMappings.containsKey(name)) {
            return ClassName.bestGuess(importMappings.get(name));
        }
        if (generated.contains(name)) {
            ClassName dto = ClassName.get(dtoPackage(name), name);
            // дженерик-обёртка без сужения: записи в спеке произвольные
            return genericWrappers.containsKey(name) ? ParameterizedTypeName.get(dto, JSON_NODE) : dto;
        }
        warnings.add("Ссылка " + ref + " (" + context + ") на несгенерированную схему — будет JsonNode");
        return JSON_NODE;
    }

    /** Разобранная обёртка выборки: схема W, сужаемое свойство P и схема его элементов. */
    private record Wrapper(String name, String property, Schema<?> items) {
    }
}
