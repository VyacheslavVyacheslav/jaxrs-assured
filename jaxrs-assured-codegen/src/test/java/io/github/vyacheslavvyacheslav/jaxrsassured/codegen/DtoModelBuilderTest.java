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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoModelBuilderTest {

    private static final GenConfig CONFIG = new GenConfig(Path.of("spec.json"), Path.of("out"),
            "gen", "api/v1", "V1Resource", List.of(),
            null, Map.of());

    @Test
    void buildsPojoWithTypedFieldsRequiredAndNestedEnums() {
        OpenAPI spec = new OpenAPI().components(new io.swagger.v3.oas.models.Components().addSchemas(
                "RegionResponseDto", new ObjectSchema()
                        .addProperty("id", new IntegerSchema())
                        .addProperty("dict_region_id", new IntegerSchema())
                        .addProperty("weight_kg", new NumberSchema())
                        .addProperty("createdAt", new StringSchema())
                        .addProperty("status", new StringSchema()._enum(List.of("ok", "shutting_down")))
                        .addProperty("consumers", new ArraySchema().items(
                                new StringSchema()._enum(List.of("web", "mobile"))))
                        .addRequiredItem("id")));

        List<DtoModel> models = new DtoModelBuilder(new SchemaTypes(CONFIG, spec)).build(spec);

        assertEquals(1, models.size());
        DtoModel model = models.get(0);
        assertEquals("gen.model", model.packageName());
        assertEquals("RegionResponseDto", model.className());
        assertFalse(model.isEnum());

        assertEquals("java.lang.Integer", field(model, "id").type().toString());
        assertTrue(field(model, "id").required());
        assertEquals("dictRegionId", field(model, "dict_region_id").varName());
        assertEquals("java.lang.Integer", field(model, "dict_region_id").type().toString());
        assertFalse(field(model, "dict_region_id").required());
        // number без format (так NestJS пишет любое число) — Double: дробная часть не теряется
        assertEquals("java.lang.Double", field(model, "weight_kg").type().toString());
        assertEquals("java.lang.String", field(model, "createdAt").type().toString());
        assertEquals("gen.model.RegionResponseDto.Status", field(model, "status").type().toString());
        assertEquals("java.util.List<gen.model.RegionResponseDto.Consumers>",
                field(model, "consumers").type().toString());

        assertEquals(2, model.nestedEnums().size());
        EnumModel status = model.nestedEnums().get(0);
        assertEquals("Status", status.className());
        assertEquals("OK", status.constants().get(0).name());
        assertEquals("ok", status.constants().get(0).value());
        assertEquals("SHUTTING_DOWN", status.constants().get(1).name());
        assertEquals("shutting_down", status.constants().get(1).value());
        assertEquals("Consumers", model.nestedEnums().get(1).className());
    }

    @Test
    void typeOverridesChangeOnlyNumericFieldsAndLastMatchWins() {
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("RouteDto", new ObjectSchema()
                        .addProperty("id", new NumberSchema())
                        .addProperty("routeId", new NumberSchema())
                        .addProperty("regionId", new IntegerSchema())
                        .addProperty("lat", new NumberSchema())
                        .addProperty("code", new StringSchema())
                        .addProperty("stopIds", new ArraySchema().items(new NumberSchema())))
                .addSchemas("RegionDto", new ObjectSchema()
                        .addProperty("id", new StringSchema()))
                .addSchemas("PageDto", new ObjectSchema()
                        .addProperty("total", new NumberSchema())
                        .addProperty("page", new NumberSchema())));
        List<TypeOverrideRule> rules = List.of(
                TypeOverrideRule.parse("*.id=Long"),
                TypeOverrideRule.parse("*.*Id=Long"),
                TypeOverrideRule.parse("*.*Ids=Long"),
                TypeOverrideRule.parse("PageDto.*=Integer"),
                TypeOverrideRule.parse("RouteDto.routeId=Integer"),   // последнее совпавшее важнее *.*Id
                TypeOverrideRule.parse("*.code=Long"),                // строка — не трогает
                TypeOverrideRule.parse("*.nothing=Long"));

        DtoModelBuilder builder = new DtoModelBuilder(new SchemaTypes(CONFIG, spec), rules);
        List<DtoModel> models = builder.build(spec);

        DtoModel route = models.stream().filter(m -> m.className().equals("RouteDto")).findFirst().orElseThrow();
        assertEquals("java.lang.Long", field(route, "id").type().toString());
        assertEquals("java.lang.Integer", field(route, "routeId").type().toString());
        assertEquals("java.lang.Long", field(route, "regionId").type().toString(), "integer тоже числовое");
        assertEquals("java.lang.Double", field(route, "lat").type().toString(), "Без правила — как в спеке");
        assertEquals("java.lang.String", field(route, "code").type().toString());
        assertEquals("java.util.List<java.lang.Long>", field(route, "stopIds").type().toString());
        DtoModel region = models.stream().filter(m -> m.className().equals("RegionDto")).findFirst().orElseThrow();
        assertEquals("java.lang.String", field(region, "id").type().toString(), "Строковый id правило *.id не трогает");
        DtoModel page = models.stream().filter(m -> m.className().equals("PageDto")).findFirst().orElseThrow();
        assertEquals("java.lang.Integer", field(page, "total").type().toString());

        assertEquals(List.of("*.code=Long", "*.nothing=Long"), builder.warnings().stream()
                .filter(w -> w.contains("не применилось"))
                .map(w -> w.substring(w.indexOf("Правило ") + 8, w.indexOf(" из typeOverrides")))
                .toList());
    }

    @Test
    void importMappedSchemasAreNotGenerated() {
        OpenAPI spec = new OpenAPI().components(new io.swagger.v3.oas.models.Components()
                .addSchemas("PageDto", new ObjectSchema().addProperties("data", new ArraySchema()))
                .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())));

        GenConfig mapped = new GenConfig(Path.of("spec.json"), Path.of("out"), "gen", "api/v1", "V1Resource",
                List.of(), null, Map.of("PageDto", "com.example.Page"));
        List<DtoModel> models = new DtoModelBuilder(new SchemaTypes(mapped, spec)).build(spec);

        assertEquals(1, models.size());
        assertEquals("RegionResponseDto", models.get(0).className());
    }

    @Test
    void pageWrapperNarrowedByResponseBecomesGenericDto() {
        Schema<?> regionsPage = new Schema<>()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/PageDto"))
                .addAllOfItem(new Schema<>().properties(Map.of("data", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/RegionResponseDto")))));
        ApiResponses responses = new ApiResponses().addApiResponse("200", new ApiResponse().content(
                new Content().addMediaType("application/json", new MediaType().schema(regionsPage))));
        OpenAPI spec = new OpenAPI()
                .components(new Components()
                        .addSchemas("PageDto", new ObjectSchema()
                                .addProperties("data", new ArraySchema().items(new Schema<>()))
                                .addProperties("total", new NumberSchema())
                                .addRequiredItem("data"))
                        .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())))
                .paths(new Paths().addPathItem("/api/v1/regions",
                        new PathItem().get(new Operation().responses(responses))));

        List<DtoModel> models = new DtoModelBuilder(new SchemaTypes(CONFIG, spec)).build(spec);

        assertEquals(2, models.size());
        DtoModel page = models.stream().filter(m -> m.className().equals("PageDto")).findFirst().orElseThrow();
        assertEquals(List.of("T"), page.typeVariables());
        assertEquals("java.util.List<T>", field(page, "data").type().toString());
        assertTrue(field(page, "data").required());
        assertEquals("java.lang.Double", field(page, "total").type().toString());
        DtoModel region = models.stream().filter(m -> m.className().equals("RegionResponseDto")).findFirst().orElseThrow();
        assertTrue(region.typeVariables().isEmpty());
    }

    @Test
    void duplicateFieldNamesCollapseWithWarning() {
        OpenAPI spec = new OpenAPI().components(new io.swagger.v3.oas.models.Components().addSchemas(
                "WeirdDto", new ObjectSchema()
                        .addProperties("opusId", new IntegerSchema())
                        .addProperties("opus_id", new IntegerSchema())));

        DtoModelBuilder builder = new DtoModelBuilder(new SchemaTypes(CONFIG, spec));
        List<DtoModel> models = builder.build(spec);

        assertEquals(1, models.get(0).fields().size());
        assertTrue(builder.warnings().stream().anyMatch(w -> w.contains("opus_id")));
    }

    @Test
    void pureEnumSchemaBecomesEnumModel() {
        OpenAPI spec = new OpenAPI().components(new io.swagger.v3.oas.models.Components().addSchemas(
                "DeliveryCategory", new StringSchema()._enum(List.of("business", "system"))));

        List<DtoModel> models = new DtoModelBuilder(new SchemaTypes(CONFIG, spec)).build(spec);

        assertEquals(1, models.size());
        DtoModel model = models.get(0);
        assertTrue(model.isEnum());
        assertEquals("DeliveryCategory", model.enumSelf().className());
        assertEquals("BUSINESS", model.enumSelf().constants().get(0).name());
        assertEquals("SYSTEM", model.enumSelf().constants().get(1).name());
    }

    @Test
    void singleRefAllOfPropertyBecomesTypedField() {
        // ссылка с описанием, как её пишет NestJS: LocationResponseDto.region = {description, allOf: [$ref]}
        OpenAPI spec = new OpenAPI().components(new Components()
                .addSchemas("LocationRegionSnapshotDto", new ObjectSchema().addProperties("id", new IntegerSchema()))
                .addSchemas("DeliveryCategory", new StringSchema()._enum(List.of("business", "system")))
                .addSchemas("LocationResponseDto", new ObjectSchema()
                        .addProperties("region", new Schema<>().description("Регион, которому принадлежит объект")
                                .addAllOfItem(new Schema<>().$ref("#/components/schemas/LocationRegionSnapshotDto")))
                        .addProperties("category", new ObjectSchema().nullable(true).description("Категория")
                                .addAllOfItem(new Schema<>().$ref("#/components/schemas/DeliveryCategory")))
                        .addRequiredItem("region")));

        SchemaTypes types = new SchemaTypes(CONFIG, spec);
        List<DtoModel> models = new DtoModelBuilder(types).build(spec);

        DtoModel location = models.stream().filter(m -> m.className().equals("LocationResponseDto"))
                .findFirst().orElseThrow();
        assertEquals("gen.model.LocationRegionSnapshotDto", field(location, "region").type().toString());
        assertEquals("Регион, которому принадлежит объект", field(location, "region").description());
        assertTrue(field(location, "region").required());
        assertEquals("gen.model.DeliveryCategory", field(location, "category").type().toString());
        assertTrue(location.nestedEnums().isEmpty(), "Энум по ссылке — отдельный класс, не вложенный");
        assertTrue(types.warnings().isEmpty(), "Без WARN: " + types.warnings());
    }

    private static FieldModel field(DtoModel model, String jsonName) {
        return model.fields().stream()
                .filter(f -> f.jsonName().equals(jsonName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет поля " + jsonName));
    }
}
