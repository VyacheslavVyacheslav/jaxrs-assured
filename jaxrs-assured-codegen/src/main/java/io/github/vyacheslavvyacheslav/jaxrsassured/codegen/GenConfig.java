package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Настройки генерации: вход спеки, выход, пакеты и корень дерева путей.
 * Пакет ресурсов — basePackage + ".resources"; группа пути — подпакет по первому сегменту;
 * пакет DTO — basePackage + ".model". superInterface — необязательный FQN супер-интерфейса
 * ресурсов, параметризуется самим интерфейсом (Base&lt;Self&gt;); null — интерфейсы без предка.
 * importMappings: схема → FQN внешнего класса — такие схемы не генерируются.
 * lombok — DTO на аннотациях Lombok вместо написанных генератором методов (API тот же, см. DtoStyle).
 * splitModel — DTO по подпакетам групп путей (model.&lt;группа&gt;, см. ModelPackages); modelPackages —
 * схема → группа, закрепляет пакет вручную ("" — корень model), только вместе со splitModel.
 * ignoreHeaders — заголовки, которые не становятся параметрами методов: их ставят фильтры клиента
 * (Authorization — BearerAuthFilter) или setHeader. Имена без учета регистра, как в HTTP.
 * typeOverrides — переопределения типов числовых полей DTO ({@code *.id=Long}, см. TypeOverrideRule); порядок важен:
 * из совпавших правил действует последнее.
 */
public record GenConfig(Path specFile, Path outDir, String basePackage, String rootPath,
                        String rootClassName, List<Pattern> ignorePaths, String superInterface,
                        Map<String, String> importMappings, boolean lombok, boolean splitModel,
                        Map<String, String> modelPackages, Set<String> ignoreHeaders,
                        List<TypeOverrideRule> typeOverrides) {

    public GenConfig {
        ignorePaths = List.copyOf(ignorePaths);
        importMappings = Map.copyOf(importMappings);
        modelPackages = Map.copyOf(modelPackages);
        ignoreHeaders = ignoreHeaders.stream().map(GenConfig::headerKey).collect(Collectors.toUnmodifiableSet());
        typeOverrides = List.copyOf(typeOverrides);
    }

    /** Без переопределения типов. */
    public GenConfig(Path specFile, Path outDir, String basePackage, String rootPath, String rootClassName,
                     List<Pattern> ignorePaths, String superInterface, Map<String, String> importMappings,
                     boolean lombok, boolean splitModel, Map<String, String> modelPackages, Set<String> ignoreHeaders) {
        this(specFile, outDir, basePackage, rootPath, rootClassName, ignorePaths, superInterface, importMappings,
                lombok, splitModel, modelPackages, ignoreHeaders, List.of());
    }

    /** Без пропуска заголовков. */
    public GenConfig(Path specFile, Path outDir, String basePackage, String rootPath, String rootClassName,
                     List<Pattern> ignorePaths, String superInterface, Map<String, String> importMappings,
                     boolean lombok, boolean splitModel, Map<String, String> modelPackages) {
        this(specFile, outDir, basePackage, rootPath, rootClassName, ignorePaths, superInterface, importMappings,
                lombok, splitModel, modelPackages, Set.of());
    }

    /** Без Lombok, model плоский — умолчания CLI. */
    public GenConfig(Path specFile, Path outDir, String basePackage, String rootPath, String rootClassName,
                     List<Pattern> ignorePaths, String superInterface, Map<String, String> importMappings) {
        this(specFile, outDir, basePackage, rootPath, rootClassName, ignorePaths, superInterface, importMappings,
                false, false, Map.of());
    }

    public String resourcesPackage() {
        return basePackage + ".resources";
    }

    public String dtoPackage() {
        return basePackage + ".model";
    }

    public boolean ignored(String path) {
        return ignorePaths.stream().anyMatch(p -> p.matcher(path).matches());
    }

    /** Заголовок из ignoreHeaders — без учета регистра. */
    public boolean ignoredHeader(String name) {
        return ignoreHeaders.contains(headerKey(name));
    }

    static String headerKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
