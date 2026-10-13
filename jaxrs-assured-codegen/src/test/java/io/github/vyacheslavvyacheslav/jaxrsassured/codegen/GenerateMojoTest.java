package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Контракт goal generate: параметры как у CLI, папка с результатом — в исходники сборки,
 * ошибка конфигурации — MojoExecutionException, ошибка генерации — MojoFailureException.
 */
class GenerateMojoTest {

    private static final String SPEC = """
            {
              "openapi": "3.0.0",
              "info": {"title": "t", "version": "1.0"},
              "paths": {
                "/api/v1/drivers": {"get": {"operationId": "DriversController_findAll",
                    "responses": {"200": {"description": "ok"}}}},
                "/metrics": {"get": {"operationId": "MetricsController_get",
                    "responses": {"200": {"description": "ok"}}}}
              },
              "components": {"schemas": {
                "DriverDto": {"type": "object", "properties": {"name": {"type": "string"}}}
              }}
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void generatesAndAddsCompileSourceRoot() throws Exception {
        GenerateMojo mojo = mojo();

        mojo.execute();

        assertTrue(Files.isRegularFile(mojo.outputDirectory.toPath().resolve("gen/test/V1Resource.java")));
        assertEquals(List.of(mojo.outputDirectory.getAbsolutePath()), mojo.project.getCompileSourceRoots());
        assertTrue(mojo.project.getTestCompileSourceRoots().isEmpty());
    }

    @Test
    void testSourcesGoToTestCompileSourceRoot() throws Exception {
        GenerateMojo mojo = mojo();
        mojo.testSources = true;

        mojo.execute();

        assertEquals(List.of(mojo.outputDirectory.getAbsolutePath()), mojo.project.getTestCompileSourceRoots());
        assertTrue(mojo.project.getCompileSourceRoots().isEmpty());
    }

    @Test
    void skipGeneratesNothing() throws Exception {
        GenerateMojo mojo = mojo();
        mojo.skip = true;

        mojo.execute();

        assertFalse(Files.exists(mojo.outputDirectory.toPath()));
        assertTrue(mojo.project.getCompileSourceRoots().isEmpty());
    }

    @Test
    void parametersReachGenerator() throws Exception {
        GenerateMojo mojo = mojo();
        mojo.superInterface = "com.example.Base";
        mojo.importMappings = Map.of("PageDto", "com.example.Page");

        mojo.execute();

        assertTrue(Files.readString(mojo.outputDirectory.toPath().resolve("gen/test/V1Resource.java"))
                .contains("public interface V1Resource extends Base<V1Resource>"));
        Path dto = mojo.outputDirectory.toPath().resolve("gen/test/model/DriverDto.java");
        assertTrue(Files.readString(dto).contains("public static DriverDtoBuilder builder()"), "По умолчанию — обычная Java");

        mojo.lombok = true;
        mojo.execute();

        assertTrue(Files.readString(dto).contains("@Data"), "lombok=true — DTO на аннотациях Lombok");
    }

    @Test
    void splitModelAndPins() throws Exception {
        GenerateMojo mojo = mojo();
        mojo.splitModel = true;

        mojo.execute();
        Path model = mojo.outputDirectory.toPath().resolve("gen/test/model");
        // DriverDto не нужен ни одной ручке — корень model
        assertTrue(Files.isRegularFile(model.resolve("DriverDto.java")));

        java.util.HashMap<String, String> pins = new java.util.HashMap<>();
        pins.put("DriverDto", "drivers");
        mojo.modelPackages = pins;
        mojo.execute();
        assertTrue(Files.isRegularFile(model.resolve("drivers/DriverDto.java")));
        assertFalse(Files.exists(model.resolve("DriverDto.java")), "Старое место вычищено");

        // пустой элемент <DriverDto/> Maven отдаёт как null — это корень
        pins.put("DriverDto", null);
        mojo.execute();
        assertTrue(Files.isRegularFile(model.resolve("DriverDto.java")));
    }

    @Test
    void badConfigurationIsExecutionException() throws Exception {
        GenerateMojo badPackage = mojo();
        badPackage.basePackage = "a..b";
        assertThrows(MojoExecutionException.class, badPackage::execute);

        GenerateMojo badRegex = mojo();
        badRegex.ignorePaths = List.of("[");
        assertThrows(MojoExecutionException.class, badRegex::execute);

        GenerateMojo badSuper = mojo();
        badSuper.superInterface = "Base";
        assertThrows(MojoExecutionException.class, badSuper::execute);

        GenerateMojo badImport = mojo();
        badImport.importMappings = Map.of("PageDto", "Page");
        assertThrows(MojoExecutionException.class, badImport::execute);

        GenerateMojo pinWithoutSplit = mojo();
        pinWithoutSplit.modelPackages = Map.of("DriverDto", "drivers");
        assertThrows(MojoExecutionException.class, pinWithoutSplit::execute);

        GenerateMojo badPin = mojo();
        badPin.splitModel = true;
        badPin.modelPackages = Map.of("DriverDto", "a.b");
        assertThrows(MojoExecutionException.class, badPin::execute);
    }

    @Test
    void generationErrorIsFailureWithReason() throws Exception {
        // без ignorePaths путь /metrics — вне rootPath
        GenerateMojo mojo = mojo();
        mojo.ignorePaths = null;

        MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(failure.getMessage().contains("вне rootPath"), failure.getMessage());
        assertTrue(mojo.project.getCompileSourceRoots().isEmpty(), "При ошибке папка в сборку не добавляется");
    }

    /** Mojo с тем, что Maven подставил бы из pom и значений по умолчанию. */
    private GenerateMojo mojo() throws Exception {
        Path spec = tempDir.resolve("spec.json");
        Files.writeString(spec, SPEC);
        GenerateMojo mojo = new GenerateMojo();
        mojo.spec = spec.toFile();
        mojo.outputDirectory = new File(tempDir.toFile(), "generated");
        mojo.basePackage = "gen.test";
        mojo.rootPath = "api/v1";
        mojo.rootName = "V1Resource";
        mojo.ignorePaths = List.of("^/metrics$");
        mojo.project = new MavenProject();
        return mojo;
    }
}
