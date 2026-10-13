# jaxrs-assured

Типизированные JAX-RS-клиенты для API-тестов на Java. Обычные интерфейсы с `@GET`/`@Path` и DTO —
а в тесте у каждого ресурса есть настройка запроса на ходу и проверки ответа в стиле rest-assured.
Интерфейсы и DTO можно не писать руками, а сгенерировать из OpenAPI-спеки.

```java
BooksResource books = client.api().getBooksResource();

Book book = books.findOne(7);                                       // типизированный DTO

books.assured(r -> r.findOne(404))                                  // ответ для проверок вместо исключения
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));

books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null))
        .statusCode(400);                                           // негативная проверка мимо типов

Book created = books.create(CreateBookDto.builder().title("Новая книга").price(9.99).build());
```

| Модуль | Что это |
|---|---|
| `jaxrs-assured-core` | фреймворк: прокси RestEasy, `ConfigurationResource` (настройка запроса на ходу), `AssuredResource` (ответ для проверок rest-assured) |
| `jaxrs-assured-codegen` | генератор JAX-RS-интерфейсов и DTO из OpenAPI: Maven-плагин, CLI, исполняемый jar |
| `jaxrs-assured-integration-tests` | интеграционные тесты генератора и сквозной тест «сгенерированный клиент + core» на учебной спеке [docs/openapi/bookstore.json](docs/openapi/bookstore.json) |

Генератор от core не зависит: он выдаёт чистые JAX-RS-интерфейсы, пригодные для любого прокси-клиента
(RestEasy, Jersey, CXF). С core их связывает один параметр — `superInterface`.

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
  (`setHeader`, `setCookie`, `setQueryParam`, `setBody`, `reset`, …) и `assured`.
- Class-level `@Path` — только у корня; подресурсы получают путь от `@Path` на методе-локаторе.
- Метод с телом (`POST`/`PUT`/`PATCH`) обязан объявить `@Consumes` — на методе или интерфейсе; без него ресурс
  не построится (`RESTEASY004590`). Из `@Consumes` берётся только первое значение.

## Настройка запроса: заголовки, cookie, query-параметры, тело

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
| `setBody(raw)` | отправить вместо DTO строку как есть, с итоговым `Content-Type` |
| `reset()` | снять все настройки ресурса, включая унаследованные от родителя |

Главное применение — негативные проверки того, что не пропускают типизированные параметры:

```java
books.assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null)).statusCode(400);  // строка вместо Integer
books.assured(r -> r.deleteQueryParam("page").findAll(1, 20, null, null)).statusCode(400);       // без обязательного параметра
books.assured(r -> r.setCookie("session", "expired").findOne(7)).statusCode(401);
books.assured(r -> r.setBody("{\"price\": \"free\"}").patch(7, new PatchBookDto())).statusCode(400);  // битый JSON вместо DTO
```

- Настройки меняют только тот ресурс, на котором вызваны, и действуют на все его следующие вызовы.
  Возвращают этот же ресурс — можно писать отдельной строкой или в цепочку. Внутри `assured(...)` — только на этот вызов.
- Каждый `get...Resource()` возвращает **новый** ресурс с настройками родителя на момент получения.
- Повторная настройка заменяет предыдущую: того же заголовка (регистр имени не важен), той же cookie,
  того же параметра, тела. Несколько значений — в порядке объявления: `setQueryParam("status", "NEW", "PAID")`.
- Настройка применяется последней — после параметров метода (`@HeaderParam`, `@QueryParam`, `@CookieParam`, тела)
  и фильтров клиента, поэтому перекрывает и их: `deleteHeader("Authorization")` убирает токен `BearerAuthFilter`.
  Не настраиваются только заголовки самого HTTP-клиента (`Host`, `Content-Length`, `Connection`).
- `setBody` — по смыслу одноразовая настройка, удобнее внутри `assured(...)`. `Content-Type` берётся итоговый
  (из `@Consumes` или `setHeader`), кодировка — из его `charset`, по умолчанию UTF-8. Для GET тело не отправится.
- Cookie из ответов (`Set-Cookie`) клиент не запоминает: иначе cookie одного теста ушли бы в запросах другого.
- Текущую настройку показывает `toString()` ресурса: `BooksResource [X-Actor=qa, -Authorization, query:limit=abc]`.
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
- Имя метода — operationId без префикса контроллера (`BooksController_findOne` → `findOne`).
- Имя java-параметра сохраняет горбы (`X-Request-Id` → `xRequestId`); совпавшие в методе имена разводятся
  суффиксом: header `last-event-id` + query `lastEventId` → `lastEventId`, `lastEventIdQuery`.
- Тело запроса → параметр-DTO + `@Consumes` (первый media type).
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
| `number` в query/path/header-параметре | `Integer` — в URL уходит через `toString()`, `Double` дал бы `?limit=20.0` |
| `boolean` | `Boolean` |
| `array` | `List<тип items>` |
| `$ref` на схему; `allOf [$ref X]` без своих свойств (ссылка с `description`/`nullable`) | DTO `X` |
| энум (`enum` схемы или свойства) | Java-энум: константы `E_BOOK`, исходное значение `e_book` — в JSON (`@JsonValue`/`@JsonCreator`) и в URL (`toString()`) |
| `$ref` на энум в query/path/header-параметре | тот же энум; в URL уходит значение из спеки: `format=e_book`, а не `E_BOOK` |
| `allOf [$ref PageDto, {data: array of $ref X}]` в ответе | `PageDto<X>` |
| `object` без свойств, `anyOf`/`oneOf`, наследование через `allOf` | `JsonNode` (+ WARN) |

Целые по смыслу поля, объявленные как `number` (`total`, `page`), тоже становятся `Double` — лечится в спеке:
`@ApiProperty({ type: 'integer' })`.

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
