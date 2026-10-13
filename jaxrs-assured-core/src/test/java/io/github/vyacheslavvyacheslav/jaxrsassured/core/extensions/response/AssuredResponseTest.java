package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response;

import io.restassured.config.JsonConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.config.JsonPathConfig;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * modifiedResponse: новый конфиг rest-assured действует и на проверки, и на extract(), исходный ответ не меняется.
 * Для наглядности конфиг меняет тип чисел в JSON: по умолчанию Float, с BIG_DECIMAL — BigDecimal.
 * Время ответа и печать запроса/ответа при упавшей проверке — как в rest-assured.
 */
class AssuredResponseTest {

    private static final byte[] BODY = "{\"price\": 12.5}".getBytes(StandardCharsets.UTF_8);

    private static final RestAssuredConfig BIG_DECIMAL_CONFIG = RestAssuredConfig.config()
            .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));

    private static final RequestLog GET_REQUEST = logConfig -> "Request method:\tGET";

    @Test
    void responseTimeShouldBeAvailableWhenMeasured() {
        AssuredResponse response = AssuredResponse.of(jsonResponse(), BODY, RestAssuredConfig.config(), RequestLog.NONE, 42);

        assertEquals(42, response.extract().time());
        response.time(equalTo(42L));
        assertEquals(42, response.modifiedResponse(BIG_DECIMAL_CONFIG).extract().time());
    }

    @Test
    void responseTimeShouldBeUnknownWithoutMeasurement() {
        AssuredResponse response = AssuredResponse.of(jsonResponse(), BODY, RestAssuredConfig.config());

        assertEquals(AssuredResponse.NO_RESPONSE_TIME, response.extract().time());
    }

    @Test
    void requestAndResponseShouldBePrintedOnlyWhenValidationFails() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RestAssuredConfig config = RestAssuredConfig.config().logConfig(LogConfig.logConfig()
                .defaultStream(new PrintStream(output, true, StandardCharsets.UTF_8))
                .enableLoggingOfRequestAndResponseIfValidationFails());
        AssuredResponse response = AssuredResponse.of(jsonResponse(), BODY, config, GET_REQUEST, 42);

        response.statusCode(200);
        assertEquals("", output.toString(StandardCharsets.UTF_8));

        assertThrows(AssertionError.class, () -> response.statusCode(500));
        String printed = output.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Request method:\tGET"), printed);
        assertTrue(printed.contains("HTTP/1.1 200 OK"), printed);
    }

    @Test
    void nothingShouldBePrintedOnFailureByDefault() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RestAssuredConfig config = RestAssuredConfig.config().logConfig(LogConfig.logConfig()
                .defaultStream(new PrintStream(output, true, StandardCharsets.UTF_8)));
        AssuredResponse response = AssuredResponse.of(jsonResponse(), BODY, config, GET_REQUEST, 42);

        assertThrows(AssertionError.class, () -> response.statusCode(500));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void modifiedResponseShouldApplyConfigToExtract() {
        AssuredResponse modified = AssuredResponse.of(jsonResponse(), BODY, RestAssuredConfig.config())
                .modifiedResponse(BIG_DECIMAL_CONFIG);

        Object price = modified.extract().path("price");

        assertEquals(new BigDecimal("12.5"), price);
    }

    @Test
    void modifiedResponseShouldApplyConfigToValidation() {
        AssuredResponse modified = AssuredResponse.of(jsonResponse(), BODY, RestAssuredConfig.config())
                .modifiedResponse(BIG_DECIMAL_CONFIG);

        modified.body("price", equalTo(new BigDecimal("12.5")));
    }

    @Test
    void modifiedResponseShouldNotChangeOriginal() {
        AssuredResponse original = AssuredResponse.of(jsonResponse(), BODY, RestAssuredConfig.config());

        original.modifiedResponse(BIG_DECIMAL_CONFIG);

        Object price = original.extract().path("price");
        assertEquals(Float.valueOf(12.5f), price);
        original.body("price", equalTo(12.5f));
    }

    @Test
    void modifiedResponseShouldParseJsonWithoutContentType() {
        BasicHttpResponse withoutContentType = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        AssuredResponse modified = AssuredResponse.of(withoutContentType, BODY, RestAssuredConfig.config())
                .modifiedResponse(BIG_DECIMAL_CONFIG);

        modified.body("price", equalTo(new BigDecimal("12.5")));
    }

    private static BasicHttpResponse jsonResponse() {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Content-Type", "application/json");
        return response;
    }
}
