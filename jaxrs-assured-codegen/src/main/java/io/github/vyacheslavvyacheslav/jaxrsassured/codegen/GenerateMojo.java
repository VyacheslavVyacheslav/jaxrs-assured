package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Maven-плагин: то же, что CLI {@link Main}, но конфигурация — в pom, без exec:java и кавычек в командной строке.
 * Сгенерированная папка добавляется в исходники сборки (testSources=true — в исходники тестов),
 * поэтому build-helper не нужен.
 * <pre>{@code
 * <plugin>
 *     <groupId>io.github.vyacheslavvyacheslav</groupId>
 *     <artifactId>jaxrs-assured-codegen</artifactId>
 *     <version>...</version>
 *     <executions>
 *         <execution>
 *             <goals><goal>generate</goal></goals>
 *             <configuration>
 *                 <spec>docs/openapi/bookstore.json</spec>
 *                 <basePackage>com.example.bookstore</basePackage>
 *                 <ignorePaths><ignorePath>^/(metrics|_healthz|_readiness)$</ignorePath></ignorePaths>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    /** OpenAPI-спека, json или yaml; относительный путь — от корня проекта. */
    @Parameter(property = "jaxrsassured.spec", required = true)
    File spec;

    /** Куда писать исходники. Перед записью удаляются только прошлые сгенерированные файлы внутри basePackage. */
    @Parameter(property = "jaxrsassured.outputDirectory",
            defaultValue = "${project.build.directory}/generated-sources/jaxrs-assured", required = true)
    File outputDirectory;

    /** Пакет корневого интерфейса; ресурсы — в basePackage.resources, DTO — в basePackage.model. */
    @Parameter(property = "jaxrsassured.basePackage", required = true)
    String basePackage;

    /** Корень дерева путей — class-level @Path корневого интерфейса (флаг --root). */
    @Parameter(property = "jaxrsassured.rootPath", defaultValue = "api/v1")
    String rootPath;

    /** Имя корневого интерфейса (флаг --root-name). */
    @Parameter(property = "jaxrsassured.rootName", defaultValue = "V1Resource")
    String rootName;

    /** Пропустить пути, целиком совпавшие с regex (флаг --ignore). */
    @Parameter
    List<String> ignorePaths;

    /** Супер-интерфейс ресурсов, параметризуется самим интерфейсом: Класс&lt;Ресурс&gt; (флаг --super-interface). */
    @Parameter(property = "jaxrsassured.superInterface")
    String superInterface;

    /** Схема → внешний класс, схема не генерируется (флаг --import): {@code <PageDto>com.example.Page</PageDto>}. */
    @Parameter
    Map<String, String> importMappings;

    /** DTO на аннотациях Lombok (флаг --lombok): API тот же, Lombok и обработку аннотаций настраивает проект. */
    @Parameter(property = "jaxrsassured.lombok", defaultValue = "false")
    boolean lombok;

    /** DTO по подпакетам групп путей: model.&lt;группа&gt;, как resources; общие — в корне model (флаг --split-model). */
    @Parameter(property = "jaxrsassured.splitModel", defaultValue = "false")
    boolean splitModel;

    /**
     * Закрепить пакет схемы при splitModel (флаг --model-package): {@code <DriverSyncItemDto>drivers</DriverSyncItemDto>};
     * пустое значение — корень model.
     */
    @Parameter
    Map<String, String> modelPackages;

    /** true — папка добавляется в исходники тестов, а не основного кода. */
    @Parameter(property = "jaxrsassured.testSources", defaultValue = "false")
    boolean testSources;

    /** Пропустить генерацию. */
    @Parameter(property = "jaxrsassured.skip", defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("codegen: генерация пропущена (jaxrsassured.skip)");
            return;
        }
        GenConfig config = config();
        Generator.GenerationResult result;
        try {
            result = Generator.run(config);
        } catch (RuntimeException e) {
            // спека не читается, пути вне rootPath, коллизии, чужой файл на месте исходника
            throw new MojoFailureException("codegen: " + e.getMessage(), e);
        }
        getLog().info(String.format("codegen: интерфейсов %d, DTO %d → %s",
                result.interfaces(), result.dtos(), outputDirectory));
        if (testSources) {
            project.addTestCompileSourceRoot(outputDirectory.getAbsolutePath());
        } else {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }

    /** Те же проверки, что у CLI: ошибка конфигурации видна до генерации. */
    private GenConfig config() throws MojoExecutionException {
        if (!Naming.isQualifiedName(basePackage)) {
            throw new MojoExecutionException("codegen: невалидный basePackage: \"" + basePackage + "\"");
        }
        if (superInterface != null && (!superInterface.contains(".") || !Naming.isQualifiedName(superInterface))) {
            throw new MojoExecutionException("codegen: ожидался superInterface полный.Класс: " + superInterface);
        }
        List<Pattern> ignore = new ArrayList<>();
        for (String regex : ignorePaths == null ? List.<String>of() : ignorePaths) {
            try {
                ignore.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                throw new MojoExecutionException("codegen: плохой regex в ignorePaths: " + regex, e);
            }
        }
        Map<String, String> mappings = importMappings == null ? Map.of() : importMappings;
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            if (!Naming.isIdentifier(mapping.getKey()) || !mapping.getValue().contains(".")
                    || !Naming.isQualifiedName(mapping.getValue())) {
                throw new MojoExecutionException("codegen: ожидалось importMappings <ИмяСхемы>полный.Класс</ИмяСхемы>: "
                        + mapping.getKey() + " → " + mapping.getValue());
            }
        }
        Map<String, String> pins = new LinkedHashMap<>();
        if (modelPackages != null) {
            for (Map.Entry<String, String> pin : modelPackages.entrySet()) {
                // пустой элемент <Схема/> Maven отдаёт как null — это корень model
                String group = pin.getValue() == null ? "" : pin.getValue().trim();
                if (!Naming.isIdentifier(pin.getKey()) || !Naming.isGroupSegment(group)) {
                    throw new MojoExecutionException("codegen: ожидалось modelPackages <ИмяСхемы>группа</ИмяСхемы>: "
                            + pin.getKey() + " → " + pin.getValue());
                }
                pins.put(pin.getKey(), group);
            }
        }
        if (!pins.isEmpty() && !splitModel) {
            throw new MojoExecutionException("codegen: modelPackages работает только вместе со splitModel");
        }
        return new GenConfig(spec.toPath(), outputDirectory.toPath(), basePackage, rootPath, rootName,
                ignore, superInterface, mappings, lombok, splitModel, pins);
    }
}
