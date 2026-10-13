package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.Locale;
import java.util.Set;

/**
 * Правила именования сгенерированного кода.
 */
public final class Naming {

    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "yield", "sealed", "permits");

    private Naming() {
    }

    /** delivery-statuses → DeliveryStatuses */
    public static String pascal(String kebab) {
        StringBuilder result = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    /** Pascal с сохранением границ camelCase: locationId → LocationId, transport-company-id → TransportCompanyId. */
    public static String pascalParam(String value) {
        String kebab = value.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
        StringBuilder result = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    /** DeliveryStatuses → deliveryStatuses */
    public static String camel(String pascal) {
        return pascal.isEmpty() ? pascal : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** Имя поля DTO: transport_company_id → transportCompanyId, opusId → opusId (горбы сохраняются). */
    public static String fieldName(String jsonName) {
        return camel(pascalParam(jsonName.replace('_', '-')));
    }

    /** Константа энума: shutting_down → SHUTTING_DOWN, IN_ROUTE → IN_ROUTE, e_book → E_BOOK. */
    public static String enumConstant(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            result.append(Character.isLetterOrDigit(c) ? Character.toUpperCase(c) : '_');
        }
        return result.toString();
    }

    /**
     * Имя параметра с сохранением горбов: X-Actor → xActor, deliveryDateStart → deliveryDateStart,
     * transport_company_id → transportCompanyId, filter[status] → filterStatus; ключевое слово
     * или цифра в начале → с префиксом _ (class → _class).
     */
    public static String parameter(String raw) {
        String name = camel(pascalParam(raw.replaceAll("[^A-Za-z0-9]+", "-")));
        return isUsableIdentifier(name) ? name : "_" + name;
    }

    /**
     * Наивное единственное число: statuses→status, drivers→driver, deliveries→delivery,
     * settings→setting. Достаточно для имён интерфейсов; спорные случаи — коллизии, их ловит реестр имён.
     */
    public static String singular(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("zes")
                || w.endsWith("ches") || w.endsWith("shes")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    public static boolean isIdentifier(String s) {
        if (s == null || s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Сегмент пути → подпакет группы (resources.&lt;группа&gt;, model.&lt;группа&gt;): daily-demands → dailydemands,
     * {tenant} → tenant; ключевое слово или цифра в начале → с префиксом _ (import → _import).
     */
    public static String packageSegment(String segment) {
        String name = segment.replaceAll("[^A-Za-z0-9_]", "");
        return isUsableIdentifier(name) ? name : "_" + name;
    }

    /** Группа для закрепления пакета схемы: сегмент пути (drivers, daily-demands) либо "" — корень model. */
    public static boolean isGroupSegment(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]*");
    }

    /** Имя через точки, каждый сегмент — java-идентификатор (пакет или FQN класса). */
    public static boolean isQualifiedName(String name) {
        if (name == null || name.isBlank() || name.startsWith(".") || name.endsWith(".")) {
            return false;
        }
        for (String segment : name.split("\\.", -1)) {
            if (!isIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    /** Идентификатор, безопасный для имени поля/константы: лексически валиден и не ключевое слово. */
    public static boolean isUsableIdentifier(String s) {
        return isIdentifier(s) && !RESERVED.contains(s);
    }
}
