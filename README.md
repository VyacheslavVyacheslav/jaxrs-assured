# jaxrs-assured

**Библиотека для API-тестов на Java: ручки сервиса описываются JAX-RS-интерфейсами, а ответы проверяются
всей мощью rest-assured.**

В основе — три идеи:

- **JAX-RS-ресурсы.** API описывается интерфейсами со стандартными аннотациями `@Path`, `@GET`, `@QueryParam` —
  только объявление, реализацию строит прокси. Тест вызывает метод и получает готовый DTO.
- **`AssuredResponse`.** Любой вызов можно обернуть в `assured(...)` и проверить статус, тело, заголовки и время
  ответа привычным API rest-assured. Ошибка 4xx/5xx при этом не исключение, а ответ, который можно проверить.
- **`ConfigurationResource`.** Каждый ресурс умеет менять запрос на ходу: добавить или убрать заголовок, cookie,
  query-параметр, подменить параметр пути или тело. Так пишутся негативные тесты, которые типизированный клиент
  обычно не пропускает.

```java
BooksResource books = client.api().getBooksResource();

Book book = books.findOne(7);                                       // обычный вызов — типизированный DTO

books.assured(r -> r.findOne(404))                                  // тот же метод — ответ для проверок
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));

books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null))
        .statusCode(400);                                           // строка вместо числа — в обход типов
```

Интерфейсы и DTO можно написать руками или сгенерировать из OpenAPI-спеки — генератор входит в проект.

## Зачем: JAX-RS вместо клиента на rest-assured

rest-assured — отличный инструмент для проверок, и в аккуратном проекте ручки не пишут строками в каждом тесте,
а собирают в классы-клиенты на `RequestSpecification`. Обычно это выглядит так:

```java
public class BooksClient {

    private final RequestSpecification spec;

    public BooksClient(RequestSpecification base) {
        spec = new RequestSpecBuilder().addRequestSpecification(base).setBasePath("/api/v1/books").build();
    }

    public Response findAll(Integer page, Integer limit, Integer authorId, String genre) {
        RequestSpecification request = given().spec(spec);
        if (page != null) request.queryParam("page", page);
        if (limit != null) request.queryParam("limit", limit);
        if (authorId != null) request.queryParam("authorId", authorId);
        if (genre != null) request.queryParam("genre", genre);
        return request.get();
    }

    public Response findOne(Integer id) {
        return given().spec(spec).pathParam("id", id).get("/{id}");
    }

    public Response create(CreateBookDto dto) {
        return given().spec(spec).contentType(ContentType.JSON).body(dto).post();
    }
}
```

Тот же ресурс на JAX-RS. Здесь только объявление, реализацию строит прокси:

```java
public interface BooksResource extends ConfigurationResource<BooksResource> {

    @GET
    PageDto<Book> findAll(@QueryParam("page") Integer page, @QueryParam("limit") Integer limit,
                          @QueryParam("authorId") Integer authorId, @QueryParam("genre") String genre);

    @GET
    @Path("{id}")
    Book findOne(@PathParam("id") Integer id);

    @POST
    @Consumes("application/json")
    Book create(CreateBookDto createBookDto);
}
```

И в тесте:

```java
// клиент на rest-assured
Book book = booksClient.findOne(7).then().statusCode(200).extract().as(Book.class);

// jaxrs-assured: на 4xx/5xx — исключение
Book book = books.findOne(7);
```

Чем JAX-RS удобнее:

- **Объявление вместо реализации.** Ручка описывается сигнатурой, а не телом метода. Имя параметра, его тип и место
  в запросе (путь, query, заголовок) стоят рядом, в одной аннотации. Меньше кода — меньше мест для ошибки
  и меньше работы, когда API меняется.
- **Сигнатура — это контракт.** `Book findOne(Integer id)` сразу говорит, что ручка принимает и что возвращает.
  Метод клиента на rest-assured возвращает `Response`, а что внутри, знает только документация.
- **Один метод на ручку.** В клиентах на rest-assured ручку часто описывают дважды: метод с `Response` для проверок
  и метод с DTO для шагов теста. Здесь метод один: обычный вызов возвращает DTO, а `assured(...)` — ответ
  для проверок.
- **Дерево ресурсов из коробки.** Подресурсы — методы-локаторы, пути складываются сами:
  `api.getAuthorsResource().getAuthorResource("a1").getBooksResource()`. Настройки родителя, например заголовок,
  переходят к подресурсам. В rest-assured базовые пути и спецификации подресурсов склеиваются вручную.
- **Рутину берёт на себя JAX-RS.** Сериализация тела, `Content-Type` из `@Consumes`, разбор ответа в DTO, пропуск
  `null`-параметров — без строчки кода в клиенте.
- **Стандарт, а не самописный каркас.** Аннотации Jakarta REST знает любой Java-разработчик: это те же аннотации,
  что в JAX-RS-сервисах. Интерфейсы переносимы между RestEasy, Jersey и CXF и генерируются из OpenAPI. Клиент
  на rest-assured в каждом проекте устроен по-своему.

