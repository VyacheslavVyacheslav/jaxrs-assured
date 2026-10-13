package io.github.vyacheslavvyacheslav.jaxrsassured.core.clients;

import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.ConfigurableRequest;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.annotations.HttpConfigurableType;

/**
 * Настройка запроса на ходу: заголовки, cookie, query-параметры, тело. Методы не являются HTTP-эндпоинтами:
 * прокси перехватывает их по аннотации ConfigurableRequest.
 * <p>
 * Ресурс — объект со своими настройками: {@code set...}/{@code delete...} меняют только тот ресурс,
 * на котором вызваны, и действуют на все его следующие вызовы; {@code reset} очищает их.
 * Методы возвращают этот же ресурс, поэтому их можно и вызывать отдельной строкой, и ставить в цепочку.
 * Каждый {@code get...Resource()} возвращает новый ресурс с настройками родителя на момент получения.
 * <pre>{@code
 * BooksResource books = root.getBooksResource().setHeader("X-Actor", "qa");
 * books.findOne(7);                          // с X-Actor
 * root.getBooksResource().findOne(7);        // новый ресурс — без X-Actor
 * books.reset();
 * books.findOne(7);                          // без X-Actor
 *
 * // негативные проверки — то, что не пропускают типизированные параметры метода
 * books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null)).statusCode(400);
 * books.assured(r -> r.setBody("{\"price\": \"free\"}").patch(7, new PatchBookDto())).statusCode(400);
 * }</pre>
 * Повторная настройка того же заголовка (без учета регистра), той же cookie, того же параметра или тела заменяет предыдущую.
 * Настройка применяется после параметров метода (@HeaderParam, @QueryParam, @CookieParam, тело) и обычных фильтров,
 * поэтому перекрывает и их значения (например, Authorization).
 * Текущую настройку показывает {@code toString()} ресурса.
 * Настроенный ресурс не стоит хранить в static-поле: его изменения увидят все тесты.
 * Ответ для проверок вместо DTO — {@link AssuredResource#assured}.
 */
public interface ConfigurationResource<T> extends AssuredResource<T> {

    /**
     * Задает значения заголовка для всех следующих вызовов этого ресурса.
     */
    @ConfigurableRequest(HttpConfigurableType.HEADER)
    T setHeader(String key, Object value, String ... additionalValue);

    /**
     * Удаляет заголовок из всех следующих вызовов этого ресурса, кем бы он ни был выставлен.
     */
    @ConfigurableRequest(HttpConfigurableType.DELETE_HEADER)
    T deleteHeader(String key);

    /**
     * Задает cookie для всех следующих вызовов, заменяя cookie с тем же именем (в том числе из @CookieParam).
     */
    @ConfigurableRequest(HttpConfigurableType.COOKIE)
    T setCookie(String name, String value);

    /**
     * Удаляет cookie из всех следующих вызовов, кем бы она ни была выставлена.
     */
    @ConfigurableRequest(HttpConfigurableType.DELETE_COOKIE)
    T deleteCookie(String name);

    /**
     * Задает значения query-параметра для всех следующих вызовов, заменяя его значения (в том числе из @QueryParam).
     * Значения уходят строкой как есть: можно отправить то, что не пропускает типизированный параметр метода.
     */
    @ConfigurableRequest(HttpConfigurableType.QUERY_PARAM)
    T setQueryParam(String name, Object value, Object ... additionalValue);

    /**
     * Удаляет query-параметр из всех следующих вызовов, в том числе выставленный через @QueryParam.
     */
    @ConfigurableRequest(HttpConfigurableType.DELETE_QUERY_PARAM)
    T deleteQueryParam(String name);

    /**
     * Подменяет тело запроса строкой как есть (например, битым JSON вместо DTO) с итоговым Content-Type.
     * По смыслу — одноразовая настройка: удобнее всего внутри {@link AssuredResource#assured}.
     * Для GET RestEasy тело не отправит и бросит ошибку.
     */
    @ConfigurableRequest(HttpConfigurableType.BODY)
    T setBody(String body);

    /**
     * Снимает все настройки этого ресурса — заголовки, cookie, query-параметры, тело,
     * включая унаследованные от родительского ресурса.
     */
    @ConfigurableRequest(HttpConfigurableType.RESET)
    T reset();

}
