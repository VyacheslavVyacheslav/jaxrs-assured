package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeOverrideRuleTest {

    @Test
    void starMatchesAnyCharactersInSchemaAndProperty() {
        TypeOverrideRule anyId = TypeOverrideRule.parse("*.id=Long");
        assertTrue(anyId.matches("RouteDto", "id"));
        assertFalse(anyId.matches("RouteDto", "routeId"), "Шаблон — на имя целиком");

        TypeOverrideRule suffix = TypeOverrideRule.parse("*.*Id=Long");
        assertTrue(suffix.matches("RouteDto", "routeId"));
        assertFalse(suffix.matches("RouteDto", "id"), "Регистр важен: id не оканчивается на Id");

        TypeOverrideRule page = TypeOverrideRule.parse(" PageDto.*=Integer ");
        assertTrue(page.matches("PageDto", "total"));
        assertFalse(page.matches("PageDtoV2", "total"));
        assertEquals("java.lang.Integer", page.type().toString());
        assertEquals("PageDto.*=Integer", page.toString());
    }

    @Test
    void specialCharactersInNamesAreLiteral() {
        TypeOverrideRule rule = TypeOverrideRule.parse("Route$Dto.weight_kg=Double");

        assertTrue(rule.matches("Route$Dto", "weight_kg"));
        assertFalse(rule.matches("RouteXDto", "weight_kg"));
    }

    @Test
    void allowedTypesOnly() {
        assertEquals("java.math.BigDecimal", TypeOverrideRule.parse("*.price=BigDecimal").type().toString());
        assertEquals("java.lang.Double", TypeOverrideRule.parse("*.lat=Double").type().toString());
        assertThrows(IllegalArgumentException.class, () -> TypeOverrideRule.parse("*.id=Number"));
        assertThrows(IllegalArgumentException.class, () -> TypeOverrideRule.parse("*.id=long"));
        assertThrows(IllegalArgumentException.class, () -> TypeOverrideRule.parse("*.id=String"));
    }

    @Test
    void malformedRulesAreRejected() {
        for (String rule : new String[]{"", "*.id", "id=Long", "a.b.c=Long", ".id=Long", "Dto.=Long", "=Long"}) {
            assertThrows(IllegalArgumentException.class, () -> TypeOverrideRule.parse(rule), rule);
        }
        assertThrows(IllegalArgumentException.class, () -> TypeOverrideRule.parse(null));
    }
}
