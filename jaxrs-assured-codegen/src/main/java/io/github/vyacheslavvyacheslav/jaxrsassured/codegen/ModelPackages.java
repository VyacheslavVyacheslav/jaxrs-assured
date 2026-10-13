package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Пакет класса DTO для схемы components.
 * <p>
 * По умолчанию все DTO — в basePackage.model. С splitModel — по группам путей, как ресурсы:
 * схема, до которой через $ref (транзитивно, через другие схемы) доходят ручки одной группы, живёт в
 * model.&lt;группа&gt; — то же имя, что у resources.&lt;группа&gt;; нужная нескольким группам или никому —
 * в корне model. modelPackages закрепляет пакет схемы вручную: группа или "" — корень.
 * <p>
 * И классы, и ссылки на них берут пакет отсюда, поэтому ошибка распределения сдвигает класс
 * в другой пакет, но не ломает компиляцию; имена схем уникальны — конфликтов имён в пакетах нет.
 */
final class ModelPackages {

    private final String rootPackage;
    private final Map<String, String> packages = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private ModelPackages(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    static ModelPackages of(GenConfig config, OpenAPI spec) {
        ModelPackages result = new ModelPackages(config.dtoPackage());
        if (!config.splitModel()) {
            return result;
        }
        Map<String, Schema> schemas = spec.getComponents() == null || spec.getComponents().getSchemas() == null
                ? Map.of() : spec.getComponents().getSchemas();
        for (Map.Entry<String, Set<String>> entry : groupsBySchema(config, spec, schemas).entrySet()) {
            Set<String> groups = entry.getValue();
            String group = groups.iterator().next();
            if (groups.size() == 1 && !group.isEmpty()) {
                result.packages.put(entry.getKey(), result.groupPackage(group));
            }
        }
        for (Map.Entry<String, String> pin : config.modelPackages().entrySet()) {
            if (!schemas.containsKey(pin.getKey())) {
                result.warnings.add("Схема " + pin.getKey() + " из modelPackages не найдена в спеке — закрепление пропущено");
                continue;
            }
            if (pin.getValue().isEmpty()) {
                result.packages.remove(pin.getKey());
            } else {
                result.packages.put(pin.getKey(), result.groupPackage(pin.getValue()));
            }
        }
        return result;
    }

    String packageOf(String schemaName) {
        return packages.getOrDefault(schemaName, rootPackage);
    }

    List<String> warnings() {
        return warnings;
    }

    private String groupPackage(String group) {
        return rootPackage + "." + Naming.packageSegment(group);
    }

    /**
     * Схема → группы путей, из которых она достижима. Группа — первый сегмент под rootPath, как у ресурсов;
     * ручки на самом rootPath дают группу "" — их схемы уходят в корень model.
     */
    private static Map<String, Set<String>> groupsBySchema(GenConfig config, OpenAPI spec, Map<String, Schema> schemas) {
        Map<String, Set<String>> result = new HashMap<>();
        PathTree.Node root = PathTree.of(spec).find(config.rootPath());
        if (root == null) {
            return result;
        }
        if (root.item() != null) {
            reach("", refsOf(root.item()), schemas, result);
        }
        for (PathTree.Node group : root.children().values()) {
            Set<String> refs = new LinkedHashSet<>();
            collectSubtree(group, refs);
            reach(group.name(), refs, schemas, result);
        }
        return result;
    }

    private static void collectSubtree(PathTree.Node node, Set<String> refs) {
        if (node.item() != null) {
            refs.addAll(refsOf(node.item()));
        }
        for (PathTree.Node child : node.children().values()) {
            collectSubtree(child, refs);
        }
    }

    /** Обход по $ref от ручек группы; visited защищает от циклов ссылок между схемами. */
    private static void reach(String group, Set<String> start, Map<String, Schema> schemas,
                              Map<String, Set<String>> result) {
        Deque<String> queue = new ArrayDeque<>(start);
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            Schema<?> schema = schemas.get(name);
            if (schema == null || !visited.add(name)) {
                continue;
            }
            result.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(group);
            Set<String> refs = new LinkedHashSet<>();
            collect(schema, refs);
            queue.addAll(refs);
        }
    }

    /** Схемы, на которые ссылаются ручки пути: параметры, тела, ответы. */
    private static Set<String> refsOf(PathItem item) {
        Set<String> refs = new LinkedHashSet<>();
        collectParameters(item.getParameters(), refs);
        for (Operation operation : item.readOperations()) {
            collectParameters(operation.getParameters(), refs);
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                for (MediaType mediaType : operation.getRequestBody().getContent().values()) {
                    collect(mediaType == null ? null : mediaType.getSchema(), refs);
                }
            }
            if (operation.getResponses() != null) {
                for (ApiResponse response : operation.getResponses().values()) {
                    if (response != null && response.getContent() != null) {
                        for (MediaType mediaType : response.getContent().values()) {
                            collect(mediaType == null ? null : mediaType.getSchema(), refs);
                        }
                    }
                }
            }
        }
        return refs;
    }

    private static void collectParameters(List<Parameter> parameters, Set<String> refs) {
        if (parameters != null) {
            parameters.forEach(parameter -> collect(parameter.getSchema(), refs));
        }
    }

    /** Имена схем по $ref внутри инлайн-схемы: свойства, items, allOf/oneOf/anyOf, additionalProperties, not. */
    private static void collect(Schema<?> schema, Set<String> refs) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            refs.add(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
            return;
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> collect(property, refs));
        }
        collect(schema.getItems(), refs);
        for (List<Schema> branches : List.of(nonNull(schema.getAllOf()), nonNull(schema.getOneOf()),
                nonNull(schema.getAnyOf()))) {
            branches.forEach(branch -> collect(branch, refs));
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            collect(additional, refs);
        }
        collect(schema.getNot(), refs);
    }

    private static List<Schema> nonNull(List<Schema> list) {
        return list == null ? List.of() : list;
    }
}
