package io.github.vyacheslavvyacheslav.jaxrsassured.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.V1Resource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.Address;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.ApiErrorDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.ArchiveRequestDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.Author;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.Book;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.BookFormat;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.CreateBookDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.Genre;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.HistoryEntryDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.HistoryFieldChangeDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.OperationStatusDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.OrderDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.OrderStatus;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.PageDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.PatchBookDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.SnapshotSyncResultDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.model.SyncBooksDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.authors.AuthorIdBooksResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.authors.AuthorIdResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.authors.AuthorPhotoResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.authors.AuthorResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.authors.AuthorsResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.books.BookSnapshotsResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.books.BooksResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.catalog.CatalogGenresResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.catalog.CatalogResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.events.EventOrdersResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.giftcards.GiftCardsResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.orders.OrderReceiptResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.reports.ReportArchivesResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain.resources.reports.ReportFileResource;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Сгенерированный код из docs/openapi/bookstore.json, обычная Java и плоский model (execution plain).
 * Сгенерированные классы компилируются вместе с тестом, поэтому имена классов, методов и типы параметров
 * проверяет уже компилятор — тест сверяет то, что он не ловит: аннотации JAX-RS, типы ответов, Jackson.
 */
class GeneratedCodeTest {

    private static final String BASE = "io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain";

    @Test
    void rootAndSubResourcesArePlainJaxRs() throws Exception {
        assertEquals("api/v1", V1Resource.class.getAnnotation(Path.class).value());
        assertEquals(0, V1Resource.class.getInterfaces().length, "Без superInterface интерфейсы без предков");
        assertEquals("books", V1Resource.class.getMethod("getBooksResource").getAnnotation(Path.class).value());
        assertEquals("gift-cards", V1Resource.class.getMethod("getGiftCardsResource").getAnnotation(Path.class).value());
        assertNull(BooksResource.class.getAnnotation(Path.class), "Подресурсы не несут class-level @Path");
        // путь вне api/v1 пропущен через ignorePaths
        assertThrows(ClassNotFoundException.class, () -> Class.forName(BASE + ".resources.health.HealthResource"));
    }

    @Test
    void booksFollowSpec() throws Exception {
        Method findAll = BooksResource.class.getMethod("findAll", Integer.class, Integer.class, Integer.class, String.class);
        assertEquals(List.of("query:page", "query:limit", "query:authorId", "query:genre"), params(findAll));
        assertEquals(BASE + ".model.PageDto<" + BASE + ".model.Book>", findAll.getGenericReturnType().getTypeName());

        // лист {id} инлайнится в BooksResource; number без format в пути — Integer, а не Double
        Method findOne = BooksResource.class.getMethod("findOne", Integer.class);
        assertEquals("{id}", findOne.getAnnotation(Path.class).value());
        assertEquals(List.of("path:id"), params(findOne));
        assertEquals(Book.class, findOne.getReturnType());

        Method create = BooksResource.class.getMethod("create", CreateBookDto.class);
        assertArrayEquals(new String[]{"application/json"}, create.getAnnotation(Consumes.class).value());
        assertEquals(Book.class, create.getReturnType());
        assertEquals(void.class, BooksResource.class.getMethod("remove", Integer.class).getReturnType());
        assertEquals(Book.class, BooksResource.class.getMethod("patch", Integer.class, PatchBookDto.class).getReturnType());

        // статический ребёнок — подресурс
        Method snapshots = BooksResource.class.getMethod("getSnapshotsResource");
        assertEquals("snapshots", snapshots.getAnnotation(Path.class).value());
        assertEquals(BookSnapshotsResource.class, snapshots.getReturnType());
        assertEquals(SnapshotSyncResultDto.class,
                BookSnapshotsResource.class.getMethod("sync", SyncBooksDto.class).getReturnType());
    }

    @Test
    void subResourceTreeFollowsSpec() throws Exception {
        assertEquals(CatalogGenresResource.class, CatalogResource.class.getMethod("getGenresResource").getReturnType());
        assertEquals(2, CatalogGenresResource.class.getDeclaredMethods().length);
        Method genres = CatalogGenresResource.class.getMethod("findAll", String.class);
        assertEquals(List.of("query:ids"), params(genres));
        assertEquals(BASE + ".model.PageDto<" + BASE + ".model.Genre>", genres.getGenericReturnType().getTypeName());

        // {id} с детьми — свой интерфейс за локатором; {authorId} — другой параметр, другой интерфейс
        Method author = AuthorsResource.class.getMethod("getAuthorResource", String.class);
        assertEquals("{id}", author.getAnnotation(Path.class).value());
        assertEquals(AuthorResource.class, author.getReturnType());
        assertEquals(AuthorPhotoResource.class, AuthorResource.class.getMethod("getPhotoResource").getReturnType());
        assertEquals(AuthorIdResource.class,
                AuthorsResource.class.getMethod("getAuthorIdResource", String.class).getReturnType());
        assertEquals(AuthorIdBooksResource.class, AuthorIdResource.class.getMethod("getBooksResource").getReturnType());

        // лист {code} — не {id}, но тоже инлайнится
        assertEquals("{code}", GiftCardsResource.class.getMethod("findOne", String.class).getAnnotation(Path.class).value());
    }

