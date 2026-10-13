package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoWriterTest {

    private static final DtoModel REGION = new DtoModel("gen.model", "RegionResponseDto", "Регион.\nОписание.",
            List.of(),
            List.of(new FieldModel("opus_id", "opusId", TypeName.INT.box(), true, "Идентификатор в ОПУС"),
                    new FieldModel("code", "code", TypeName.get(String.class), false, null)),
            List.of(), null);

    private final DtoWriter writer = new DtoWriter(new PlainDtoStyle());
    private final DtoWriter lombokWriter = new DtoWriter(new LombokDtoStyle());

    @Test
    void factoryPicksStyleByConfig() {
        GenConfig plain = new GenConfig(Path.of("spec.json"), Path.of("out"), "gen", "api/v1", "V1Resource",
                List.of(), null, Map.of());
        GenConfig lombok = new GenConfig(Path.of("spec.json"), Path.of("out"), "gen", "api/v1", "V1Resource",
                List.of(), null, Map.of(), true, false, Map.of());
        assertInstanceOf(PlainDtoStyle.class, DtoStyle.of(plain));
        assertInstanceOf(LombokDtoStyle.class, DtoStyle.of(lombok));
    }

    @Test
    void plainStyleWritesBuilderEqualsAndToString() {
        String source = writer.render(REGION);

        assertTrue(source.contains("public static RegionResponseDtoBuilder builder()"));
        assertTrue(source.contains("public RegionResponseDtoBuilder toBuilder()"));
        assertTrue(source.contains("public static class RegionResponseDtoBuilder"));
        assertTrue(source.contains("public RegionResponseDtoBuilder opusId(Integer opusId)"));
        assertTrue(source.contains("public RegionResponseDto build()"));
        assertTrue(source.contains("Objects.equals(this.opusId, other.opusId)"));
        assertTrue(source.contains("return Objects.hash(opusId, code)"));
        assertTrue(source.contains("\"RegionResponseDto(\""));
        assertFalse(source.contains("lombok"));
    }

    @Test
    void plainStyleSurvivesFieldsNamedLikeLocals() {
        // поле может называться result (как в OperationStatusDto); локальные переменные equals/build не должны его перекрывать
        DtoModel model = new DtoModel("gen.model", "OperationStatusDto", null, List.of(),
                List.of(new FieldModel("result", "result", TypeName.get(String.class), false, null),
                        new FieldModel("other", "other", TypeName.get(String.class), false, null),
                        new FieldModel("o", "o", TypeName.get(String.class), false, null)),
                List.of(), null);

        String source = writer.render(model);

        assertTrue(source.contains("result.result = this.result;"), source);
        assertTrue(source.contains("Objects.equals(this.other, other.other)"));
        assertTrue(source.contains("Objects.equals(this.o, other.o)"));
    }

    @Test
    void lombokStyleWritesAnnotationsInsteadOfMethods() {
        String source = lombokWriter.render(REGION);

        assertTrue(source.contains("import lombok.Data;"));
        assertTrue(source.contains("@Data"));
        assertTrue(source.contains("toBuilder = true"));
        assertTrue(source.contains("@NoArgsConstructor"));
        assertTrue(source.contains("access = AccessLevel.PRIVATE"));
        // общее со стилем Plain — Jackson-аннотации, поля, javadoc
        assertTrue(source.contains("@JsonProperty(\"opus_id\")"));
        assertTrue(source.contains("private Integer opusId;"));
        assertTrue(source.contains("Сгенерировано jaxrs-assured-codegen из OpenAPI-спеки"));
        assertFalse(source.contains("getOpusId()"), "Аксессоры даёт Lombok");
        assertFalse(source.contains("builder()"), "Билдер даёт Lombok");
    }

    @Test
    void rendersPojoWithJacksonAnnotationsAndAccessors() {
        String source = writer.render(REGION);

        assertTrue(source.contains("public class RegionResponseDto"));
        assertTrue(source.contains("@JsonInclude(JsonInclude.Include.NON_NULL)"));
        assertTrue(source.contains("ignoreUnknown = true"));
        assertTrue(source.contains("@JsonProperty(\"opus_id\")"));
        assertFalse(source.contains("@JsonProperty(\"code\")"), "camelCase-полю аннотация не нужна");
        assertTrue(source.contains("(обязательно)"));
        assertTrue(source.contains("(опционально)"));
        assertTrue(source.contains("public Integer getOpusId()"));
        assertTrue(source.contains("public void setOpusId(Integer opusId)"));
        assertTrue(source.contains("public String getCode()"));
        assertTrue(source.contains("Сгенерировано jaxrs-assured-codegen из OpenAPI-спеки"));
    }

    @Test
    void rendersEnumWithRoundTrip() {
        DtoModel model = new DtoModel("gen.model", "ProbeResultDto", null, List.of(), List.of(),
                List.of(new EnumModel("Status", List.of(
                        new EnumModel.Constant("OK", "ok"),
                        new EnumModel.Constant("SHUTTING_DOWN", "shutting_down")))),
                null);

        String source = writer.render(model);

        assertTrue(source.contains("public enum Status"));
        assertTrue(source.contains("OK(\"ok\")"));
        assertTrue(source.contains("SHUTTING_DOWN(\"shutting_down\")"));
        assertTrue(source.contains("@JsonValue"));
        assertTrue(source.contains("@JsonCreator"));
        assertTrue(source.contains("public static Status fromValue(String value)"));
        assertTrue(source.contains("Неизвестное значение Status: "));
        // toString — исходное значение: так энум-параметр уходит в URL
        assertTrue(source.replaceAll("\\s+", " ").contains("public String toString() { return value; }"), source);
    }

    @Test
    void rendersTopLevelEnum() {
        DtoModel model = new DtoModel("gen.model", "DeliveryCategory", null, List.of(), List.of(), List.of(),
                new EnumModel("DeliveryCategory", List.of(new EnumModel.Constant("BUSINESS", "business"))));

        String source = writer.render(model);

        assertTrue(source.contains("public enum DeliveryCategory"));
        assertTrue(source.contains("BUSINESS(\"business\")"));
    }

    @Test
    void rendersGenericWrapper() {
        TypeName listOfT = ParameterizedTypeName.get(ClassName.get(List.class), TypeVariableName.get("T"));
        DtoModel model = new DtoModel("gen.model", "PageDto", "Конверт выборки.", List.of("T"),
                List.of(new FieldModel("data", "data", listOfT, true, null),
                        new FieldModel("total", "total", TypeName.INT.box(), true, null)),
                List.of(), null);

        String source = writer.render(model);

        assertTrue(source.contains("public class PageDto<T>"));
        assertTrue(source.contains("private List<T> data;"));
        assertTrue(source.contains("public List<T> getData()"));
        assertTrue(source.contains("public void setData(List<T> data)"));
        assertTrue(source.contains("public Integer getTotal()"));
        // билдер дженерика — как у Lombok: PageDto.<X>builder().data(...).build()
        assertTrue(source.contains("public static <T> PageDtoBuilder<T> builder()"));
        assertTrue(source.contains("public static class PageDtoBuilder<T>"));
        assertTrue(source.contains("public PageDtoBuilder<T> data(List<T> data)"));
        assertTrue(source.contains("public PageDto<T> build()"));
        assertTrue(source.contains("PageDto<?> other = (PageDto<?>) o;"));
    }
}
