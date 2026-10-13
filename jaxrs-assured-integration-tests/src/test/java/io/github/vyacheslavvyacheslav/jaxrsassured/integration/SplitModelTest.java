package io.github.vyacheslavvyacheslav.jaxrsassured.integration;

import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.Address;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.ApiErrorDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.Book;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.BookFormat;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.Genre;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.LegacyBookDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.PageDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.authors.Author;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.books.BookSyncItemDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.books.SyncBooksDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.giftcards.GiftCardDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.history.HistoryEntryDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.orders.OrderDto;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model.orders.OrderStatus;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.resources.books.BookSnapshotsResource;
import io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.resources.giftcards.GiftCardsResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Раскладка DTO по пакетам: bookstore.json с splitModel (execution split): DTO по подпакетам групп путей.
 * Пакеты проверяет уже компилятор — импорты выше; тест закрепляет правило и ссылки между пакетами.
 */
class SplitModelTest {

    private static final String MODEL = "io.github.vyacheslavvyacheslav.jaxrsassured.integration.split.model";

    @Test
    void sharedAndUnusedSchemasStayInModelRoot() {
        // PageDto, ApiErrorDto — почти всем группам; Book — books и authors; Genre — books, catalog, authors;
        // Address — authors и orders; LegacyBookDto — ни одной ручке
        for (Class<?> shared : List.of(PageDto.class, ApiErrorDto.class, Book.class, Genre.class, BookFormat.class,
                Address.class, LegacyBookDto.class)) {
            assertEquals(MODEL, shared.getPackageName(), shared.getSimpleName());
        }
    }

    @Test
    void groupSchemasGoToGroupPackage() {
        assertEquals(MODEL + ".books", SyncBooksDto.class.getPackageName());
        assertEquals(MODEL + ".books", BookSyncItemDto.class.getPackageName(), "Транзитивно, через SyncBooksDto");
        assertEquals(MODEL + ".authors", Author.class.getPackageName());
        assertEquals(MODEL + ".orders", OrderDto.class.getPackageName());
        assertEquals(MODEL + ".orders", OrderStatus.class.getPackageName());
        assertEquals(MODEL + ".history", HistoryEntryDto.class.getPackageName());
        // группа с дефисом — имя пакета как у resources.giftcards
        assertEquals(MODEL + ".giftcards", GiftCardDto.class.getPackageName());
        assertEquals(MODEL.replace(".model", ".resources") + ".giftcards", GiftCardsResource.class.getPackageName());
    }

    @Test
    void referencesCrossPackages() throws Exception {
        // группа → корень и корень → группа
        assertEquals(Address.class, OrderDto.class.getDeclaredField("deliveryAddress").getType());
        assertEquals(BookSyncItemDto.class, LegacyBookDto.class.getDeclaredField("syncItem").getType());
        // ресурс группы books — с телом из model.books и ответом из model.books
        assertEquals(MODEL + ".books",
                BookSnapshotsResource.class.getMethod("sync", SyncBooksDto.class).getReturnType().getPackageName());
    }
}