При этом сильные стороны rest-assured остаются. Проверки ответа — это тот же rest-assured (`AssuredResponse`),
а гибкость `given()` для негативных запросов возвращает `ConfigurationResource`.

| | клиент на rest-assured | jaxrs-assured |
|---|---|---|
| описание ручки | метод с телом: `given().spec(...)…get(...)` | сигнатура с аннотациями |
| тип ответа | `Response`, содержимое известно из документации | DTO в сигнатуре |
| DTO и проверки | два метода на ручку или `extract().as(...)` в тестах | один метод: DTO или `assured(...)` |
| подресурсы | базовые пути склеиваются вручную | методы-локаторы, пути складываются сами |
| необязательные параметры | `if (x != null)` в каждом методе | `null` в запрос не уходит |
| проверки ответа | GPath + Hamcrest | GPath + Hamcrest — тот же rest-assured |
| неверный запрос для негативного теста | `given()` позволяет всё | `setQueryParam`, `setBody`, `deleteHeader`, … |
| переносимость | свой каркас в каждом проекте | стандарт Jakarta REST |

Обычные типизированные клиенты (Feign, Retrofit, JAX-RS без обвязки) тоже дают интерфейсы, но в тестах неудобны:
на 4xx/5xx бросают исключение и не дают отправить заведомо неверный запрос. Эти пробелы закрывают `assured(...)`
и `ConfigurationResource`.

## JAX-RS: API как дерево интерфейсов

Корень API и подресурсы связаны методами-локаторами, поэтому код повторяет структуру URL:

```java
@Path("api/v1")
public interface V1Resource extends ConfigurationResource<V1Resource> {

    @Path("books")
    BooksResource getBooksResource();

    @Path("authors")
    AuthorsResource getAuthorsResource();
}
```

Параметр пути тоже может жить в локаторе. Тогда `getAuthorResource("a1")` возвращает отдельный ресурс конкретного
автора — `AuthorResource`, и его ручкам id уже не нужен:

```java
public interface AuthorsResource extends ConfigurationResource<AuthorsResource> {

    @GET
    PageDto<Author> findAll(@QueryParam("page") Integer page, @QueryParam("limit") Integer limit);

    @Path("{id}")
    AuthorResource getAuthorResource(@PathParam("id") String id);     // локатор с параметром пути
}

public interface AuthorResource extends ConfigurationResource<AuthorResource> {

    @GET
    Author findOne();                                                 // id уже в пути

    @Path("books")
    AuthorBooksResource getBooksResource();
}

public interface AuthorBooksResource extends ConfigurationResource<AuthorBooksResource> {

    @GET
    PageDto<Book> findAll(@QueryParam("page") Integer page, @QueryParam("format") BookFormat format);
}
```

```java
AuthorResource author = api.getAuthorsResource().getAuthorResource("a1");

author.findOne();                                          // GET /api/v1/authors/a1
author.getBooksResource().findAll(1, BookFormat.E_BOOK);   // GET /api/v1/authors/a1/books?page=1&format=e_book
```

IDE ведёт по API как по дереву: после точки видны и подресурсы, и ручки. Поменялся путь или параметр — правится
интерфейс, а компилятор сам покажет все затронутые тесты.

Тела запросов собираются билдерами DTO, ответы приходят готовыми объектами, необязательный параметр передаётся
как `null` и в запрос не попадает:

```java
Book created = books.create(CreateBookDto.builder().title("Новая книга").price(9.99).build());
PageDto<Book> fiction = books.findAll(1, 20, null, "FICTION");      // ?page=1&limit=20&genre=FICTION
```

