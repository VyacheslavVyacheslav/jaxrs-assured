package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Дерево путей → модели интерфейсов.
 * <p>
 * Правила:
 * <ul>
 *     <li>корень — общий префикс путей (rootPath), class-level @Path на нём;</li>
 *     <li>первый сегмент под корнем — группа: подпакет resources.&lt;сегмент&gt;, локатор на корне;</li>
 *     <li>имя интерфейса: группа → Pascal(сегмент); статический потомок → база родителя + Pascal(сегмент);
 *         сегмент-параметр {id} → Pascal(singular(родительского сегмента)); прочие параметры ({locationId})
 *         → Pascal(параметра) — у узла бывает несколько параметризованных потомков;</li>
 *     <li>{param} без детей — ручки инлайнятся в родительский интерфейс с @Path("{param}");</li>
 *     <li>{param} с детьми — локатор с @PathParam; тип — общий для ручек поддерева (разные числовые — Number),
 *         при расхождении String;</li>
 *     <li>имя метода — operationId без префикса контроллера ("XxxController_findOne" → findOne),
 *         при коллизии в интерфейсе — полный operationId;</li>
 *     <li>header-параметры из ignoreHeaders (без учета регистра) не становятся параметрами метода —
 *         их ставят фильтры клиента или setHeader;</li>
 *     <li>тело — параметр-DTO и @Consumes; необязательное тело (requestBody.required false или не указан —
 *         по стандарту OpenAPI это false) — ещё и перегрузка без тела и без @Consumes;</li>
 *     <li>ответ — первый media type минимального 2xx: text/event-stream → InputStream (поток не кончается,
 *         String ждал бы его конца вечно), string/binary → byte[] (String испортил бы zip), иначе тип схемы;
 *         не-JSON ответ → @Produces с этим media type.</li>
 * </ul>
 */
final class ResourceModelBuilder {

    private static final Set<String> SUPPORTED_HTTP = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final TypeName INPUT_STREAM = ClassName.get(InputStream.class);
    private static final TypeName STRING = ClassName.get(String.class);
    /** Числовые типы параметров: у локатора их смесь сводится к Number. */
    private static final Set<TypeName> NUMERIC = Set.of(TypeName.INT.box(), TypeName.LONG.box(),
            TypeName.DOUBLE.box(), TypeName.FLOAT.box(), SchemaTypes.NUMBER);

    private final GenConfig config;
    private final SchemaTypes types;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Set<String>> takenNames = new HashMap<>();
    /** Заголовки из ignoreHeaders, которые встретились в ручках: остальные — повод для WARN (опечатка?). */
    private final Set<String> matchedIgnoredHeaders = new HashSet<>();

    ResourceModelBuilder(GenConfig config, SchemaTypes types) {
        this.config = config;
        this.types = types;
    }

    List<String> warnings() {
        return warnings;
    }

    List<ResourceModel> build(PathTree tree) {
        checkTopLevels(tree);
        PathTree.Node root = root(tree);
        List<ResourceModel> models = new ArrayList<>();
        List<LocatorModel> rootLocators = new ArrayList<>();

        for (PathTree.Node top : root.children().values()) {
            String groupPackage = config.resourcesPackage() + "." + Naming.packageSegment(top.name());
            String groupClass = Naming.pascal(top.name()) + "Resource";
            rootLocators.add(new LocatorModel("get" + groupClass, top.name(), null, null, groupPackage, groupClass));
            interfaceOf(top, groupPackage, Naming.pascal(top.name()), true, models);
        }

        models.add(0, new ResourceModel(config.basePackage(), config.rootClassName(),
                config.rootPath(), List.of(), rootLocators));
        config.ignoreHeaders().stream()
                .filter(header -> !matchedIgnoredHeaders.contains(header))
                .sorted()
                .forEach(header -> warnings.add("Заголовок " + header + " из ignoreHeaders (--ignore-header) "
                        + "не встретился ни в одной ручке"));
        return models;
    }

    private PathTree.Node root(PathTree tree) {
        PathTree.Node current = tree.root();
        for (String segment : config.rootPath().split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = current.children().get(segment);
            if (current == null) {
                throw new IllegalArgumentException("В спеке нет пути-префикса /" + config.rootPath());
            }
        }
        return current;
    }

    /** Пути вне rootPath терять молча нельзя: либо --ignore, либо ошибка. */
    private void checkTopLevels(PathTree tree) {
        String firstSegment = config.rootPath().split("/")[0];
        for (PathTree.Node top : tree.root().children().values()) {
            if (!top.name().equals(firstSegment) && !config.ignored(top.fullPath())) {
                throw new IllegalArgumentException("Путь вне rootPath /" + config.rootPath() + ": "
                        + top.fullPath() + " — добавьте шаблон в --ignore");
            }
        }
    }

