package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceModelBuilderTest {

    private static final GenConfig CONFIG = new GenConfig(Path.of("spec.json"), Path.of("out"),
            "gen", "api/v1", "V1Resource", List.of(),
            null, Map.of());

    @Test
    void inlinesParamLeafAndBuildsSubResourceTree() {
        OpenAPI spec = spec();
        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        ResourceModel root = models.get(0);
        assertEquals("V1Resource", root.className());
        assertEquals("api/v1", root.classLevelPath());
        assertEquals("gen", root.packageName());
        assertEquals(2, root.locators().size());
        LocatorModel deliveries = locator(root, "getDeliveriesResource");
        assertEquals("deliveries", deliveries.path());
        assertEquals("gen.resources.deliveries", deliveries.childPackage());
        assertEquals("DeliveriesResource", deliveries.childClass());

        ResourceModel statuses = byName(models, "DeliveryStatusesResource");
        assertEquals("gen.resources.deliveries", statuses.packageName());
        assertEquals(2, statuses.operations().size());

        OperationModel getAll = statuses.operations().get(0);
        assertEquals("getAll", getAll.methodName());
        assertEquals("GET", getAll.httpMethod());
        assertEquals("", getAll.path());
        assertEquals(ParamModel.In.QUERY, getAll.params().get(0).in());
        assertEquals("ids", getAll.params().get(0).paramName());
        assertEquals("java.lang.String", getAll.params().get(0).typeName().toString());
        assertEquals("com.fasterxml.jackson.databind.JsonNode", getAll.returnType().toString());

        OperationModel findOne = statuses.operations().get(1);
        assertEquals("findOne", findOne.methodName());
        assertEquals("{id}", findOne.path());
        assertEquals(ParamModel.In.PATH, findOne.params().get(0).in());
        assertEquals("id", findOne.params().get(0).paramName());

        ResourceModel drivers = byName(models, "DriversResource");
        assertEquals("gen.resources.drivers", drivers.packageName());
        LocatorModel driverLocator = locator(drivers, "getDriverResource");
        assertEquals("{id}", driverLocator.path());
        assertEquals("id", driverLocator.pathParam());
        assertEquals("DriverResource", driverLocator.childClass());

        ResourceModel driver = byName(models, "DriverResource");
        LocatorModel snapshotsLocator = locator(driver, "getSnapshotsResource");
        assertEquals("snapshots", snapshotsLocator.path());
        assertEquals("DriverSnapshotsResource", snapshotsLocator.childClass());

        ResourceModel snapshots = byName(models, "DriverSnapshotsResource");
        assertEquals(1, snapshots.operations().size());
        OperationModel sync = snapshots.operations().get(0);
        assertEquals("POST", sync.httpMethod());
        assertEquals("sync", sync.methodName());
        assertEquals("application/json", sync.consumes());
        assertEquals(ParamModel.In.BODY, sync.params().get(0).in());
        assertEquals("syncDriversDto", sync.params().get(0).varName());
        // спека без components — ссылка на SyncDriversDto не резолвится, тело остаётся JsonNode
        assertEquals("com.fasterxml.jackson.databind.JsonNode", sync.params().get(0).typeName().toString());
    }

    @Test
    void distinguishesParamChildrenByParamName() {
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/locations", new PathItem().get(new Operation()
                        .operationId("LocationsController_findAll")
                        .responses(responses200())))
                .addPathItem("/api/v1/locations/{id}/gates-schedule", new PathItem().get(new Operation()
                        .operationId("LocationsGatesScheduleController_getAll")
                        .responses(responses200())))
                .addPathItem("/api/v1/locations/{locationId}/settings", new PathItem().put(new Operation()
                        .operationId("LocationsSettingsController_update")
                        .responses(new ApiResponses().addApiResponse("204", new ApiResponse())))));

        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        ResourceModel locations = byName(models, "LocationsResource");
        LocatorModel byId = locator(locations, "getLocationResource");
        assertEquals("{id}", byId.path());
        assertEquals("id", byId.pathParam());
        LocatorModel byLocationId = locator(locations, "getLocationIdResource");
        assertEquals("{locationId}", byLocationId.path());
        assertEquals("locationId", byLocationId.pathParam());

        byName(models, "LocationResource");
        byName(models, "LocationGatesScheduleResource");
        byName(models, "LocationIdResource");
        byName(models, "LocationIdSettingsResource");
    }

    @Test
    void pathsOutsideRootAreRejected() {
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/drivers", new PathItem().get(new Operation().operationId("list")))
                .addPathItem("/metrics", new PathItem().get(new Operation().operationId("metrics"))));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec)).build(PathTree.of(spec)));
        assertTrue(failure.getMessage().contains("/metrics"));
    }

    @Test
    void nonJsonResponsesGetMatchingTypesAndProduces() {
        // формы, которые встречаются в NestJS-спеках: файл отчёта, SSE-поток, html-лист
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/reports/file", new PathItem().get(new Operation()
                        .operationId("ReportsController_file")
                        .responses(responses200("application/octet-stream", new StringSchema().format("binary")))))
                .addPathItem("/api/v1/reports/zip", new PathItem().post(new Operation()
                        .operationId("ReportsController_zip")
                        .responses(responses200("application/zip", new StringSchema().format("binary")))))
                .addPathItem("/api/v1/reports/events", new PathItem().get(new Operation()
                        .operationId("ReportsController_events")
                        .responses(responses200("text/event-stream", new StringSchema()))))
                .addPathItem("/api/v1/reports/html", new PathItem().get(new Operation()
                        .operationId("ReportsController_html")
                        .responses(responses200("text/html; charset=utf-8", new StringSchema()))))
                .addPathItem("/api/v1/reports/json", new PathItem().get(new Operation()
                        .operationId("ReportsController_json")
                        .responses(responses200()))));

        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        OperationModel file = operation(models, "ReportFileResource", "file");
        assertEquals("byte[]", file.returnType().toString());
        assertEquals("application/octet-stream", file.produces());
        OperationModel zip = operation(models, "ReportZipResource", "zip");
        assertEquals("byte[]", zip.returnType().toString());
        assertEquals("application/zip", zip.produces());
        OperationModel events = operation(models, "ReportEventsResource", "events");
        assertEquals("java.io.InputStream", events.returnType().toString());
        assertEquals("text/event-stream", events.produces());
        OperationModel html = operation(models, "ReportHtmlResource", "html");
        assertEquals("java.lang.String", html.returnType().toString());
        assertEquals("text/html", html.produces(), "Параметры media type (charset) в @Produces не нужны");
        assertNull(operation(models, "ReportJsonResource", "json").produces(), "JSON — умолчание, без @Produces");
    }

    @Test
    void paramNamesKeepCamelCaseAndStayUniqueInMethod() {
        // SSE-ручка: header last-event-id и query lastEventId дают одно java-имя
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/events", new PathItem().get(new Operation()
                        .operationId("EventsController_operations")
                        .addParametersItem(new Parameter().name("last-event-id").in("header").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("lastEventId").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("last_event_id").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("deliveryDateStart").in("query").schema(new StringSchema()))
                        .responses(responses200()))));

        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        List<ParamModel> params = byName(models, "EventsResource").operations().get(0).params();
        assertEquals(List.of("lastEventId", "lastEventIdQuery", "lastEventIdQuery2", "deliveryDateStart"),
                params.stream().map(ParamModel::varName).toList());
        // имена в HTTP не меняются
        assertEquals(List.of("last-event-id", "lastEventId", "last_event_id", "deliveryDateStart"),
                params.stream().map(ParamModel::paramName).toList());
    }

    @Test
    void missingOperationIdFallsBackWithWarning() {
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/drivers", new PathItem().get(new Operation())));

        PathTree tree = PathTree.of(spec);
        ResourceModelBuilder builder = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec));
        List<ResourceModel> models = builder.build(tree);

        ResourceModel drivers = byName(models, "DriversResource");
        assertEquals("getDrivers", drivers.operations().get(0).methodName());
        assertTrue(builder.warnings().stream().anyMatch(w -> w.contains("operationId")));
    }

    @Test
    void wiresGeneratedDtosIntoBodiesAndReturns() {
        OpenAPI spec = new OpenAPI()
                .components(new Components().addSchemas("SyncDriversDto",
                        new ObjectSchema().addProperties("drivers", new ArraySchema())))
                .paths(new Paths()
                        .addPathItem("/api/v1/drivers/{id}/snapshots", new PathItem().post(new Operation()
                                .operationId("DriverSnapshotsController_sync")
                                .requestBody(new RequestBody().content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/SyncDriversDto")))))
                                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(
                                        new Content().addMediaType("application/json", new MediaType().schema(
                                                new Schema<>().$ref("#/components/schemas/SyncDriversDto")))))))));

        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        // POST /drivers/{id}/snapshots → ручка sync живёт в DriverSnapshotsResource
        OperationModel sync = byName(models, "DriverSnapshotsResource").operations().get(0);
        assertEquals("gen.model.SyncDriversDto", sync.params().get(0).typeName().toString());
        assertEquals("gen.model.SyncDriversDto", sync.returnType().toString());
    }

    @Test
    void listResponsesResolveToGeneratedGenericPage() {
        OpenAPI spec = pagedRegionsSpec();
        List<ResourceModel> models = new ResourceModelBuilder(CONFIG, new SchemaTypes(CONFIG, spec))
                .build(PathTree.of(spec));

        assertEquals("gen.model.PageDto<gen.model.RegionResponseDto>",
                byName(models, "RegionsResource").operations().get(0).returnType().toString());
    }

    @Test
    void listResponsesResolveToMappedPageWrapper() {
        OpenAPI spec = pagedRegionsSpec();
        GenConfig mapped = new GenConfig(Path.of("spec.json"), Path.of("out"), "gen", "api/v1", "V1Resource",
                List.of(), null, Map.of("PageDto", "com.example.Page"));
        List<ResourceModel> models = new ResourceModelBuilder(mapped, new SchemaTypes(mapped, spec))
                .build(PathTree.of(spec));

        assertEquals("com.example.Page<gen.model.RegionResponseDto>",
                byName(models, "RegionsResource").operations().get(0).returnType().toString());
    }

    /** GET /api/v1/regions → allOf [$ref PageDto, {data: array of $ref RegionResponseDto}]. */
    private static OpenAPI pagedRegionsSpec() {
        return new OpenAPI()
                .components(new Components()
                        .addSchemas("PageDto", new ObjectSchema().addProperties("data", new ArraySchema()))
                        .addSchemas("RegionResponseDto", new ObjectSchema().addProperties("id", new IntegerSchema())))
                .paths(new Paths().addPathItem("/api/v1/regions", new PathItem().get(new Operation()
                        .operationId("RegionsController_findAll")
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(
                                new Content().addMediaType("application/json", new MediaType().schema(
                                        new Schema<>()
                                                .addAllOfItem(new Schema<>().$ref("#/components/schemas/PageDto"))
                                                .addAllOfItem(new Schema<>().properties(Map.of("data",
                                                        new ArraySchema().items(new Schema<>()
                                                                .$ref("#/components/schemas/RegionResponseDto")))))))))))));
    }

    private OpenAPI spec() {
        return new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/deliveries/statuses", new PathItem().get(new Operation()
                        .operationId("DeliveryStatusesController_getAll")
                        .addParametersItem(new Parameter().name("ids").in("query").schema(new StringSchema()))
                        .responses(responses200())))
                .addPathItem("/api/v1/deliveries/statuses/{id}", new PathItem().get(new Operation()
                        .operationId("DeliveryStatusesController_findOne")
                        .addParametersItem(new Parameter().name("id").in("path").schema(new StringSchema()))
                        .responses(responses200())))
                .addPathItem("/api/v1/drivers", new PathItem().get(new Operation()
                        .operationId("DriversController_list")
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse()))))
                .addPathItem("/api/v1/drivers/{id}/snapshots", new PathItem().post(new Operation()
                        .operationId("DriverSnapshotsController_sync")
                        .requestBody(new RequestBody().content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/SyncDriversDto")))))
                        .responses(new ApiResponses().addApiResponse("202", new ApiResponse())))));
    }

    private static ApiResponses responses200() {
        return responses200("application/json", new ObjectSchema());
    }

    private static ApiResponses responses200(String mediaType, Schema<?> schema) {
        return new ApiResponses().addApiResponse("200", new ApiResponse().content(
                new Content().addMediaType(mediaType, new MediaType().schema(schema))));
    }

    private static OperationModel operation(List<ResourceModel> models, String className, String methodName) {
        return byName(models, className).operations().stream()
                .filter(o -> o.methodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет ручки " + className + "." + methodName));
    }

    private static LocatorModel locator(ResourceModel model, String methodName) {
        return model.locators().stream()
                .filter(l -> l.methodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет локатора " + methodName));
    }

    private static ResourceModel byName(List<ResourceModel> models, String className) {
        Optional<ResourceModel> found = models.stream()
                .filter(m -> m.className().equals(className))
                .findFirst();
        assertNotNull(found.orElse(null), "Нет интерфейса " + className);
        return found.get();
    }
}
