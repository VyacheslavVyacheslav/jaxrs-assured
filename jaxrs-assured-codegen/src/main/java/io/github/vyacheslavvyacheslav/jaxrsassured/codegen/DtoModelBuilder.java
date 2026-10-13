package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Схемы components → модели DTO. Объектные схемы с свойствами становятся POJO,
 * чисто-энумные схемы — отдельными энумами; инлайн-энумы в свойствах — вложенными
 * энумами POJO; обёртка выборки (PageDto) — дженериком PageDto&lt;T&gt; с полем List&lt;T&gt; data.
 * Схемы из importMappings и нестандартные (free-form, без свойств)
 * не генерируются — их резолвинг SchemaTypes уводит в JsonNode.
 */
final class DtoModelBuilder {

    private final SchemaTypes types;
    private final List<String> warnings = new ArrayList<>();

    /** Пакеты классов и типы полей берёт у SchemaTypes — одна таблица на классы и ссылки. */
    DtoModelBuilder(SchemaTypes types) {
        this.types = types;
    }

    List<String> warnings() {
        return warnings;
    }

    List<DtoModel> build(OpenAPI spec) {
        if (spec.getComponents() == null || spec.getComponents().getSchemas() == null) {
            return List.of();
        }
        List<DtoModel> models = new ArrayList<>();
        for (Map.Entry<String, Schema> entry : spec.getComponents().getSchemas().entrySet()) {
            if (!types.generated(entry.getKey())) {
                continue;
            }
            models.add(isPureEnum(entry.getValue())
                    ? enumOf(entry.getKey(), entry.getValue())
                    : objectOf(entry.getKey(), entry.getValue()));
        }
        return models;
    }

    private boolean isPureEnum(Schema<?> schema) {
        return "string".equals(schema.getType()) && schema.getEnum() != null && !schema.getEnum().isEmpty();
    }

    private DtoModel enumOf(String className, Schema<?> schema) {
        EnumModel enumModel = new EnumModel(className, constantsOf(schema.getEnum(), className));
        return new DtoModel(types.dtoPackage(className), className, schema.getDescription(),
                List.of(), List.of(), List.of(), enumModel);
    }

    private DtoModel objectOf(String className, Schema<?> schema) {
        // дженерик-обёртка выборки: сужаемое ответами свойство-массив становится List<T>
        String genericProperty = types.genericProperty(className);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());
        Set<String> takenVars = new HashSet<>();
        Set<String> takenEnumNames = new HashSet<>();
        List<FieldModel> fields = new ArrayList<>();
        List<EnumModel> nestedEnums = new ArrayList<>();

        for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
            String jsonName = entry.getKey();
            Schema<?> property = entry.getValue();
            String varName = Naming.fieldName(jsonName);
            if (!Naming.isUsableIdentifier(varName) || !takenVars.add(varName)) {
                warnings.add("Свойство " + jsonName + " схемы " + className
                        + " не станет полем (имя занято или не идентификатор) — пропущено");
                continue;
            }
            boolean array = "array".equals(property.getType());
            Schema<?> enumHolder = array ? property.getItems() : property;
            TypeName fieldType;
            if (jsonName.equals(genericProperty)) {
                fieldType = ParameterizedTypeName.get(ClassName.get(List.class),
                        TypeVariableName.get(SchemaTypes.WRAPPER_TYPE_VARIABLE));
            } else if (enumHolder != null && enumHolder.getEnum() != null && !enumHolder.getEnum().isEmpty()) {
                String enumName = Naming.pascalParam(jsonName);
                if (!takenEnumNames.add(enumName)) {
                    warnings.add("Вложенный энум " + enumName + " в " + className + " — коллизия имён, "
                            + "свойство " + jsonName + " станет JsonNode");
                    fieldType = types.fieldType(property);
                } else {
                    EnumModel nested = new EnumModel(enumName,
                            constantsOf(enumHolder.getEnum(), className + "." + enumName));
                    nestedEnums.add(nested);
                    ClassName enumType = ClassName.get(types.dtoPackage(className), className, enumName);
                    fieldType = array ? ParameterizedTypeName.get(ClassName.get(List.class), enumType) : enumType;
                }
            } else {
                fieldType = types.fieldType(property);
            }
            fields.add(new FieldModel(jsonName, varName, fieldType, required.contains(jsonName),
                    property.getDescription()));
        }
        return new DtoModel(types.dtoPackage(className), className, schema.getDescription(),
                genericProperty == null ? List.of() : List.of(SchemaTypes.WRAPPER_TYPE_VARIABLE),
                fields, nestedEnums, null);
    }

    /** Значения энума → константы; коллизии после нормализации получают суффикс _2, _3... */
    private List<EnumModel.Constant> constantsOf(List<?> values, String context) {
        List<EnumModel.Constant> constants = new ArrayList<>();
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
            constants.add(new EnumModel.Constant(unique, String.valueOf(value)));
        }
        return constants;
    }
}