    /**
     * Строит интерфейс узла; модели поддерева (дети раньше родителя) складываются в models,
     * возвращает модель самого узла — она нужна локатору родителя.
     * ownBase — накопленное имя без суффикса Resource; потомкам группы достаётся
     * Pascal(singular(сегмент группы)), потомкам остальных узлов — ownBase родителя.
     */
    private ResourceModel interfaceOf(PathTree.Node node, String packageName, String ownBase, boolean groupRoot,
                                      List<ResourceModel> models) {
        String className = ownBase + "Resource";
        register(packageName, className, node.fullPath());

        Set<String> usedNames = new HashSet<>();
        List<OperationModel> operations = new ArrayList<>(operationsOf(node, null, usedNames));
        List<LocatorModel> locators = new ArrayList<>();
        String childrenBase = groupRoot ? Naming.pascal(Naming.singular(node.name())) : ownBase;

        for (PathTree.Node child : node.children().values()) {
            boolean childIsParam = child.name().startsWith("{");
            if (childIsParam && child.childrenCount() == 0) {
                operations.addAll(operationsOf(child, child.name(), usedNames));
                continue;
            }
            String childBase = childIsParam
                    ? paramChildBase(node, child)
                    : childrenBase + Naming.pascal(child.name());
            ResourceModel childModel = interfaceOf(child, packageName, childBase, false, models);
            String locatorName = childIsParam
                    ? "get" + childBase + "Resource"
                    : "get" + Naming.pascal(child.name()) + "Resource";
            String pathParam = childIsParam ? child.name().substring(1, child.name().length() - 1) : null;
            locators.add(new LocatorModel(locatorName, child.name(), pathParam,
                    childIsParam ? locatorParamType(child, pathParam) : null,
                    childModel.packageName(), childModel.className()));
        }
        ResourceModel model = new ResourceModel(packageName, className, null, operations, locators);
        models.add(model);
        return model;
    }

    /**
     * База имени интерфейса параметризованного узла: {id} — от родительского сегмента
     * (locations/{id} → Location), прочие — от имени параметра (locations/{locationId} → LocationId):
     * у одного узла бывает несколько параметризованных потомков с детьми.
     */
    private String paramChildBase(PathTree.Node parent, PathTree.Node child) {
        String param = child.name().substring(1, child.name().length() - 1);
        return "id".equals(param)
                ? Naming.pascal(Naming.singular(parent.name()))
                : Naming.pascalParam(param);
    }

    /**
     * Тип параметра локатора {param}. В OpenAPI тип параметра пути объявляет каждая ручка, а локатор обслуживает
     * все ручки своего поддерева: тип берётся, если все объявившие его ручки согласны (number → Number,
     * $ref на энум → энум — как у параметров ручек). Разные числовые (integer в одной ручке, number в другой) —
     * Number: он принимает любое из них. Разошлись иначе — String и WARN; ни одна не объявила — String.
     */
    private TypeName locatorParamType(PathTree.Node node, String param) {
        Map<TypeName, List<String>> declared = new LinkedHashMap<>();
        collectPathParamTypes(node, param, declared);
        if (declared.size() == 1) {
            return declared.keySet().iterator().next();
        }
        if (declared.size() > 1 && NUMERIC.containsAll(declared.keySet())) {
            return SchemaTypes.NUMBER;
        }
        if (declared.size() > 1) {
            List<String> variants = new ArrayList<>();
            declared.forEach((type, operations) -> variants.add(simpleName(type) + ": " + String.join(", ", operations)));
            warnings.add("Параметр пути {" + param + "} в ручках под " + node.fullPath() + " объявлен с разными типами ("
                    + String.join("; ", variants) + ") — у локатора будет String");
        }
        return STRING;
    }

