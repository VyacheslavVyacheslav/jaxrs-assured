package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сборка: спека → реестр типов → модели DTO и интерфейсов → java-файлы.
 * <p>
 * Перед записью убираются исходники прошлых прогонов, но только свои: .java с пометкой
 * {@link #GENERATED_MARKER} внутри папки basePackage. Чужие файлы не удаляются и не перезаписываются —
 * даже если outDir указан по ошибке (src/main/java, корень проекта).
 */
public final class Generator {

    /** Пометка в javadoc каждого сгенерированного типа верхнего уровня — по ней генератор узнаёт свои файлы. */
    static final String GENERATED_MARKER = "Сгенерировано jaxrs-assured-codegen из OpenAPI-спеки — не редактировать вручную.";

    private static final Logger log = LoggerFactory.getLogger(Generator.class);

    private Generator() {
    }

    public static GenerationResult run(GenConfig config) {
        OpenAPI spec = SpecLoader.load(config.specFile());
        PathTree tree = PathTree.of(spec);
        SchemaTypes types = new SchemaTypes(config, spec);
        DtoModelBuilder dtoBuilder = new DtoModelBuilder(types);
        List<DtoModel> dtos = dtoBuilder.build(spec);
        ResourceModelBuilder builder = new ResourceModelBuilder(config, types);
        List<ResourceModel> models = builder.build(tree);
        types.warnings().forEach(warning -> log.warn("codegen: {}", warning));
        dtoBuilder.warnings().forEach(warning -> log.warn("codegen: {}", warning));
        builder.warnings().forEach(warning -> log.warn("codegen: {}", warning));

        List<Path> targets = new ArrayList<>();
        dtos.forEach(dto -> targets.add(sourceFile(config.outDir(), dto.packageName(), dto.className())));
        models.forEach(model -> targets.add(sourceFile(config.outDir(), model.packageName(), model.className())));
        requireNoForeignTargets(targets);
        cleanGenerated(config.outDir().resolve(config.basePackage().replace('.', '/')));
        try {
            new DtoWriter(DtoStyle.of(config)).write(dtos, config.outDir());
            new ResourceWriter(config).write(models, config.outDir());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать исходники в " + config.outDir(), e);
        }
        return new GenerationResult(models.size(), dtos.size());
    }

    /** Файл с пометкой codegen — значит, его создал генератор, и его можно удалить или перезаписать. */
    static boolean isGenerated(Path file) {
        if (!file.getFileName().toString().endsWith(".java")) {
            return false;
        }
        try {
            return Files.readString(file).contains(GENERATED_MARKER);
        } catch (IOException e) {
            // не UTF-8, не читается, ссылка на папку — точно не наш файл
            return false;
        }
    }

    /** Тот же путь, по которому JavaFile.writeTo кладёт исходник. */
    private static Path sourceFile(Path outDir, String packageName, String className) {
        return outDir.resolve(packageName.replace('.', '/')).resolve(className + ".java");
    }

    /** Проверка до любых удалений: на месте будущего исходника не должно лежать чужого файла. */
    private static void requireNoForeignTargets(List<Path> targets) {
        List<Path> foreign = targets.stream()
                .filter(Files::exists)
                .filter(path -> !isGenerated(path))
                .toList();
        if (!foreign.isEmpty()) {
            throw new IllegalArgumentException("Файлы созданы не codegen, перезаписывать не буду (проверьте outDir "
                    + "и basePackage): " + foreign);
        }
    }

    /**
     * Удаляет сгенерированные .java внутри папки basePackage (так уходят устаревшие классы прошлых прогонов),
     * затем опустевшие папки. Остальное не трогает.
     */
    private static void cleanGenerated(Path packageDir) {
        if (!Files.isDirectory(packageDir)) {
            return;
        }
        List<Path> kept = new ArrayList<>();
        try (var paths = Files.walk(packageDir)) {
            // обратный порядок: содержимое папки раньше самой папки
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(path)) {
                    if (isEmptyDirectory(path)) {
                        Files.delete(path);
                    }
                } else if (isGenerated(path)) {
                    Files.delete(path);
                } else {
                    kept.add(path);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось очистить " + packageDir, e);
        }
        if (!kept.isEmpty()) {
            log.warn("codegen: в {} оставлены файлы, созданные не генератором: {}", packageDir, kept);
        }
    }

    private static boolean isEmptyDirectory(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    public record GenerationResult(int interfaces, int dtos) {
    }
}
