package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Загрузка OpenAPI-спеки: резолвим $ref, ошибки и предупреждения парсера всплывают как исключение.
 */
public final class SpecLoader {

    private SpecLoader() {
    }

    public static OpenAPI load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Файл спеки не найден: " + file);
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(file.toUri().toString(), null, options);
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            throw new IllegalArgumentException("Не удалось разобрать спеку " + file + ": " + result.getMessages());
        }
        return openAPI;
    }
}
