package io.github.vyacheslavvyacheslav.jaxrsassured.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Один контракт DTO для обоих стилей: bookstore.json сгенерирован обычной Java (пакет integration.plain)
 * и с lombok (integration.lombok, скомпилирован настоящим Lombok). Код тестов не должен зависеть от стиля,
 * поэтому каждая проверка гоняется по обоим выводам, а bothStylesExposeSameApi сверяет публичные сигнатуры.
 * Типы в двух пакетах разные, поэтому здесь — рефлексия.
 */
class DtoStyleContractTest {

    private static final String PLAIN = "io.github.vyacheslavvyacheslav.jaxrsassured.integration.plain";
    private static final String LOMBOK = "io.github.vyacheslavvyacheslav.jaxrsassured.integration.lombok";

    @ParameterizedTest
    @ValueSource(strings = {PLAIN, LOMBOK})
    void builderBuildsAndToBuilderCopies(String base) throws Exception {
        Object genre = genre(base, "FICTION", 2.0);
        assertEquals("FICTION", ((Enum<?>) get(genre, "getCode")).name());
        assertEquals(2.0, get(genre, "getDisplayOrder"));

        // toBuilder — поменять одно поле у готового DTO, исходный не меняется
        Object changed = build(with(genre.getClass().getMethod("toBuilder").invoke(genre), "displayOrder", 5.0));
        assertEquals(5.0, get(changed, "getDisplayOrder"));
        assertEquals("FICTION", ((Enum<?>) get(changed, "getCode")).name());
        assertEquals(2.0, get(genre, "getDisplayOrder"));
    }

    @ParameterizedTest
    @ValueSource(strings = {PLAIN, LOMBOK})
    void equalsAndHashCodeCompareFields(String base) throws Exception {
        Object a = genre(base, "FICTION", 2.0);
        Object same = genre(base, "FICTION", 2.0);
        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, genre(base, "SCIENCE", 2.0));
        assertNotEquals(a, genre(base, "FICTION", 3.0));
        assertNotEquals(a, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {PLAIN, LOMBOK})
    void toStringListsAllFieldsInLombokFormat(String base) throws Exception {
        // одна и та же строка для обоих стилей — сообщения упавших тестов не зависят от стиля
        assertEquals("Genre(code=FICTION, title=null, titleEn=null, displayOrder=2.0, active=null, createdAt=null, "
                + "updatedAt=null)", genre(base, "FICTION", 2.0).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {PLAIN, LOMBOK})
    void genericPageFromBuilderRoundTripsThroughJackson(String base) throws Exception {
        Class<?> page = Class.forName(base + ".model.PageDto");
        Class<?> genre = Class.forName(base + ".model.Genre");
        Object builder = page.getMethod("builder").invoke(null);
        with(builder, "data", List.of(genre(base, "FICTION", 2.0)));
        with(builder, "total", 1.0);
        with(builder, "page", 1.0);
        with(builder, "limit", 20.0);
        Object value = build(builder);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(value);
        assertFalse(json.contains("titleEn"), "@JsonInclude(NON_NULL) — null-поля не пишутся: " + json);
        Object back = mapper.readValue(json, mapper.getTypeFactory().constructParametricType(page, genre));

        assertEquals(value, back);
    }

    @Test
    void bothStylesExposeSameApi() throws Exception {
        for (String type : List.of(".model.Genre", ".model.Genre$GenreBuilder", ".model.PageDto",
                ".model.PageDto$PageDtoBuilder", ".model.Book", ".model.OperationStatusDto")) {
            assertEquals(api(Class.forName(PLAIN + type)), api(Class.forName(LOMBOK + type)), "Публичный API " + type);
        }
        // публичный конструктор — только без аргументов (Jackson); всё остальное — через билдер
        for (String base : List.of(PLAIN, LOMBOK)) {
            List<Integer> constructors = Arrays.stream(Class.forName(base + ".model.Genre").getConstructors())
                    .map(Constructor::getParameterCount)
                    .toList();
            assertEquals(List.of(0), constructors, base);
        }
    }

    /** Genre.builder().code(code).displayOrder(order).build() */
    private static Object genre(String base, String code, double displayOrder) throws Exception {
        Object builder = Class.forName(base + ".model.Genre").getMethod("builder").invoke(null);
        with(builder, "code", constant(Class.forName(base + ".model.Genre$Code"), code));
        with(builder, "displayOrder", displayOrder);
        return build(builder);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumType, String name) {
        return Enum.valueOf((Class) enumType, name);
    }

    /** Fluent-метод билдера по имени поля; возвращает тот же билдер. */
    private static Object with(Object builder, String field, Object value) throws Exception {
        Method method = Arrays.stream(builder.getClass().getMethods())
                .filter(m -> m.getName().equals(field) && m.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("В билдере нет метода " + field));
        return method.invoke(builder, value);
    }

    private static Object build(Object builder) throws Exception {
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private static Object get(Object dto, String getter) throws Exception {
        return dto.getClass().getMethod(getter).invoke(dto);
    }

    /** Публичные методы (с унаследованными) как «static? Возврат имя(Параметры)» по простым именам типов. */
    private static Set<String> api(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .map(m -> (Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + m.getReturnType().getSimpleName() + " " + m.getName()
                        + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
                        .collect(Collectors.joining(", ", "(", ")")))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
