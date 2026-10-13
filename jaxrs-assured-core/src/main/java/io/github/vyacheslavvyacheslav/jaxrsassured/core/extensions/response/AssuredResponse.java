package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response;

import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.time.TimingFilter;
import io.restassured.internal.ResponseParserRegistrar;
import io.restassured.internal.RestAssuredResponseImpl;
import io.restassured.internal.ValidatableResponseOptionsImpl;
import io.restassured.internal.http.HttpResponseDecorator;
import io.restassured.internal.log.LogRepository;
import io.restassured.internal.print.ResponsePrinter;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.apache.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Ответ для проверок rest-assured. Хранит исходные статус, заголовки, тело, время ответа и описание запроса,
 * поэтому может пересобраться с другим конфигом rest-assured (modifiedResponse).
 */
public class AssuredResponse extends ValidatableResponseOptionsImpl<ValidatableResponse, Response> implements ValidatableResponse {

    /**
     * Как в rest-assured: время ответа неизвестно.
     */
    public static final long NO_RESPONSE_TIME = -1;

    private final HttpResponse httpResponse;
    private final byte[] content;
    private final RequestLog requestLog;
    private final long responseTimeMillis;

    private AssuredResponse(ResponseParserRegistrar rpr, RestAssuredConfig config, RestAssuredResponseImpl response,
                            LogRepository logRepository, HttpResponse httpResponse, byte[] content,
                            RequestLog requestLog, long responseTimeMillis) {
        super(rpr, config, response, response, logRepository);
        this.httpResponse = httpResponse;
        this.content = content;
        this.requestLog = requestLog;
        this.responseTimeMillis = responseTimeMillis;
    }

    /**
     * Ответ без описания запроса и без времени ответа.
     */
    public static AssuredResponse of(HttpResponse httpResponse, byte[] content, RestAssuredConfig config) {
        return of(httpResponse, content, config, RequestLog.NONE, NO_RESPONSE_TIME);
    }

    /**
     * Собирает ответ так же, как RequestSpecificationImpl rest-assured: конфиг и производные от него настройки ответа.
     *
     * @param httpResponse       статус и заголовки ответа, тело из него не читается
     * @param content            тело ответа
     * @param requestLog         описание запроса для лога при упавшей проверке
     * @param responseTimeMillis время ответа для time(), {@link #NO_RESPONSE_TIME} — неизвестно
     */
    public static AssuredResponse of(HttpResponse httpResponse, byte[] content, RestAssuredConfig config,
                                     RequestLog requestLog, long responseTimeMillis) {
        Objects.requireNonNull(config, "RestAssuredConfig can not be null");
        LogRepository logRepository = new LogRepository();
        RestAssuredResponseImpl restAssuredResponse = new RestAssuredResponseImpl();
        restAssuredResponse.setConfig(config);
        restAssuredResponse.setSessionIdName(config.getSessionConfig().sessionIdName());
        restAssuredResponse.setDecoderConfig(config.getDecoderConfig());
        restAssuredResponse.setLogRepository(logRepository);
        if (responseTimeMillis != NO_RESPONSE_TIME) {
            // там же время ответа хранит TimingFilter rest-assured
            restAssuredResponse.setFilterContextProperties(Map.of(TimingFilter.RESPONSE_TIME_MILLISECONDS, responseTimeMillis));
        }

        ResponseParserRegistrar responseParserRegistrar = new ResponseParserRegistrar();
        responseParserRegistrar.registerDefaultParser(Parser.JSON);
        // hasBodyAssertions = true: тело сразу сохраняется массивом байт и читается сколько угодно раз
        restAssuredResponse.parseResponse(new HttpResponseDecorator(httpResponse, null),
                new ByteArrayInputStream(content), true, responseParserRegistrar);
        if (config.getLogConfig().isLoggingOfRequestAndResponseIfValidationFailsEnabled()) {
            registerLogs(logRepository, restAssuredResponse, config.getLogConfig(), requestLog);
        }
        return new AssuredResponse(responseParserRegistrar, config, restAssuredResponse, logRepository,
                httpResponse, content, requestLog, responseTimeMillis);
    }

    /**
     * Тот же ответ с другим конфигом rest-assured: конфиг действует и на проверки, и на extract().
     * Исходный ответ не меняется.
     */
    public AssuredResponse modifiedResponse(RestAssuredConfig config) {
        return of(httpResponse, content, config, requestLog, responseTimeMillis);
    }

    @Override
    public Response originalResponse() {
        return response;
    }

    /**
     * Как фильтры логирования rest-assured: запрос и ответ записываются заранее, а печатает их
     * LogRequestAndResponseOnFailListener — только если проверка упала.
     */
    private static void registerLogs(LogRepository logRepository, RestAssuredResponseImpl response, LogConfig logConfig,
                                     RequestLog requestLog) {
        String request = requestLog.print(logConfig);
        if (request != null) {
            ByteArrayOutputStream requestText = new ByteArrayOutputStream();
            requestText.writeBytes(request.getBytes(StandardCharsets.UTF_8));
            logRepository.registerRequestLog(requestText);
        }
        ByteArrayOutputStream responseText = new ByteArrayOutputStream();
        ResponsePrinter.print(response, response, new PrintStream(responseText, true, StandardCharsets.UTF_8),
                LogDetail.ALL, logConfig.isPrettyPrintingEnabled(), logConfig.blacklistedHeaders());
        logRepository.registerResponseLog(responseText);
    }
}
