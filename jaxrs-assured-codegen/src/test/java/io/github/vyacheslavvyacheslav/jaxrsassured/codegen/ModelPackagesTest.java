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
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Раскладка DTO по подпакетам групп путей (--split-model): схема одной группы — в model.&lt;группа&gt;,
 * общая или ничья — в корне model; ссылки резолвятся по той же таблице.
 */
class ModelPackagesTest {

    @Test
    void flatByDefault() {
        SchemaTypes types = new SchemaTypes(config(false, Map.of()), spec());

        for (String schema : List.of("SyncDriversDto", "DriverSyncItemDto", "DeliveryStatus", "PageDto", "Orphan")) {
            assertEquals("gen.model", types.dtoPackage(schema), schema);
        }
    }

    @Test
    void splitsByPathGroupTransitively() {
        SchemaTypes types = new SchemaTypes(config(true, Map.of()), spec());

        // тело POST drivers/snapshots → SyncDriversDto → items DriverSyncItemDto (транзитивно)
        assertEquals("gen.model.drivers", types.dtoPackage("SyncDriversDto"));
        assertEquals("gen.model.drivers", types.dtoPackage("DriverSyncItemDto"));
        // цикл ссылок A ↔ B не мешает обходу
        assertEquals("gen.model.drivers", types.dtoPackage("CycleA"));
        assertEquals("gen.model.drivers", types.dtoPackage("CycleB"));
        // элементы выборки и enum из query-параметра — группа deliveries
        assertEquals("gen.model.deliveries", types.dtoPackage("DeliveryStatus"));
        assertEquals("gen.model.deliveries", types.dtoPackage("StatusFilter"));
        // группа с дефисом — имя пакета как у resources.dailydemands
        assertEquals("gen.model.dailydemands", types.dtoPackage("DailyDemandDto"));
        // нужна двум группам — корень; не нужна никому — корень
        assertEquals("gen.model", types.dtoPackage("PageDto"));
        assertEquals("gen.model", types.dtoPackage("Orphan"));
    }

    @Test
    void referencesUseTheSameTable() {
        SchemaTypes types = new SchemaTypes(config(true, Map.of()), spec());

        assertEquals("gen.model.PageDto<gen.model.deliveries.DeliveryStatus>",
                types.responseType(pageOf("DeliveryStatus")).toString());
        assertEquals("gen.model.drivers.SyncDriversDto",
                types.fieldType(new Schema<>().$ref("#/components/schemas/SyncDriversDto")).toString());
    }

    @Test
    void classesAndNestedEnumsLandInGroupPackage() {
        OpenAPI spec = spec();
        SchemaTypes types = new SchemaTypes(config(true, Map.of()), spec);
        List<DtoModel> models = new DtoModelBuilder(types).build(spec);

        DtoModel status = models.stream().filter(m -> m.className().equals("DeliveryStatus")).findFirst().orElseThrow();
        assertEquals("gen.model.deliveries", status.packageName());
        FieldModel code = status.fields().stream().filter(f -> f.jsonName().equals("code")).findFirst().orElseThrow();
        assertEquals("gen.model.deliveries.DeliveryStatus.Code", code.type().toString());
        DtoModel page = models.stream().filter(m -> m.className().equals("PageDto")).findFirst().orElseThrow();
        assertEquals("gen.model", page.packageName());
    }

    @Test
    void pinsOverrideGroups() {
        SchemaTypes types = new SchemaTypes(config(true,
                Map.of("DriverSyncItemDto", "", "DeliveryStatus", "drivers", "Orphan", "daily-demands", "Nope", "x")),
                spec());

        assertEquals("gen.model", types.dtoPackage("DriverSyncItemDto"), "пустая группа — корень");
        assertEquals("gen.model.drivers", types.dtoPackage("DeliveryStatus"));
        assertEquals("gen.model.dailydemands", types.dtoPackage("Orphan"));
        assertEquals("gen.model.drivers", types.dtoPackage("SyncDriversDto"), "незакреплённые — по правилу");
        assertTrue(types.warnings().stream().anyMatch(w -> w.contains("Nope")), "опечатка в имени схемы видна");
    }

    private static GenConfig config(boolean split, Map<String, String> pins) {
        return new GenConfig(Path.of("spec.json"), Path.of("out"), "gen", "api/v1", "V1Resource",
                List.of(), null, Map.of(), false, split, pins);
    }

    /** drivers: тело с вложенной схемой и цикл; deliveries: выборка + enum-параметр; daily-demands; общий PageDto. */
    private static OpenAPI spec() {
        return new OpenAPI()
                .components(new Components()
                        .addSchemas("PageDto", new ObjectSchema()
                                .addProperty("data", new ArraySchema().items(new Schema<>()))
                                .addProperty("total", new IntegerSchema()))
                        .addSchemas("SyncDriversDto", new ObjectSchema().addProperty("drivers", new ArraySchema()
                                .items(new Schema<>().$ref("#/components/schemas/DriverSyncItemDto"))))
                        .addSchemas("DriverSyncItemDto", new ObjectSchema().addProperty("id", new StringSchema()))
                        .addSchemas("CycleA", new ObjectSchema().addProperty("b", new Schema<>().$ref("#/components/schemas/CycleB")))
                        .addSchemas("CycleB", new ObjectSchema().addProperty("a", new Schema<>().$ref("#/components/schemas/CycleA")))
                        .addSchemas("DeliveryStatus", new ObjectSchema()
                                .addProperty("code", new StringSchema()._enum(List.of("NEW", "IN_ROUTE"))))
                        .addSchemas("StatusFilter", new StringSchema()._enum(List.of("active", "all")))
                        .addSchemas("DailyDemandDto", new ObjectSchema().addProperty("id", new StringSchema()))
                        .addSchemas("Orphan", new ObjectSchema().addProperty("id", new StringSchema())))
                .paths(new Paths()
                        .addPathItem("/api/v1/drivers", new PathItem().get(new Operation()
                                .operationId("DriversController_findAll")
                                .responses(ok(pageOf("CycleA")))))
                        .addPathItem("/api/v1/drivers/snapshots", new PathItem().post(new Operation()
                                .operationId("DriverSnapshotsController_sync")
                                .requestBody(new RequestBody().content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/SyncDriversDto")))))
                                .responses(new ApiResponses().addApiResponse("204", new ApiResponse()))))
                        .addPathItem("/api/v1/deliveries/statuses", new PathItem().get(new Operation()
                                .operationId("DeliveryStatusesController_findAll")
                                .addParametersItem(new Parameter().name("filter").in("query")
                                        .schema(new Schema<>().$ref("#/components/schemas/StatusFilter")))
                                .responses(ok(pageOf("DeliveryStatus")))))
                        .addPathItem("/api/v1/daily-demands/{id}", new PathItem().get(new Operation()
                                .operationId("DailyDemandsController_findOne")
                                .addParametersItem(new Parameter().name("id").in("path").schema(new StringSchema()))
                                .responses(ok(new Schema<>().$ref("#/components/schemas/DailyDemandDto"))))));
    }

    private static Schema<?> pageOf(String element) {
        return new Schema<>()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/PageDto"))
                .addAllOfItem(new Schema<>().properties(Map.of("data", new ArraySchema().items(
                        new Schema<>().$ref("#/components/schemas/" + element)))));
    }

    private static ApiResponses ok(Schema<?> schema) {
        return new ApiResponses().addApiResponse("200", new ApiResponse().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