Подробнее — [Интерфейсы ресурсов](#интерфейсы-ресурсов).

## AssuredResponse: проверки rest-assured из коробки

Обычный вызов возвращает DTO, а на 4xx/5xx бросает исключение — так удобно, когда ответ нужен для следующего шага
теста. Когда проверяется сам ответ, тот же вызов оборачивается в `assured(...)`:

```java
books.assured(r -> r.findAll(1, 20, null, "FICTION"))
        .statusCode(200)
        .contentType(ContentType.JSON)
        .time(lessThan(2000L))
        .body("total", greaterThan(0))
        .body("data.genre.code", everyItem(equalTo("FICTION")))
        .body("data.title", hasItem("Мастер и Маргарита"));
```

`AssuredResponse` — это `ValidatableResponse` из rest-assured, поэтому работает всё, что вы уже знаете:

- статус, заголовки, `Content-Type`, время ответа;
- тело через GPath и матчеры Hamcrest: `body("data.size()", equalTo(20))`,
  `body("data.find { it.id == 7 }.title", equalTo("Мастер и Маргарита"))`;
- извлечение значений: `extract().path("data[0].id")`, `extract().as(Book.class)`, `extract().asString()`;
- печать запроса и ответа в лог, если проверка упала.

Главное отличие от типизированного клиента: ошибка сервиса становится обычным ответом, который можно разобрать
и проверить.

```java
books.assured(r -> r.findOne(404))
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"))
        .body("message", notNullValue());
```

Сигнатуры ресурса для этого менять не нужно: один и тот же интерфейс обслуживает и позитивные сценарии, где нужен
DTO, и проверки ответа.

Подробнее — [Ответ для проверок: assured](#ответ-для-проверок-assured).

## ConfigurationResource: запрос можно изменить на ходу

Типы защищают от ошибок, но негативному тесту нужна именно ошибка: строка вместо числа, запрос без обязательного
параметра, битый JSON, просроченная сессия, запрос без токена. Для этого каждый ресурс, унаследованный от
`ConfigurationResource`, умеет менять запрос. Эти методы — не HTTP-ручки: прокси перехватывает их и запоминает
настройку.

```java
// только для одного вызова — внутри assured(...)
books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null)).statusCode(400);     // строка вместо числа
books.assured(r -> r.setPathParam("id", "abc").findOne(7)).statusCode(400);                         // строка вместо числа в пути
books.assured(r -> r.deleteQueryParam("page").findAll(1, 20, null, null)).statusCode(400);          // без обязательного параметра
books.assured(r -> r.setBody("{\"price\": \"free\"}").patch(7, new PatchBookDto())).statusCode(400); // битый JSON вместо DTO
books.assured(r -> r.setCookie("session", "expired").findOne(7)).statusCode(401);                   // просроченная сессия
books.assured(r -> r.deleteHeader("Authorization").findOne(7)).statusCode(401);                     // без токена

// для всех следующих вызовов — на самом ресурсе
BooksResource books = api.getBooksResource()
        .setHeader("X-Actor", "qa")
        .setHeader("Accept-Language", "ru");
```

- **Сигнатура метода не мешает.** Настройка применяется последней — после параметров метода, локаторов и фильтров
  клиента — и перекрывает их: `deleteHeader("Authorization")` убирает даже токен, который подставил
  `BearerAuthFilter`, а `setPathParam("id", "abc")` заменяет и `id` из `findOne(7)`, и `id`, переданный в локатор
  `getStoreResource(3)` выше по дереву.
- **Два режима.** На ресурсе настройка действует на все его следующие вызовы, внутри `assured(...)` — только на один.
- **Настройки не утекают.** Каждый `get...Resource()` возвращает новый ресурс с настройками родителя на момент
  получения; его собственные изменения родителя не трогают. `reset()` снимает все настройки, а значения,
  переданные в локаторы, остаются: это адрес ресурса.
- **Видно, что настроено.** `toString()` ресурса показывает текущую настройку:
  `BooksResource [X-Actor=qa, -Authorization, query:limit=abc]`.

Подробнее — [Настройка запроса](#настройка-запроса-заголовки-cookie-параметры-тело).

## Генератор из OpenAPI

Интерфейсы можно не писать руками: `jaxrs-assured-codegen` строит дерево JAX-RS-ресурсов и DTO из OpenAPI-спеки
(json/yaml) — Maven-плагином прямо в сборке или из командной строки. DTO получаются с билдерами, на обычной Java
или на Lombok.

Генератор от core не зависит: он выдаёт чистые JAX-RS-интерфейсы, пригодные для любого прокси-клиента
(RestEasy, Jersey, CXF). С core их связывает один параметр — `superInterface`.
Подробнее — [jaxrs-assured-codegen](#jaxrs-assured-codegen).

## Сравнение с openapi-generator

Ближайший готовый аналог — [openapi-generator](https://openapi-generator.tech) с генератором `java` и
`library=rest-assured`. Он тоже строит из спеки типизированный клиент поверх rest-assured: у каждой операции есть
билдер параметров, `executeAs(...)` возвращает DTO, `execute(...)` — ответ rest-assured, а `reqSpec(...)` позволяет
дописать в запрос что угодно. Примерно так:

```java
// openapi-generator, library=rest-assured
Book book = api.books().findOne().idPath(7)
        .executeAs(r -> r.then().statusCode(200).extract().response());

api.books().findAll()
        .reqSpec(spec -> spec.addQueryParam("limit", "abc"))
        .execute(r -> r.then().statusCode(400));
```

```java
// jaxrs-assured
Book book = books.findOne(7);

books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null))
        .statusCode(400);
```

Оба подхода дают типы, DTO и проверки rest-assured. Разница в том, как описано API и как с ним работает тест:

| | openapi-generator (`library=rest-assured`) | jaxrs-assured |
|---|---|---|
| что получается | классы-реализации с билдером на каждую операцию | JAX-RS-интерфейсы, только объявления |
| писать и править руками | не принято: кода много, при генерации он перезаписывается | можно: интерфейс короткий, генератор — по желанию |
| структура API | плоские классы по тегам: `api.books().findOne()` | дерево по URL: `api.getAuthorsResource().getAuthorResource("a1").getBooksResource()` |
| вызов ручки | билдер: `.idPath(7).executeAs(...)` | метод: `findOne(7)` |
| DTO или ответ для проверок | `executeAs(...)` или `execute(...)` | обычный вызов или `assured(...)` |
| изменить запрос | `reqSpec(...)` — всё, что умеет `RequestSpecBuilder` | `set...` и `delete...` перекрывают параметры метода и фильтры; `delete...` убирает значение, кем бы оно ни было выставлено |
| общие настройки | `reqSpecSupplier` на весь клиент или `reqSpec` на операцию | на любом узле дерева, подресурсы их наследуют |
| транспорт | rest-assured | RestEasy (JAX-RS), проверки — rest-assured |
| привязка к библиотеке | код привязан к rest-assured | чистые JAX-RS-интерфейсы подходят RestEasy, Jersey, CXF |

Где openapi-generator сильнее — это стоит учитывать при выборе:

- **Зрелость.** Большое сообщество, годы развития, поддержка почти всех возможностей OpenAPI. jaxrs-assured
  молодой: `oneOf`/`anyOf` и наследование через `allOf` пока становятся `JsonNode`.
- **Allure из коробки.** Запросы идут через rest-assured, поэтому работает стандартный фильтр `AllureRestAssured`.
  В jaxrs-assured запросы идут через RestEasy, своей интеграции с Allure пока нет.
- **Доступность.** openapi-generator опубликован в Maven Central, jaxrs-assured пока ставится только локально.

Среди генераторов openapi-generator есть и такие, что выдают JAX-RS-интерфейсы (например, `jaxrs-spec`
с `interfaceOnly`), но они рассчитаны на сервер: интерфейсы плоские, без подресурсов-локаторов и без связки
с проверками rest-assured.

## Модули

| Модуль | Что это |
|---|---|
| `jaxrs-assured-core` | фреймворк: прокси RestEasy, `ConfigurationResource` (настройка запроса на ходу), `AssuredResource` (ответ для проверок rest-assured) |
| `jaxrs-assured-codegen` | генератор JAX-RS-интерфейсов и DTO из OpenAPI: Maven-плагин, CLI, исполняемый jar |
| `jaxrs-assured-integration-tests` | интеграционные тесты генератора и сквозной тест «сгенерированный клиент + core» на учебной спеке [docs/openapi/bookstore.json](docs/openapi/bookstore.json) |

## Сборка

Нужны JDK 17+ и Maven 3.9+. Проект пока не опубликован в Maven Central — соберите и установите его
в локальный репозиторий:

```bash
mvn install
```

`mvn install` прогоняет тесты всех модулей и ставит в `~/.m2` `jaxrs-assured-core`, `jaxrs-assured-codegen`
и родительский pom (`jaxrs-assured-integration-tests` не ставится).

## Подключение

```xml
<dependencies>
    <dependency>
        <groupId>io.github.vyacheslavvyacheslav</groupId>
        <artifactId>jaxrs-assured-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.github.vyacheslavvyacheslav</groupId>
            <artifactId>jaxrs-assured-codegen</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <spec>src/main/openapi/bookstore.json</spec>
                        <basePackage>com.example.bookstore</basePackage>
                        <superInterface>io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.ConfigurationResource</superInterface>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Плагин генерирует на фазе `generate-sources` в `target/generated-sources/jaxrs-assured` и сам добавляет папку
в исходники сборки. `jaxrs-assured-core` приносит всё, что нужно сгенерированному коду (JAX-RS API, Jackson).
Рабочий пример конфигурации — [jaxrs-assured-integration-tests/pom.xml](jaxrs-assured-integration-tests/pom.xml).

Клиент сервиса — наследник `BaseClient`:

```java
public class BookstoreClient extends BaseClient {

    public V1Resource api() {
        return proxy(V1Resource.class, "https://bookstore.example.com");
    }
}
```

`BaseClient` держит пул HTTP-соединений: создавайте клиент один на прогон и закрывайте `close()` в конце.
Фильтры (например, `BearerAuthFilter`) передаются в `proxy(...)` и регистрируются на построенном ресурсе.

---

# jaxrs-assured-core

## Интерфейсы ресурсов

```java
@Path("api/v1")
public interface V1Resource extends ConfigurationResource<V1Resource> {

    @Path("books")
    BooksResource getBooksResource();
}

public interface BooksResource extends ConfigurationResource<BooksResource> {

    @GET
    PageDto<Book> findAll(@QueryParam("page") Integer page, @QueryParam("limit") Integer limit,
                          @QueryParam("authorId") Integer authorId, @QueryParam("genre") String genre);

    @GET
    @Path("{id}")
    Book findOne(@PathParam("id") Integer id);

    @POST
    @Consumes("application/json")
    Book create(CreateBookDto createBookDto);

    @Path("snapshots")
    BookSnapshotsResource getSnapshotsResource();
}
```

- Интерфейс наследует `ConfigurationResource<СвойТип>` — так у ресурса появляются настройки запроса
  (`setHeader`, `setCookie`, `setQueryParam`, `setPathParam`, `setBody`, `reset`, …) и `assured`.
- Class-level `@Path` — только у корня; подресурсы получают путь от `@Path` на методе-локаторе.
- Метод с телом (`POST`/`PUT`/`PATCH`) обязан объявить `@Consumes` — на методе или интерфейсе; без него ресурс
  не построится (`RESTEASY004590`). Из `@Consumes` берётся только первое значение.
- Дробные числа в параметрах (query, path, header, cookie, значения локаторов) уходят без лишнего:
  `42.0` → `42`, `1.0E-4` → `0.0001`, `BigDecimal 2.50` → `2.5`. Так `id` из DTO, где `number` стал `Double`, можно
  передать в следующий вызов как есть. Целые, `NaN` и бесконечности не меняются. Числа в `setHeader` идут так же,
  а `setQueryParam` значение не конвертирует — оно уходит строкой как есть.

## Настройка запроса: заголовки, cookie, параметры, тело

Ресурс — объект со своими настройками запроса:

```java
BooksResource books = api.getBooksResource().setHeader("X-Actor", "qa");

books.findOne(7);                          // с X-Actor
books.findAll(1, 20, null, null);          // тоже с X-Actor
api.getBooksResource().findOne(7);         // новый ресурс — без X-Actor

books.deleteHeader("X-Actor");             // убрать заголовок, кем бы он ни был выставлен
books.reset();                             // снять все настройки ресурса
```

| Метод | Что делает |
|---|---|
| `setHeader(name, value, ...)` / `deleteHeader(name)` | задать значения заголовка / убрать заголовок |
| `setCookie(name, value)` / `deleteCookie(name)` | задать cookie / убрать cookie (в том числе из `@CookieParam`) |
| `setQueryParam(name, value, ...)` / `deleteQueryParam(name)` | задать значения query-параметра / убрать параметр (в том числе из `@QueryParam`) |
| `setPathParam(name, value)` | задать значение параметра пути `{name}` — вместо значения из `@PathParam` метода или локатора |
| `setBody(raw)` | отправить вместо DTO строку как есть, с итоговым `Content-Type` |
| `reset()` | снять все настройки ресурса, включая унаследованные от родителя |

Главное применение — негативные проверки того, что не пропускают типизированные параметры:

```java
books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null)).statusCode(400);  // строка вместо числа
books.assured(r -> r.deleteQueryParam("page").findAll(1, 20, null, null)).statusCode(400);       // без обязательного параметра
books.assured(r -> r.setPathParam("id", "abc").findOne(7)).statusCode(400);                      // строка вместо числа в пути
books.assured(r -> r.setCookie("session", "expired").findOne(7)).statusCode(401);
books.assured(r -> r.setBody("{\"price\": \"free\"}").patch(7, new PatchBookDto())).statusCode(400);  // битый JSON вместо DTO
```

- Настройки меняют только тот ресурс, на котором вызваны, и действуют на все его следующие вызовы.
  Возвращают этот же ресурс — можно писать отдельной строкой или в цепочку. Внутри `assured(...)` — только на этот вызов.
- Каждый `get...Resource()` возвращает **новый** ресурс с настройками родителя на момент получения.
- Повторная настройка заменяет предыдущую: того же заголовка (регистр имени не важен), той же cookie,
  того же параметра, тела. Несколько значений — в порядке объявления: `setQueryParam("status", "NEW", "PAID")`.
- Настройка применяется последней — после параметров метода (`@HeaderParam`, `@QueryParam`, `@PathParam`,
  `@CookieParam`, тела), значений локаторов и фильтров клиента, поэтому перекрывает и их:
  `deleteHeader("Authorization")` убирает токен `BearerAuthFilter`.
  Не настраиваются только заголовки самого HTTP-клиента (`Host`, `Content-Length`, `Connection`).
- `setPathParam` подставляет значение в шаблон `{name}` раньше параметров метода и локаторов, поэтому заменяет
  и `id` из `findOne(7)`, и `id`, переданный в `getStoreResource(3)` выше по дереву:
  `stores.getStoreResource(3).getBooksResource().assured(r -> r.setPathParam("id", "abc").findAll(null))` уйдёт
  на `/stores/abc/books`. Значение кодируется как сегмент пути (пробел — `%20`, `/` — `%2F`). Если в пути вызова
  нет `{name}`, на этот вызов настройка не влияет. `reset()` её снимает, а значения локаторов остаются.
- `setBody` — по смыслу одноразовая настройка, удобнее внутри `assured(...)`. `Content-Type` берётся итоговый
  (из `@Consumes` или `setHeader`), кодировка — из его `charset`, по умолчанию UTF-8. Для GET тело не отправится.
- Cookie из ответов (`Set-Cookie`) клиент не запоминает: иначе cookie одного теста ушли бы в запросах другого.
- Текущую настройку показывает `toString()` ресурса:
  `BooksResource [X-Actor=qa, -Authorization, query:limit=abc, path:id=abc]`.
  Примененную к запросу настройку пишет DEBUG-лог `io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions`
  (значения не маскируются — токены попадут в лог).
- Настроенный ресурс не храните в `static`-поле — его изменения увидят все тесты.

## Ответ для проверок: assured

Методы ресурсов возвращают DTO, а на 4xx/5xx бросают исключение (`NotFoundException`, `NotAuthorizedException`, …).
Чтобы проверить статус и тело, тот же вызов оборачивается в `assured(...)`:

```java
Book book = books.findOne(7);                                       // как обычно: DTO

books.assured(r -> r.findOne(404))                                  // AssuredResponse вместо DTO
        .statusCode(404)
        .body("status", equalTo(404));

books.assured(r -> r.deleteHeader("Authorization").findOne(7))
        .statusCode(401);                                           // настройка в лямбде — только для этого вызова
```

`AssuredResponse` — это `ValidatableResponse` rest-assured: `statusCode`, `body`, `header`, `contentType`, `time`
и `extract()` — `extract().path("data[0].id")`, `extract().as(Book.class)`, `extract().asString()`.
Время ответа — от отправки запроса до полностью прочитанного тела, в миллисекундах.

Правила `assured`:
- в лямбде ровно один HTTP-вызов ресурса `r`; без вызова или с двумя — `IllegalStateException`;
- исключений на 4xx/5xx нет; ошибки сети и таймауты — по-прежнему исключения;
- внутри лямбды метод возвращает `null` (0/`false` для примитивов) — у результата ничего не вызывайте;
- переходы по подресурсам внутри лямбды не поддерживаются — вызывайте `assured` у нужного подресурса;
- реактивные методы (`CompletionStage` и т.п.) не поддерживаются.

Печать запроса и ответа при упавшей проверке включается в конфиге rest-assured — **один раз, до запуска тестов**:

```java
abstract class BaseApiTest {
    static {
        RestAssured.config = RestAssured.config().logConfig(LogConfig.logConfig()
                .enableLoggingOfRequestAndResponseIfValidationFails()
                .blacklistHeader("Authorization"));  // значение токена в логе заменится на [ BLACKLISTED ]
    }
}
```

`RestAssured.config` — одно статическое поле на всю JVM: не меняйте его из отдельных тестов. Другой конфиг
для одного ответа — `response.modifiedResponse(config)`.

## HTTP-клиент

`HttpClientUtil.restEasyClient(...)` — RestEasy на Apache HttpClient с таймаутами (соединение 10 с, чтение 60 с —
без них зависший сервер вешал бы тест навсегда), пулом на 50 соединений и без общего хранилища cookie.

Проверка TLS-сертификата включена по умолчанию. Для тестовых стендов с самоподписанным сертификатом её
выключают явно:

```java
public class StandClient extends BaseClient {

    public StandClient() {
        super(TlsVerification.DISABLED);   // любой сертификат и имя хоста — только для тестовых стендов
    }
}
```

Логи — через slf4j; реализацию (logback и т.п.) выбирает ваш проект.

---

# jaxrs-assured-codegen

Из OpenAPI-спеки (json/yaml) генерирует JAX-RS-интерфейсы (дерево подресурсов) и DTO на Jackson.
Парсер — swagger-parser, кодогенерация — JavaPoet. Лучше всего понимает спеки @nestjs/swagger.

## Запуск

| Способ | Когда |
|---|---|
| Maven-плагин, goal `generate` | генерация в сборке своего проекта — см. [Подключение](#подключение) |
| `jaxrs-assured-codegen/target/jaxrs-assured-codegen-<версия>-cli.jar` | разовая генерация из командной строки |
| `mvn -pl jaxrs-assured-codegen compile exec:java "-Dexec.args=..."` | отладка генератора |

```bash
java -jar jaxrs-assured-codegen/target/jaxrs-assured-codegen-0.1.0-SNAPSHOT-cli.jar \
    docs/openapi/bookstore.json target/client com.example.bookstore --ignore "^/health$"
```

```
Main <spec.(json|yaml)>                         — дерево путей спеки
Main <spec> <outDir> <basePackage> [флаги]      — генерация исходников
```

| Флаг CLI | Параметр плагина | Значение |
|---|---|---|
| 1-й аргумент | `spec` | спека; в плагине относительный путь — от корня проекта |
| 2-й аргумент | `outputDirectory` | куда писать; в плагине по умолчанию `${project.build.directory}/generated-sources/jaxrs-assured` |
| 3-й аргумент | `basePackage` | пакет корневого интерфейса |
| `--root <путь>` | `rootPath` | корень дерева путей, по умолчанию `api/v1`; class-level `@Path` корня |
| `--root-name <Имя>` | `rootName` | имя корневого интерфейса, по умолчанию `V1Resource` |
| `--ignore <regex>` | `ignorePaths` (`<ignorePath>`) | пропустить пути, целиком совпавшие с regex; пути вне `--root` без него — ошибка |
| `--ignore-header <имя>` | `ignoreHeaders` (`<ignoreHeader>`) | заголовок не становится параметром методов — его ставит фильтр (`BearerAuthFilter`) или `setHeader`; регистр не важен; имя, которого нет в спеке, — WARN |
| `--super-interface полный.Класс` | `superInterface` | супер-интерфейс ресурсов: `XResource extends Класс<XResource>`; для core — `…core.clients.ConfigurationResource` |
| `--import Схема=полный.Класс` | `importMappings` (`<Схема>класс</Схема>`) | схему не генерировать, ссылки типировать указанным классом |
| `--lombok` | `lombok` | DTO на аннотациях Lombok (см. [Два стиля DTO](#два-стиля-dto)) |
| `--split-model` | `splitModel` | DTO по подпакетам групп путей (см. [Раскладка model](#раскладка-model-по-пакетам)) |
| `--model-package Схема=группа` | `modelPackages` (`<Схема>группа</Схема>`) | закрепить пакет схемы при split-model; пустая группа — корень `model` |
| — | `testSources` | `true` — папка уходит в исходники тестов |
| — | `skip` (`-Djaxrsassured.skip`) | пропустить генерацию |

Коды возврата CLI: 0 — успех; 1 — ошибка генерации (спека не читается, пути вне `--root`, коллизии имён,
чужой файл на месте исходника); 2 — ошибка использования (неизвестный флаг, плохой regex, кривой `basePackage`).
В плагине ошибка конфигурации — `MojoExecutionException`, ошибка генерации — `MojoFailureException` с причиной.

## Что генерируется

```
<outDir>/<basePackage>/
├─ V1Resource.java                         # корень, class-level @Path("api/v1")
├─ resources/
│  ├─ books/BooksResource.java             # группа — первый сегмент пути
│  ├─ books/BookSnapshotsResource.java     # статический подресурс books/snapshots
│  └─ authors/AuthorResource.java          # authors/{id} с детьми — свой интерфейс за локатором
└─ model/
   ├─ PageDto.java                         # конверт выборки — дженерик PageDto<T>
   ├─ Book.java, Genre.java, …             # схемы components
   └─ BookFormat.java                      # чисто-энумная схема
```

- `{param}` без детей инлайнится в родителя: `@Path("{id}")` + `@PathParam`.
- Header-параметр — `@HeaderParam` у метода. Заголовки, которые в тестах ставит фильтр или `setHeader`
  (`Authorization`, `X-Actor`), лучше перечислить в `--ignore-header`: иначе в каждом вызове придется передавать
  `null`, а токен, переданный и параметром, и через `BearerAuthFilter`, уйдет двумя значениями.
- `{param}` с детьми — свой интерфейс за локатором: `StoreResource getStoreResource(@PathParam("id") Number id)`.
  Тип параметра локатора берётся из ручек поддерева по тем же правилам, что у параметров ручек (`number` → `Number`,
  `$ref` на энум → энум); разные числовые (`integer` в одной ручке, `number` в другой) — `Number`. Если ручки
  объявили его по-разному иначе — `String` и WARN со списком расхождений.
- Имя метода — operationId без префикса контроллера (`BooksController_findOne` → `findOne`).
- Имя java-параметра сохраняет горбы (`X-Request-Id` → `xRequestId`); совпавшие в методе имена разводятся
  суффиксом: header `last-event-id` + query `lastEventId` → `lastEventId`, `lastEventIdQuery`.
- Тело запроса → параметр-DTO + `@Consumes` (первый media type). Необязательное тело (`requestBody.required: false`
  или не указан — по стандарту OpenAPI это `false`) — ещё и перегрузка без тела и без `@Consumes`: рядом
  с `cancel(JsonNode body)` — `cancel()`. Так запрос без тела уходит без `Content-Type`: RestEasy ставит его
  по `@Consumes`, даже если передать `null`.
- Перед записью удаляются только свои прошлые файлы — `.java` с пометкой «Сгенерировано jaxrs-assured-codegen»
  внутри папки `basePackage`; чужой файл на месте будущего исходника — ошибка до любых удалений.

Возврат — по первому media type минимального 2xx:

| Ответ | Тип | `@Produces` |
|---|---|---|
| JSON | тип схемы (DTO, `PageDto<X>`, примитив) | нет — умолчание |
| файл: `string`/`binary` (zip, octet-stream, png) | `byte[]` | media type ответа |
| `text/event-stream` (SSE) | `InputStream` | `text/event-stream` |
| прочее (`text/html; charset=utf-8`) | тип схемы, обычно `String` | без параметров: `text/html` |

SSE-вызов возвращается сразу, события читаются из потока. Поток сам не кончается, а его `close()` в RestEasy
дочитывает поток до конца — чтобы перестать читать, закрывайте клиент.

### Типы

| OpenAPI | Java |
|---|---|
| `string` (включая date-time) | `String` |
| `integer` / `int64` | `Integer` / `Long` |
| `number` без format, `double`, `float` | `Double` — дробь не теряется |
| `number` без format в query/path/header-параметре и у локатора | `Number` — `number` бывает и целым (`page`, `id`), и дробным (метры, проценты): принимает и `20`, и `2.5`, и `79991234567L` |
| `boolean` | `Boolean` |
| `array` | `List<тип items>`; массив `number` в параметре — `List<? extends Number>` (примет и `List<Integer>`) |
| `$ref` на схему; `allOf [$ref X]` без своих свойств (ссылка с `description`/`nullable`) | DTO `X` |
| энум (`enum` схемы или свойства) | Java-энум: константы `E_BOOK`, исходное значение `e_book` — в JSON (`@JsonValue`/`@JsonCreator`) и в URL (`toString()`) |
| `$ref` на энум в query/path/header-параметре и у локатора | тот же энум; в URL уходит значение из спеки: `format=e_book`, а не `E_BOOK` |
| `allOf [$ref PageDto, {data: array of $ref X}]` в ответе | `PageDto<X>` |
| `object` без свойств, `anyOf`/`oneOf`, наследование через `allOf` | `JsonNode` (+ WARN) |

Целые по смыслу поля, объявленные как `number` (`total`, `page`), тоже становятся `Double` — лечится в спеке:
`@ApiProperty({ type: 'integer' })`. Такой `Double` можно сразу передать в параметр `Number`: core отправит
`42.0` как `42` (см. [Интерфейсы ресурсов](#интерфейсы-ресурсов)).

### Постраничные выборки

Ответ `allOf [$ref PageDto, {properties: {data: array of $ref X}}]` генератор узнаёт сам и делает конверт дженериком
`PageDto<T>` с полем `List<T> data`; ответ ручки — `PageDto<Book>`. Имена `PageDto` и `data` не зашиты.
Свой класс конверта — `--import PageDto=com.example.Page` (класс — дженерик с одним параметром).

### Два стиля DTO

У каждого DTO — геттеры/сеттеры, `toString`, `equals`/`hashCode` и билдер:

```java
Book book = Book.builder().title("Мастер и Маргарита").price(12.5).build();
Book discounted = book.toBuilder().price(9.99).build();
PageDto<Book> page = PageDto.<Book>builder().data(List.of(book)).total(1.0).build();
```

| | По умолчанию — обычная Java | `--lombok` |
|---|---|---|
| геттеры/сеттеры, `toString`, `equals`/`hashCode` | пишет генератор | `@Data` |
| `X.builder()…build()`, `toBuilder()`, класс `XBuilder` | пишет генератор | `@Builder(toBuilder = true)` |
| публичный конструктор | только без аргументов | `@NoArgsConstructor`; `@AllArgsConstructor(PRIVATE)` — для билдера |
| зависимости | нет | Lombok и явный `annotationProcessorPaths` (с JDK 23 javac не запускает процессоры с classpath) |

Публичный API у стилей одинаковый — это проверяет `DtoStyleContractTest` на выводе обоих.

### Раскладка model по пакетам

С `--split-model` схема, до которой через `$ref` доходят ручки одной группы путей, идёт в `model.<группа>`
(имя пакета как у `resources.<группа>`), общая для нескольких групп или ничья — в корень `model`.
Пакет зависит от использования: если DTO понадобится второй группе, он переедет в корень. Закрепить —
`--model-package BookSyncItemDto=books`.

### Ограничения

- Авторизация и `securitySchemes` не генерятся — фильтры подключаются в клиенте.
- Cookie-параметры — WARN и пропуск.
- Наследование через `allOf`, `oneOf`/`anyOf` без дискриминатора — `JsonNode`.

---

# jaxrs-assured-integration-tests

Интеграционные тесты: учебная спека [docs/openapi/bookstore.json](docs/openapi/bookstore.json) — книжный магазин,
в котором собраны формы спек @nestjs/swagger, — генерируется плагином из `jaxrs-assured-codegen` четыре раза:

| Запуск | Что | Тесты |
|---|---|---|
| `plain` | обычная Java, плоский model | `GeneratedCodeTest` — интерфейсы, аннотации, типы ответов, Jackson |
| `split` | `splitModel` | `SplitModelTest` — раскладка по пакетам, ссылки между пакетами |
| `lombok` | `lombok` | `DtoStyleContractTest` — один контракт для обоих стилей |
| `e2e` | `superInterface` = `ConfigurationResource` | `EndToEndTest` — сгенерированный клиент на core против локального сервера |

## Лицензия

[Apache License 2.0](LICENSE)