    @Test
    void nonJsonResponsesGetTypesAndProduces() throws Exception {
        Method photo = AuthorPhotoResource.class.getMethod("photo");
        assertEquals(byte[].class, photo.getReturnType());
        assertProduces("image/png", photo);

        Method file = ReportFileResource.class.getMethod("file");
        assertEquals(byte[].class, file.getReturnType());
        assertProduces("application/octet-stream", file);

        Method archive = ReportArchivesResource.class.getMethod("create", ArchiveRequestDto.class);
        assertEquals(byte[].class, archive.getReturnType());
        assertProduces("application/zip", archive);

        // SSE — поток, а не String; header last-event-id и query lastEventId дают разные java-имена
        Method events = EventOrdersResource.class.getMethod("orders", String.class, String.class, String.class);
        assertEquals(InputStream.class, events.getReturnType());
        assertProduces("text/event-stream", events);
        assertEquals(List.of("header:last-event-id", "query:lastEventId", "query:ticket"), params(events));

        // "text/html; charset=utf-8" → @Produces("text/html")
        Method receipt = OrderReceiptResource.class.getMethod("receipt");
        assertEquals(String.class, receipt.getReturnType());
        assertProduces("text/html", receipt);

        assertNull(BooksResource.class.getMethod("findOne", Integer.class).getAnnotation(Produces.class),
                "JSON — умолчание, без @Produces");
    }

    @Test
    void pageAndErrorAreGeneratedFromSpec() throws Exception {
        assertEquals("T", PageDto.class.getTypeParameters()[0].getName());
        assertEquals("java.util.List<T>", PageDto.class.getDeclaredField("data").getGenericType().getTypeName());
        // number без format — Double
        assertEquals(Double.class, PageDto.class.getDeclaredField("total").getType());
        assertEquals(Double.class, ApiErrorDto.class.getDeclaredField("status").getType());
        assertEquals(JsonNode.class, ApiErrorDto.class.getDeclaredField("details").getType());
    }

    @Test
    void pageDeserializesTypedRecords() throws Exception {
        PageDto<Genre> page = new ObjectMapper().readValue("""
                {"data": [{"code": "FICTION", "title": "Художественная литература", "displayOrder": 1, "unknown": true}],
                 "total": 1, "page": 1, "limit": 20}
                """, new TypeReference<>() {
        });

        assertEquals(Genre.Code.FICTION, page.getData().get(0).getCode(), "Записи data — типизированные DTO");
        assertEquals(1.0, page.getTotal());
    }

    @Test
    void refsWithDescriptionAreTypedFields() throws Exception {
        // {description, allOf: [$ref X]} — так NestJS пишет $ref с описанием или nullable
        assertEquals(Genre.class, Book.class.getDeclaredField("genre").getType());
        assertEquals(Address.class, Author.class.getDeclaredField("address").getType());
        assertEquals(Address.class, OrderDto.class.getDeclaredField("deliveryAddress").getType());
        assertEquals(OrderStatus.class, OrderDto.class.getDeclaredField("status").getType());
    }

    @Test
    void numbersAndEnumsSurviveJackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Book book = mapper.readValue("""
                {"id": 7, "title": "Мастер и Маргарита", "price": 12.5, "format": "e_book", "tags": ["bestseller"]}
                """, Book.class);

        assertEquals(12.5, book.getPrice(), "number без format — Double, дробь не теряется");
        assertEquals(BookFormat.E_BOOK, book.getFormat());
        assertEquals(List.of(Book.Tags.BESTSELLER), book.getTags());
        // исходное значение энума уходит обратно как было
        String json = mapper.writeValueAsString(book);
        assertEquals("e_book", mapper.readTree(json).get("format").asText());

        Address address = mapper.readValue("{\"city\": \"Москва\", \"lat\": 55.7558, \"lng\": 37.6173}", Address.class);
        assertEquals(55.7558, address.getLat());
        assertEquals(37.6173, address.getLng());
    }

    @Test
    void unsupportedShapesFallBackToJsonNode() throws Exception {
        // anyOf [string, number, boolean] и free-form объекты — JsonNode, честный тип для произвольного значения
        assertEquals(JsonNode.class, HistoryFieldChangeDto.class.getDeclaredField("from").getType());
        assertEquals(JsonNode.class, HistoryEntryDto.class.getDeclaredField("snapshot").getType());
        assertEquals(JsonNode.class, OperationStatusDto.class.getDeclaredField("result").getType());
    }

    @Test
    void fieldsNamedLikeLocalsWork() {
        // поля result и other — тёзки локальных переменных в equals и build
        OperationStatusDto status = OperationStatusDto.builder().id("op-1").other("x").build();
        assertEquals("x", status.getOther());
        assertEquals(status, OperationStatusDto.builder().id("op-1").other("x").build());
        assertFalse(status.equals(status.toBuilder().other("y").build()));
        assertNotNull(status.toString());
    }

    /** Параметры метода как «где:имя» по аннотациям JAX-RS. */
    private static List<String> params(Method method) {
        return Arrays.stream(method.getParameters()).map(GeneratedCodeTest::param).toList();
    }

    private static String param(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation instanceof QueryParam query) {
                return "query:" + query.value();
            }
            if (annotation instanceof PathParam path) {
                return "path:" + path.value();
            }
            if (annotation instanceof HeaderParam header) {
                return "header:" + header.value();
            }
        }
        return "body";
    }

    private static void assertProduces(String expected, Method method) {
        Produces produces = method.getAnnotation(Produces.class);
        assertNotNull(produces, "Нет @Produces у " + method.getName());
        assertArrayEquals(new String[]{expected}, produces.value());
    }
}
