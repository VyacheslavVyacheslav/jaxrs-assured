package io.github.vyacheslavvyacheslav.jaxrsassured.core.clients;

import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.AssuredCall;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.AssuredResponse;

import java.util.function.Consumer;

/**
 * Ответ для проверок вместо DTO — по требованию, без изменения сигнатур ресурса.
 * <pre>{@code
 * Book book = books.findOne(7);                                    // как обычно: DTO
 *
 * books.assured(r -> r.findOne(404))                               // ответ для проверок
 *         .statusCode(404)
 *         .body("code", equalTo("NOT_FOUND"));
 *
 * books.assured(r -> r.deleteHeader("Authorization").findOne(7))
 *         .statusCode(401);                                        // настройка — только для этого вызова
 * }</pre>
 * Правила:
 * <ul>
 *     <li>в лямбде ровно один HTTP-вызов ресурса {@code r}, иначе IllegalStateException;</li>
 *     <li>на 4xx/5xx исключений нет — статус и тело проверяются через возвращенный AssuredResponse;</li>
 *     <li>настройки ресурса действуют; set.../delete.../reset внутри лямбды касаются только этого вызова;</li>
 *     <li>внутри лямбды метод ресурса возвращает null (0/false для примитивов) — не вызывайте ничего у его результата;</li>
 *     <li>переходы по подресурсам внутри лямбды пока не поддерживаются: вызывайте assured у нужного подресурса;</li>
 *     <li>реактивные методы (CompletionStage и т.п.) не поддерживаются.</li>
 * </ul>
 */
public interface AssuredResource<T> {

    /**
     * Выполняет единственный HTTP-вызов из лямбды и возвращает его ответ для проверок.
     */
    @AssuredCall
    AssuredResponse assured(Consumer<T> call);
}
