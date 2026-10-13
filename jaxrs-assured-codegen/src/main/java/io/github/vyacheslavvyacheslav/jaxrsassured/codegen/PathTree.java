package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Дерево путей спеки: сегмент пути — узел, HTTP-операции — на узле полного пути.
 * Основа генерации: узел с операциями и/или потомками становится интерфейсом, потомки — локаторами подресурсов.
 */
public final class PathTree {

    private final Node root;

    private PathTree(Node root) {
        this.root = root;
    }

    public static PathTree of(OpenAPI spec) {
        Paths paths = spec.getPaths();
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("В спеке нет путей");
        }
        Node root = new Node("", "");
        paths.forEach((path, item) -> {
            Node current = root;
            for (String segment : path.split("/")) {
                if (!segment.isEmpty()) {
                    current = current.child(segment);
                }
            }
            current.item = item;
        });
        return new PathTree(root);
    }

    public Node root() {
        return root;
    }

    /**
     * Узел по полному пути вида /api/v1/deliveries/statuses, null — если такого пути не было.
     */
    public Node find(String fullPath) {
        Node current = root;
        for (String segment : fullPath.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = current.children.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Потенциальных интерфейсов: все узлы, кроме служебного корня.
     */
    public int interfaceCount() {
        return countNodes(root) - 1;
    }

    public long operationCount() {
        return operations(root);
    }

    public void print(PrintStream out) {
        print(root, out);
    }

    private void print(Node node, PrintStream out) {
        for (Node child : node.children.values()) {
            out.println(child.describe());
            print(child, out);
        }
    }

    private static int countNodes(Node node) {
        int total = 1;
        for (Node child : node.children.values()) {
            total += countNodes(child);
        }
        return total;
    }

    private static long operations(Node node) {
        long total = node.item == null ? 0 : node.item.readOperationsMap().size();
        for (Node child : node.children.values()) {
            total += operations(child);
        }
        return total;
    }

    public static final class Node {

        private final String name;
        private final String fullPath;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private PathItem item;

        private Node(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
        }

        public String name() {
            return name;
        }

        public String fullPath() {
            return fullPath;
        }

        public PathItem item() {
            return item;
        }

        public Map<String, Node> children() {
            return children;
        }

        public int childrenCount() {
            return children.size();
        }

        public Set<String> httpMethods() {
            return item == null ? Set.of()
                    : item.readOperationsMap().keySet().stream()
                            .map(Enum::name)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private Node child(String segment) {
            return children.computeIfAbsent(segment,
                    s -> new Node(s, fullPath.isEmpty() ? "/" + s : fullPath + "/" + s));
        }

        private String describe() {
            String methods = String.join(",", httpMethods());
            return "- " + name + (methods.isEmpty() ? "" : " [" + methods + "]");
        }
    }
}
