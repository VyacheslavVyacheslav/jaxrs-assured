package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingTest {

    @Test
    void singularForms() {
        assertEquals("status", Naming.singular("statuses"));
        assertEquals("driver", Naming.singular("drivers"));
        assertEquals("delivery", Naming.singular("deliveries"));
        assertEquals("company", Naming.singular("companies"));
        assertEquals("setting", Naming.singular("settings"));
        assertEquals("address", Naming.singular("addresses"));
        assertEquals("tax", Naming.singular("taxes"));
        assertEquals("type", Naming.singular("types"));
    }

    @Test
    void pascalAndCamel() {
        assertEquals("DeliveryStatuses", Naming.pascal("delivery-statuses"));
        assertEquals("DeliveryStatuses", Naming.pascal("delivery-STATUSes"));
        assertEquals("deliveryStatuses", Naming.camel("DeliveryStatuses"));
        assertEquals("xActor", Naming.parameter("X-Actor"));
        assertEquals("ids", Naming.parameter("ids"));
    }

    @Test
    void parameterKeepsCamelHumps() {
        assertEquals("deliveryDateStart", Naming.parameter("deliveryDateStart"));
        assertEquals("locationId", Naming.parameter("locationId"));
        assertEquals("xRequestId", Naming.parameter("X-Request-Id"));
        assertEquals("transportCompanyId", Naming.parameter("transport_company_id"));
        assertEquals("filterStatus", Naming.parameter("filter[status]"));
        assertEquals("pageSize", Naming.parameter("page.size"));
        // результат всегда годится в имя java-параметра
        assertEquals("_class", Naming.parameter("class"));
        assertEquals("_2fa", Naming.parameter("2fa"));
    }

    @Test
    void packageSegmentsAndGroups() {
        assertEquals("dailydemands", Naming.packageSegment("daily-demands"));
        assertEquals("drivers", Naming.packageSegment("drivers"));
        assertEquals("tenant", Naming.packageSegment("{tenant}"));
        assertEquals("_import", Naming.packageSegment("import"));
        assertEquals("_2fa", Naming.packageSegment("2fa"));
        assertTrue(Naming.isGroupSegment("daily-demands"));
        assertTrue(Naming.isGroupSegment(""), "пустая группа — корень model");
        assertFalse(Naming.isGroupSegment("ru.example"));
        assertFalse(Naming.isGroupSegment("a/b"));
    }

    @Test
    void pascalParamKeepsCamelHumps() {
        assertEquals("LocationId", Naming.pascalParam("locationId"));
        assertEquals("Id", Naming.pascalParam("id"));
        assertEquals("TransportCompanyId", Naming.pascalParam("transport-company-id"));
    }

    @Test
    void fieldAndEnumNaming() {
        assertEquals("transportCompanyId", Naming.fieldName("transport_company_id"));
        assertEquals("opusId", Naming.fieldName("opusId"));
        assertEquals("descriptionRu", Naming.fieldName("descriptionRu"));
        assertEquals("SHUTTING_DOWN", Naming.enumConstant("shutting_down"));
        assertEquals("E_BOOK", Naming.enumConstant("e_book"));
        assertEquals("IN_ROUTE", Naming.enumConstant("IN_ROUTE"));
        assertTrue(Naming.isUsableIdentifier("code"));
        assertFalse(Naming.isUsableIdentifier("class"));
    }

    @Test
    void identifiers() {
        assertTrue(Naming.isIdentifier("findOne"));
        assertTrue(Naming.isIdentifier("driversController_findOne"));
        assertFalse(Naming.isIdentifier("get-all"));
        assertFalse(Naming.isIdentifier("2fast"));
        assertFalse(Naming.isIdentifier(null));
    }
}
