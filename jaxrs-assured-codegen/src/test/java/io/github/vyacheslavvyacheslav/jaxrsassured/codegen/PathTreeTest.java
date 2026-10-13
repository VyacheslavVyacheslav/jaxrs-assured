package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTreeTest {

    @Test
    void buildsTreeFromPaths() {
        PathTree tree = PathTree.of(new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/deliveries/statuses", new PathItem().get(op("getDeliveryStatuses")))
                .addPathItem("/api/v1/deliveries/statuses/{id}", new PathItem().get(op("getDeliveryStatus")))
                .addPathItem("/api/v1/drivers", new PathItem().get(op("getDrivers")))));

        PathTree.Node statuses = tree.find("/api/v1/deliveries/statuses");
        assertNotNull(statuses);
        assertEquals(Set.of("GET"), statuses.httpMethods());

        assertNotNull(tree.find("/api/v1/deliveries/statuses/{id}"));
        assertNotNull(tree.find("/api/v1/drivers"));
        assertNull(tree.find("/api/v1/absent"));

        assertEquals(6, tree.interfaceCount());
        assertEquals(3, tree.operationCount());
    }

    @Test
    void sharedPrefixPathsReuseNodes() {
        PathTree tree = PathTree.of(new OpenAPI().paths(new Paths()
                .addPathItem("/a/b", new PathItem().get(op("op1")))
                .addPathItem("/a/c", new PathItem().post(op("op2")))));

        PathTree.Node a = tree.find("/a");
        assertNotNull(a);
        assertEquals(2, a.childrenCount());
        assertTrue(a.httpMethods().isEmpty(), "узел-префикс без собственных операций");
        assertEquals(2, tree.operationCount());
    }

    @Test
    void operationsOnMiddleNodeAreKept() {
        // /a и /a/b — ручки на обоих уровнях: узел /a и интерфейс, и носитель операций
        PathTree tree = PathTree.of(new OpenAPI().paths(new Paths()
                .addPathItem("/a", new PathItem().get(op("list")))
                .addPathItem("/a/b", new PathItem().get(op("getB")))));

        assertEquals(Set.of("GET"), tree.find("/a").httpMethods());
        assertEquals(2, tree.operationCount());
    }

    @Test
    void emptySpecIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PathTree.of(new OpenAPI()));
    }

    private static Operation op(String operationId) {
        return new Operation().operationId(operationId);
    }
}
