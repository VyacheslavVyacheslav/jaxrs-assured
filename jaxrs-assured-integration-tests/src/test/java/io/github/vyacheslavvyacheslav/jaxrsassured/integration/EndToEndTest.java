package io.github.vyacheslavvyacheslav.jaxrsassured.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.clients.BaseClient;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.V1Resource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.model.Book;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.model.BookFormat;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.model.CreateBookDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.model.Genre;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.model.PageDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.e2e.resources.books.BooksResource;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сквозной сценарий: интерфейсы, сгенерированные из bookstore.json с superInterface ConfigurationResource
 * (execution e2e), работают на прокси из jaxrs-assured-core против локального HTTP-сервера —
 * типизированные DTO, настройка запроса на ходу, assured-проверки, тело запроса из билдера.
 */
class EndToEndTest {

    private static final String BOOK = """
            {"id": 7, "title": "Мастер и Маргарита", "price": 12.5, "format": "e_book", "tags": ["bestseller"],
             "genre": {"code": "FICTION", "title": "Художественная литература", "displayOrder": 1, "active": true,
                       "createdAt": "2026-01-01T00:00:00Z"}}
            """;

    /** Клиент сервиса — как в проекте-потребителе: наследник BaseClient, один на прогон. */
    static final class BookstoreClient extends BaseClient {

        V1Resource api(String baseUri) {
            return proxy(V1Resource.class, baseUri);
        }
    }

    private final BookstoreClient client = new BookstoreClient();
    private HttpServer server;
    private V1Resource api;

    /** Последний запрос, который увидел сервер. */
    private volatile String lastUri;
    private volatile String lastActor;
    private volatile String lastBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/books", this::books);
        server.createContext("/api/v1/authors", this::authorBooks);
        server.start();
        api = client.api("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    void methodReturnsTypedDto() {
        Book book = api.getBooksResource().findOne(7);

        assertEquals("/api/v1/books/7", lastUri);
        assertEquals("Мастер и Маргарита", book.getTitle());
        assertEquals(12.5, book.getPrice());
        assertEquals(BookFormat.E_BOOK, book.getFormat());
        assertEquals(List.of(Book.Tags.BESTSELLER), book.getTags());
        assertEquals(Genre.Code.FICTION, book.getGenre().getCode());
    }

    @Test
    void pageIsTyped() {
        PageDto<Book> page = api.getBooksResource().findAll(1, 20, null, "FICTION");

        assertEquals("Мастер и Маргарита", page.getData().get(0).getTitle());
        assertEquals(1.0, page.getTotal());
        Map<String, String> query = query(lastUri);
        assertEquals(Map.of("page", "1", "limit", "20", "genre", "FICTION"), query, "null-параметр не уходит");
    }

    @Test
    void enumParameterGoesAsSpecValue() {
        // параметр — $ref на энум BookFormat, значения в спеке не совпадают с именами констант (e_book / E_BOOK)
        PageDto<Book> page = api.getAuthorsResource().getAuthorIdResource("a1").getBooksResource()
                .findAll(1, BookFormat.E_BOOK);

        assertEquals("/api/v1/authors/a1/books", lastUri.substring(0, lastUri.indexOf('?')));
        assertEquals("e_book", query(lastUri).get("format"), "В URL — значение из спеки, а не имя константы");
        assertEquals(BookFormat.E_BOOK, page.getData().get(0).getFormat());
    }

    @Test
    void errorStatusIsCheckedThroughAssured() {
        BooksResource books = api.getBooksResource();

        assertThrows(NotFoundException.class, () -> books.findOne(404));
        books.assured(r -> r.findOne(404))
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void configurationBelongsToResource() {
        BooksResource configured = api.getBooksResource().setHeader("X-Actor", "qa");

        configured.findOne(7);
        assertEquals("qa", lastActor);

        api.getBooksResource().findOne(7);
        assertNull(lastActor, "Новый ресурс — без настроек");
    }

    @Test
    void negativeCheckOverridesTypedParameter() {
        api.getBooksResource()
                .assured(r -> r.setQueryParam("limit", "abc").findAll(1, 20, null, null))
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));

        assertEquals("abc", query(lastUri).get("limit"));
    }

    @Test
    void bodyFromBuilderGoesAsJson() throws Exception {
        Book created = api.getBooksResource().create(CreateBookDto.builder()
                .title("Новая книга")
                .price(9.99)
                .format(BookFormat.PAPERBACK)
                .build());

        assertEquals(100, created.getId());
        ObjectMapper mapper = new ObjectMapper();
        // null-поля не пишутся, энум — исходным значением
        assertEquals(mapper.readTree("{\"title\": \"Новая книга\", \"price\": 9.99, \"format\": \"paperback\"}"),
                mapper.readTree(lastBody));
        assertFalse(lastBody.contains("authorId"));
        assertTrue(created.getTitle().startsWith("Новая"));
    }

    /** Учебный сервер: ровно те ручки books, которые нужны сценариям. */
    private void books(HttpExchange exchange) throws IOException {
        lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastUri = exchange.getRequestURI().toString();
        lastActor = exchange.getRequestHeaders().getFirst("X-Actor");
        String path = exchange.getRequestURI().getPath();
        switch (exchange.getRequestMethod() + " " + path) {
            case "GET /api/v1/books/7" -> respond(exchange, 200, BOOK);
            case "GET /api/v1/books" -> {
                String limit = query(lastUri).get("limit");
                if (limit != null && !limit.matches("\\d+")) {
                    respond(exchange, 400, "{\"code\": \"VALIDATION_FAILED\", \"message\": \"limit must be an integer\","
                            + " \"status\": 400}");
                } else {
                    respond(exchange, 200, "{\"data\": [" + BOOK + "], \"total\": 1, \"page\": 1, \"limit\": 20}");
                }
            }
            case "POST /api/v1/books" -> respond(exchange, 201,
                    "{\"id\": 100, \"title\": \"Новая книга\", \"price\": 9.99, \"format\": \"paperback\"}");
            default -> respond(exchange, 404, "{\"code\": \"NOT_FOUND\", \"message\": \"Книги нет\", \"status\": 404}");
        }
    }

    /** GET /api/v1/authors/{authorId}/books — страница из одной книги. */
    private void authorBooks(HttpExchange exchange) throws IOException {
        lastUri = exchange.getRequestURI().toString();
        respond(exchange, 200, "{\"data\": [" + BOOK + "], \"total\": 1, \"page\": 1, \"limit\": 20}");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static Map<String, String> query(String uri) {
        int start = uri.indexOf('?');
        if (start < 0) {
            return Map.of();
        }
        return Arrays.stream(uri.substring(start + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair.length > 1 ? pair[1] : ""));
    }
}