    /** Типы параметра param во всех ручках поддерева node: тип → ручки, где он так объявлен. */
    private void collectPathParamTypes(PathTree.Node node, String param, Map<TypeName, List<String>> declared) {
        if (node.item() != null) {
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : node.item().readOperationsMap().entrySet()) {
                Parameter parameter = pathParameter(node.item(), entry.getValue(), param);
                if (parameter != null) {
                    declared.computeIfAbsent(types.paramType(parameter.getSchema()), type -> new ArrayList<>())
                            .add(entry.getKey().name() + " " + node.fullPath());
                }
            }
        }
        for (PathTree.Node child : node.children().values()) {
            collectPathParamTypes(child, param, declared);
        }
    }

    /** Объявление параметра пути в ручке; объявление на самой ручке важнее общего для пути (parameters у path item). */
    private static Parameter pathParameter(PathItem item, Operation operation, String name) {
        Parameter own = pathParameter(operation.getParameters(), name);
        return own != null ? own : pathParameter(item.getParameters(), name);
    }

    private static Parameter pathParameter(List<Parameter> parameters, String name) {
        if (parameters == null) {
            return null;
        }
        return parameters.stream()
                .filter(parameter -> "path".equals(parameter.getIn()) && name.equals(parameter.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String simpleName(TypeName type) {
        return type instanceof ClassName className ? className.simpleName() : type.toString();
    }

    /** Ручки узла; с inlinePath ({param}-лист) ручки уходят в родителя с @Path("{param}"). */
    private List<OperationModel> operationsOf(PathTree.Node node, String inlinePath, Set<String> usedNames) {
        if (node.item() == null) {
            return List.of();
        }
        List<OperationModel> result = new ArrayList<>();
        for (Map.Entry<PathItem.HttpMethod, Operation> entry : node.item().readOperationsMap().entrySet()) {
            Operation operation = entry.getValue();
            String http = entry.getKey().name();
            if (!SUPPORTED_HTTP.contains(http)) {
                warnings.add("HTTP-метод не поддержан, ручка пропущена: " + http + " " + node.fullPath());
                continue;
            }
            List<ParamModel> params = paramsOf(operation, inlinePath, node);
            List<ParamModel> withoutBody = uniqueVarNames(params);
            String mediaType = consumes(operation);
            if (mediaType != null) {
                params = new ArrayList<>(params);
                params.add(new ParamModel(ParamModel.In.BODY, "body", bodyVar(operation, mediaType),
                        bodyType(operation, mediaType)));
            }
            Response response = response(operation, node);
            String path = inlinePath == null ? "" : inlinePath;
            String methodName = methodName(operation, http, node, usedNames);
            result.add(new OperationModel(http, path, methodName, uniqueVarNames(params), mediaType,
                    response.produces(), response.type()));
            if (mediaType != null && !Boolean.TRUE.equals(operation.getRequestBody().getRequired())) {
                // тело необязательно: перегрузка без тела и без @Consumes — запрос без тела уйдёт без Content-Type
                result.add(new OperationModel(http, path, methodName, withoutBody, null,
                        response.produces(), response.type()));
            }
        }
        return result;
    }

    /**
     * Имена java-параметров в методе уникальны: разные параметры спеки дают одно имя
     * (header Last-Event-ID и query lastEventId → lastEventId) — второму достаётся суффикс места
     * (lastEventIdQuery), при повторе — ещё и номер.
     */
    private List<ParamModel> uniqueVarNames(List<ParamModel> params) {
        Set<String> used = new HashSet<>();
        List<ParamModel> result = new ArrayList<>();
        for (ParamModel param : params) {
            String varName = param.varName();
            if (!used.add(varName)) {
                String base = varName + Naming.pascal(param.in().name());
                varName = base;
                for (int i = 2; !used.add(varName); i++) {
                    varName = base + i;
                }
            }
            result.add(varName.equals(param.varName()) ? param
                    : new ParamModel(param.in(), param.paramName(), varName, param.typeName()));
        }
        return List.copyOf(result);
    }

    private List<ParamModel> paramsOf(Operation operation, String inlinePath, PathTree.Node node) {
        List<ParamModel> params = new ArrayList<>();
        if (operation.getParameters() == null) {
            return params;
        }
        for (Parameter parameter : operation.getParameters()) {
            switch (parameter.getIn() == null ? "" : parameter.getIn()) {
                case "path" -> {
                    if (inlinePath != null) {
                        params.add(new ParamModel(ParamModel.In.PATH, parameter.getName(),
                                Naming.parameter(parameter.getName()), types.paramType(parameter.getSchema())));
                    }
                    // без инлайна параметр пути уже съеден локаторами родителей
                }
                case "query" -> params.add(new ParamModel(ParamModel.In.QUERY, parameter.getName(),
                        Naming.parameter(parameter.getName()), types.paramType(parameter.getSchema())));
                case "header" -> {
                    if (config.ignoredHeader(parameter.getName())) {
                        // заголовок ставит фильтр клиента или setHeader — параметром метода не нужен
                        matchedIgnoredHeaders.add(GenConfig.headerKey(parameter.getName()));
                    } else {
                        params.add(new ParamModel(ParamModel.In.HEADER, parameter.getName(),
                                Naming.parameter(parameter.getName()), types.paramType(parameter.getSchema())));
                    }
                }
                case "cookie" -> warnings.add("Cookie-параметр пропущен: " + parameter.getName()
                        + " (" + node.fullPath() + ")");
                default -> warnings.add("Параметр без in пропущен: " + parameter.getName()
                        + " (" + node.fullPath() + ")");
            }
        }
        return params;
    }

    /** operationId "XxxController_findOne" → findOne; при коллизии в интерфейсе — полный operationId. */
    private String methodName(Operation operation, String http, PathTree.Node node, Set<String> used) {
        String operationId = operation.getOperationId();
        String candidate = null;
        if (operationId != null && operationId.indexOf('_') >= 0) {
            String tail = operationId.substring(operationId.indexOf('_') + 1);
            if (Naming.isIdentifier(tail)) {
                candidate = tail;
            }
        }
        if (candidate == null && operationId != null && Naming.isIdentifier(operationId)) {
            candidate = Naming.camel(operationId);
        }
        if (candidate == null) {
            candidate = http.toLowerCase(Locale.ROOT) + Naming.pascal(lastSegment(node));
            warnings.add("operationId отсутствует у " + http + " " + node.fullPath()
                    + " — имя метода " + candidate);
        }
        if (used.contains(candidate) && operationId != null && Naming.isIdentifier(operationId)
                && !used.contains(operationId)) {
            candidate = operationId;
        }
        if (used.contains(candidate)) {
            throw new IllegalArgumentException("Коллизия имени метода " + candidate + " в " + node.fullPath());
        }
        used.add(candidate);
        return candidate;
    }

    private String lastSegment(PathTree.Node node) {
        String fullPath = node.fullPath();
        return fullPath.substring(fullPath.lastIndexOf('/') + 1);
    }

    private String consumes(Operation operation) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return null;
        }
        return requestBody.getContent().keySet().iterator().next();
    }

    /** Имя параметра тела из $ref схемы (SyncDriversDto → syncDriversDto), иначе body. */
    private String bodyVar(Operation operation, String mediaType) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return "body";
        }
        var mediaTypeItem = requestBody.getContent().get(mediaType);
        Schema<?> schema = mediaTypeItem == null ? null : mediaTypeItem.getSchema();
        if (schema != null && schema.get$ref() != null) {
            String ref = schema.get$ref();
            String var = Naming.camel(ref.substring(ref.lastIndexOf('/') + 1));
            if (Naming.isIdentifier(var)) {
                return var;
            }
        }
        return "body";
    }

    /**
     * Ответ минимального 2xx: тип (DTO, обёртка выборки, примитив, byte[] для binary, InputStream для SSE)
     * и @Produces для не-JSON media type; пустой 2xx или без 2xx → void.
     */
    private Response response(Operation operation, PathTree.Node node) {
        if (operation.getResponses() == null) {
            return Response.NONE;
        }
        Integer best = null;
        for (String code : operation.getResponses().keySet()) {
            try {
                int value = Integer.parseInt(code);
                if (value >= 200 && value < 300 && (best == null || value < best)) {
                    best = value;
                }
            } catch (NumberFormatException ignored) {
                // default и прочие нечисловые коды не задают возвращаемый тип
            }
        }
        if (best == null) {
            warnings.add("Нет 2xx-ответа, ручка вернёт void: " + node.fullPath());
            return Response.NONE;
        }
        var response = operation.getResponses().get(String.valueOf(best));
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return Response.NONE;
        }
        var content = response.getContent().entrySet().iterator().next();
        // "text/html; charset=utf-8" → "text/html": параметры media type в Accept не нужны
        String mediaType = content.getKey().split(";", 2)[0].trim();
        String produces = isJson(mediaType) || "*/*".equals(mediaType) ? null : mediaType;
        if ("text/event-stream".equals(mediaType)) {
            return new Response(INPUT_STREAM, produces);
        }
        Schema<?> schema = content.getValue() == null ? null : content.getValue().getSchema();
        return new Response(types.responseType(schema), produces);
    }

    private static boolean isJson(String mediaType) {
        return "application/json".equals(mediaType) || mediaType.endsWith("+json");
    }

    /** Тип ответа ручки и media type для @Produces (null — JSON или ответа нет). */
    private record Response(TypeName type, String produces) {
        static final Response NONE = new Response(TypeName.VOID, null);
    }

    /** Тип тела запроса: $ref → DTO, инлайн-объект/безтиповые → JsonNode (решает SchemaTypes). */
    private TypeName bodyType(Operation operation, String mediaType) {
        var mediaTypeItem = operation.getRequestBody().getContent().get(mediaType);
        Schema<?> schema = mediaTypeItem == null ? null : mediaTypeItem.getSchema();
        return types.fieldType(schema);
    }

    private void register(String packageName, String className, String fullPath) {
        Set<String> names = takenNames.computeIfAbsent(packageName, k -> new HashSet<>());
        if (!names.add(className)) {
            throw new IllegalArgumentException("Коллизия имени интерфейса " + packageName + "." + className
                    + " (путь " + fullPath + ")");
        }
    }
}
