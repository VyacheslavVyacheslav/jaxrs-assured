package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTypesTest {

    private static final GenConfig MAPPED = new GenConfig(Path.of("spec.json"), Path.of("out"),
            "gen", "api/v1", "V1Resource", List.of(),
            null, Map.of("PageDto", "com.example.Page"));

    private static final GenConfig CONFIG = new GenConfig(Path.of("spec.json"), Path.of("out"),
            "gen", "api/v1", "V1Resource", List.of(),
            null, Map.of());

    @Test
    void allOfOverGeneratedWrapperResolvesToGenericDto() {
        Schema<?> page = pageOf("RegionResponseDto");
        OpenAPI spec = new OpenAPI()
                .components(new Components()
                        .addSchemas("PageDto", pageSchema())
                        .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())))
                .paths(new Paths().addPathItem("/api/v1/regions", new PathItem().get(new Operation()
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(
                                new Content().addMediaType("application/json", new MediaType().schema(page))))))));
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        assertTrue(types.generated("PageDto"));
        assertEquals("data", types.genericProperty("PageDto"));
        assertNull(types.genericProperty("RegionResponseDto"));
        assertEquals("gen.model.PageDto<gen.model.RegionResponseDto>", types.responseType(page).toString());
        // прямой $ref без сужения — записи произвольные
        assertEquals("gen.model.PageDto<com.fasterxml.jackson.databind.JsonNode>",
                types.fieldType(new Schema<>().$ref("#/components/schemas/PageDto")).toString());
    }

    @Test
    void wrapperNotNarrowedByResponsesStaysPlainDto() {
        OpenAPI spec = new OpenAPI().components(new Components().addSchemas("PageDto", pageSchema()));
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        assertNull(types.genericProperty("PageDto"));
        assertEquals("gen.model.PageDto",
                types.fieldType(new Schema<>().$ref("#/components/schemas/PageDto")).toString());
        assertEquals("com.fasterxml.jackson.databind.JsonNode",
                types.responseType(pageOf("RegionResponseDto")).toString());
        assertTrue(types.warnings().stream().anyMatch(w -> w.contains("не стала дженериком")));
    }

    private static Schema<?> pageSchema() {
        return new ObjectSchema()
                .addProperties("data", new ArraySchema().items(new Schema<>()))
                .addProperties("total", new NumberSchema());
    }

    private static Schema<?> pageOf(String element) {
        return new Schema<>()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/PageDto"))
                .addAllOfItem(new Schema<>().properties(Map.of("data", new ArraySchema().items(
                        new Schema<>().$ref("#/components/schemas/" + element)))));
    }

    @Test
    void allOfWithMappedWrapperAndDataArrayResolvesToPage() {
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())));
        SchemaTypes types = new SchemaTypes(MAPPED, spec);

        // $ref сам по себе — тип DTO, а не обёртка
        assertEquals("gen.model.RegionResponseDto",
                types.responseType(new Schema<>().$ref("#/components/schemas/RegionResponseDto")).toString());

        Schema<?> page = new Schema<>()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/PageDto"))
                .addAllOfItem(new Schema<>().properties(Map.of("data", new ArraySchema().items(
                        new Schema<>().$ref("#/components/schemas/RegionResponseDto")))));
        assertEquals("com.example.Page<gen.model.RegionResponseDto>",
                types.responseType(page).toString());
    }

    @Test
    void oneOfAndMergeAllOfFallBackToJsonNodeWithWarning() {
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())));
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        Schema<?> oneOf = new Schema<>().addOneOfItem(new StringSchema());
        assertEquals("com.fasterxml.jackson.databind.JsonNode", types.responseType(oneOf).toString());
        assertTrue(types.warnings().stream().anyMatch(w -> w.contains("allOf/oneOf/anyOf")));

        Schema<?> inheritance = new Schema<>()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/RegionResponseDto"))
                .addProperties("extra", new StringSchema());
        assertEquals("com.fasterxml.jackson.databind.JsonNode", types.responseType(inheritance).toString());
        assertEquals("com.fasterxml.jackson.databind.JsonNode", types.fieldType(inheritance).toString());

        // единственная ветка allOf — inline, а не $ref: не ссылка с описанием
        Schema<?> inlineOnly = new Schema<>().addAllOfItem(new ObjectSchema().addProperties("x", new StringSchema()));
        assertEquals("com.fasterxml.jackson.databind.JsonNode", types.fieldType(inlineOnly).toString());
    }

    @Test
    void singleRefAllOfResolvesLikePlainRef() {
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema()))
                .addSchemas("DeliveryCategory", new StringSchema()._enum(List.of("business", "system"))));
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        // NestJS: $ref с description/nullable оборачивается в allOf из одной ветки; type: object рядом не мешает
        Schema<?> region = new ObjectSchema().nullable(true).description("Регион объекта")
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/RegionResponseDto"));
        assertEquals("gen.model.RegionResponseDto", types.fieldType(region).toString());
        assertEquals("gen.model.RegionResponseDto", types.responseType(region).toString());
        assertEquals("java.util.List<gen.model.RegionResponseDto>",
                types.fieldType(new ArraySchema().items(region)).toString());

        Schema<?> category = new Schema<>().description("Категория")
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/DeliveryCategory"));
        assertEquals("gen.model.DeliveryCategory", types.fieldType(category).toString());

        assertTrue(types.warnings().isEmpty(), "Ссылка с описанием не должна давать WARN: " + types.warnings());
    }

    @Test
    void binaryResponseIsByteArray() {
        SchemaTypes types = new SchemaTypes(CONFIG, new OpenAPI());

        // ответ-файл (zip, octet-stream): в String байты испортились бы при декодировании
        assertEquals("byte[]", types.responseType(new StringSchema().format("binary")).toString());
        // в JSON-полях binary не встречается осмысленно — правило только для ответа целиком
        assertEquals("java.lang.String", types.fieldType(new StringSchema().format("binary")).toString());
        assertEquals("java.lang.String", types.responseType(new StringSchema()).toString());
    }

    @Test
    void numberParamsStayInteger() {
        SchemaTypes types = new SchemaTypes(CONFIG, new OpenAPI());

        // параметр уходит в URL через toString(): Double дал бы /drivers/1.0 и ?limit=20.0
        assertEquals("java.lang.Integer", types.paramType(new NumberSchema()).toString());
        assertEquals("java.util.List<java.lang.Integer>",
                types.paramType(new ArraySchema().items(new NumberSchema())).toString());
        // явный format в спеке сильнее
        assertEquals("java.lang.Double", types.paramType(new NumberSchema().format("double")).toString());
        assertEquals("java.lang.Long", types.paramType(new NumberSchema().format("int64")).toString());
        assertEquals("java.lang.Integer", types.paramType(new IntegerSchema()).toString());
        assertEquals("java.lang.String", types.paramType(new StringSchema()).toString());
    }

    @Test
    void pureEnumSchemaIsGeneratedAndResolvable() {
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("DeliveryCategory", new StringSchema()._enum(List.of("business", "system"))));
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        assertTrue(types.generated("DeliveryCategory"));
        assertEquals("gen.model.DeliveryCategory",
                types.fieldType(new Schema<>().$ref("#/components/schemas/DeliveryCategory")).toString());
    }

    @Test
    void primitiveTypes() {
        OpenAPI spec = new OpenAPI();
        SchemaTypes types = new SchemaTypes(CONFIG, spec);

        assertEquals("java.lang.String", types.fieldType(new StringSchema()).toString());
        assertEquals("java.lang.String", types.fieldType(new StringSchema().format("date-time")).toString());
        assertEquals("java.lang.Integer", types.fieldType(new IntegerSchema()).toString());
        assertEquals("java.lang.Long", types.fieldType(new IntegerSchema().format("int64")).toString());
        // number — любое число: без format Double, иначе Integer обрезал бы lat 55.7558 до 55
        assertEquals("java.lang.Double", types.fieldType(new NumberSchema()).toString());
        assertEquals("java.lang.Double", types.responseType(new NumberSchema()).toString());
        assertEquals("java.lang.Double", types.fieldType(new NumberSchema().format("double")).toString());
        assertEquals("java.lang.Double", types.fieldType(new NumberSchema().format("float")).toString());
        assertEquals("java.lang.Integer", types.fieldType(new NumberSchema().format("int32")).toString());
        assertEquals("java.lang.Long", types.fieldType(new NumberSchema().format("int64")).toString());
        assertEquals("java.util.List<java.lang.Double>",
                types.fieldType(new ArraySchema().items(new NumberSchema())).toString());
        assertEquals("java.lang.Boolean", types.fieldType(new Schema<>().type("boolean")).toString());
        assertEquals("java.util.List<java.lang.String>",
                types.fieldType(new ArraySchema().items(new StringSchema())).toString());
        // безтиповые и free-form схемы — JsonNode
        assertEquals("com.fasterxml.jackson.databind.JsonNode", types.fieldType(null).toString());
        assertEquals("com.fasterxml.jackson.databind.JsonNode",
                types.fieldType(new Schema<>().additionalProperties(true)).toString());
    }
}
