package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Контракт CLI: коды возврата 0/1/2, справка в stdout, ошибки и usage при ошибке — в stderr.
 */
class MainTest {

    private static final String SPEC = """
            {
              "openapi": "3.0.0",
              "info": {"title": "t", "version": "1.0"},
              "paths": {
                "/api/v1/drivers": {"get": {"operationId": "DriversController_findAll",
                    "responses": {"200": {"description": "ok"}}}},
                "/api/v1/drivers/{id}": {"get": {"operationId": "DriversController_findOne",
                    "parameters": [{"name": "id", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {"200": {"description": "ok"}}}}
              }
            }
            """;
    private static final String SPEC_OUTSIDE_ROOT = """
            {
              "openapi": "3.0.0",
              "info": {"title": "t", "version": "1.0"},
              "paths": {
                "/other/x": {"get": {"operationId": "OtherController_findAll",
                    "responses": {"200": {"description": "ok"}}}}
              }
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void helpReturnsZeroAndPrintsUsageToStdout() {
        Run run = run("--help");
        assertEquals(0, run.code());
        assertTrue(run.stdout().contains("Использование"));
        assertTrue(run.stderr().isEmpty());
    }

    @Test
    void noArgsIsUsageError() {
        Run run = run();
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("Использование"));
        assertTrue(run.stdout().isEmpty());
    }

    @Test
    void unknownFlagIsUsageError() throws Exception {
        Run run = run(writeSpec("spec.json", SPEC),
                tempDir.resolve("out").toString(), "gen.test", "--wat");
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("Неизвестный аргумент"));
        assertTrue(run.stderr().contains("Использование"));
    }

    @Test
    void missingFlagValueIsUsageError() throws Exception {
        Run run = run(writeSpec("spec.json", SPEC),
                tempDir.resolve("out").toString(), "gen.test", "--ignore");
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("Нет значения у флага --ignore"));
    }

    @Test
    void badRegexIsUsageError() throws Exception {
        Run run = run(writeSpec("spec.json", SPEC),
                tempDir.resolve("out").toString(), "gen.test", "--ignore", "[");
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("Плохой regex"));
    }

    @Test
    void badImportIsUsageError() throws Exception {
        Run run = run(writeSpec("spec.json", SPEC),
                tempDir.resolve("out").toString(), "gen.test", "--import", "Foo");
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("--import"));
    }

    @Test
    void blankOrMalformedBasePackageIsUsageError() throws Exception {
        String spec = writeSpec("spec.json", SPEC);
        String outDir = tempDir.resolve("out").toString();
        assertEquals(2, run(spec, outDir, "").code());
        assertEquals(2, run(spec, outDir, "a..b").code());
        assertEquals(2, run(spec, outDir, "1abc").code());
    }

    @Test
    void treeModePrintsSummary() throws Exception {
        Run run = run(writeSpec("spec.json", SPEC));
        assertEquals(0, run.code());
        assertTrue(run.stdout().contains("Путей:"));
        assertTrue(run.stderr().isEmpty());
    }

    @Test
    void generationWritesRootToOutDir() throws Exception {
        Path outDir = tempDir.resolve("out");
        Run run = run(writeSpec("spec.json", SPEC), outDir.toString(), "gen.test");
        assertEquals(0, run.code());
        assertTrue(run.stdout().contains("Сгенерировано интерфейсов"));
        Path root = outDir.resolve("gen/test/V1Resource.java");
        assertTrue(Files.isRegularFile(root));
        assertTrue(Files.readString(root).contains("public interface V1Resource {"),
                "Без --super-interface интерфейс без предков");
    }

    @Test
    void lombokFlagSwitchesDtoStyle() throws Exception {
        String spec = writeSpec("spec-dto.json", """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1.0"},
                  "paths": {"/api/v1/drivers": {"get": {"operationId": "DriversController_findAll",
                      "responses": {"200": {"description": "ok"}}}}},
                  "components": {"schemas": {
                    "DriverDto": {"type": "object", "properties": {"name": {"type": "string"}}}
                  }}
                }
                """);
        Path plain = tempDir.resolve("plain");
        Path lombok = tempDir.resolve("lombok");

        assertEquals(0, run(spec, plain.toString(), "gen.test").code());
        assertEquals(0, run(spec, lombok.toString(), "gen.test", "--lombok").code());

        String plainDto = Files.readString(plain.resolve("gen/test/model/DriverDto.java"));
        String lombokDto = Files.readString(lombok.resolve("gen/test/model/DriverDto.java"));
        assertTrue(plainDto.contains("public static DriverDtoBuilder builder()"));
        assertFalse(plainDto.contains("lombok"));
        assertTrue(lombokDto.contains("@Data"));
        assertFalse(lombokDto.contains("public String getName()"));
    }

    @Test
    void splitModelPutsDtoIntoGroupPackage() throws Exception {
        String spec = writeSpec("spec-split.json", """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "t", "version": "1.0"},
                  "paths": {"/api/v1/daily-demands": {"get": {"operationId": "DailyDemandsController_findAll",
                      "responses": {"200": {"description": "ok", "content": {"application/json":
                          {"schema": {"$ref": "#/components/schemas/DailyDemandDto"}}}}}}}},
                  "components": {"schemas": {
                    "DailyDemandDto": {"type": "object", "properties": {"id": {"type": "string"}}},
                    "Orphan": {"type": "object", "properties": {"id": {"type": "string"}}}
                  }}
                }
                """);
        Path outDir = tempDir.resolve("split");

        assertEquals(0, run(spec, outDir.toString(), "gen.test", "--split-model",
                "--model-package", "Orphan=daily-demands").code());

        assertTrue(Files.isRegularFile(outDir.resolve("gen/test/model/dailydemands/DailyDemandDto.java")));
        assertTrue(Files.isRegularFile(outDir.resolve("gen/test/model/dailydemands/Orphan.java")), "закрепление");
        assertTrue(Files.readString(outDir.resolve("gen/test/resources/dailydemands/DailyDemandsResource.java"))
                .contains("import gen.test.model.dailydemands.DailyDemandDto;"));
    }

    @Test
    void modelPackageNeedsSplitModelAndValidGroup() throws Exception {
        String spec = writeSpec("spec.json", SPEC);
        String outDir = tempDir.resolve("out").toString();

        Run withoutSplit = run(spec, outDir, "gen.test", "--model-package", "DriverDto=drivers");
        assertEquals(2, withoutSplit.code());
        assertTrue(withoutSplit.stderr().contains("--split-model"));
        assertEquals(2, run(spec, outDir, "gen.test", "--split-model", "--model-package", "DriverDto").code());
        assertEquals(2, run(spec, outDir, "gen.test", "--split-model", "--model-package", "DriverDto=a.b").code());
    }

    @Test
    void superInterfaceIsOptIn() throws Exception {
        Path outDir = tempDir.resolve("out");
        Run run = run(writeSpec("spec.json", SPEC), outDir.toString(), "gen.test",
                "--super-interface", "com.example.Base");
        assertEquals(0, run.code());
        assertTrue(Files.readString(outDir.resolve("gen/test/V1Resource.java"))
                .contains("public interface V1Resource extends Base<V1Resource>"));
        assertTrue(Files.readString(outDir.resolve("gen/test/resources/drivers/DriversResource.java"))
                .contains("extends Base<DriversResource>"));
    }

    @Test
    void badSuperInterfaceIsUsageError() throws Exception {
        String spec = writeSpec("spec.json", SPEC);
        String outDir = tempDir.resolve("out").toString();
        assertEquals(2, run(spec, outDir, "gen.test", "--super-interface", "Base").code());
        Run run = run(spec, outDir, "gen.test", "--super-interface", "com..Base");
        assertEquals(2, run.code());
        assertTrue(run.stderr().contains("--super-interface"));
    }

    @Test
    void cleanupRemovesOnlyOwnFiles() throws Exception {
        // outDir указан «как по ошибке» — корень проекта с чужими файлами
        Path outDir = tempDir.resolve("project");
        Path notes = write(outDir.resolve("notes.txt"), "заметки");
        Path app = write(outDir.resolve("src/main/java/App.java"), "class App {}");
        Path handwritten = write(outDir.resolve("gen/test/model/Handwritten.java"),
                "package gen.test.model; class Handwritten {}");
        Path stale = write(outDir.resolve("gen/test/model/Stale.java"),
                "/** " + Generator.GENERATED_MARKER + " */ package gen.test.model; class Stale {}");
        Path staleGroup = write(outDir.resolve("gen/test/resources/old/OldResource.java"),
                "/** " + Generator.GENERATED_MARKER + " */ package gen.test.resources.old; interface OldResource {}");
        String spec = writeSpec("spec.json", SPEC);

        assertEquals(0, run(spec, outDir.toString(), "gen.test").code());
        // повторный прогон поверх своего же вывода
        assertEquals(0, run(spec, outDir.toString(), "gen.test").code());

        assertTrue(Files.exists(notes), "Чужие файлы вне basePackage не трогаем");
        assertTrue(Files.exists(app));
        assertTrue(Files.exists(handwritten), "Чужие файлы внутри basePackage тоже не трогаем");
        assertFalse(Files.exists(stale), "Устаревший сгенерированный класс удаляется");
        assertFalse(Files.exists(staleGroup.getParent()), "Опустевшая папка устаревшей группы удаляется");
        assertTrue(Files.isRegularFile(outDir.resolve("gen/test/V1Resource.java")));
    }

    @Test
    void foreignFileAtTargetPathIsGenerationError() throws Exception {
        Path outDir = tempDir.resolve("out");
        Path foreign = write(outDir.resolve("gen/test/V1Resource.java"), "// рукописный корень");
        Path stale = write(outDir.resolve("gen/test/model/Stale.java"),
                "/** " + Generator.GENERATED_MARKER + " */ package gen.test.model; class Stale {}");

        Run run = run(writeSpec("spec.json", SPEC), outDir.toString(), "gen.test");

        assertEquals(1, run.code());
        assertTrue(run.stderr().contains("перезаписывать не буду"), run.stderr());
        assertEquals("// рукописный корень", Files.readString(foreign));
        assertTrue(Files.exists(stale), "При отказе ничего не удаляется");
    }

    @Test
    void pathOutsideRootIsGenerationError() throws Exception {
        Run run = run(writeSpec("spec-outside.json", SPEC_OUTSIDE_ROOT),
                tempDir.resolve("out").toString(), "gen.test");
        assertEquals(1, run.code());
        assertTrue(run.stderr().contains("вне rootPath"));
        assertTrue(run.stdout().isEmpty());
    }

    @Test
    void missingSpecIsGenerationError() {
        Run run = run(tempDir.resolve("nope.json").toString(),
                tempDir.resolve("out").toString(), "gen.test");
        assertEquals(1, run.code());
        assertTrue(run.stderr().contains("Файл спеки не найден"));
    }

    private String writeSpec(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file.toString();
    }

    private static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private Run run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = Main.run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Run(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private record Run(int code, String stdout, String stderr) {
    }
}
