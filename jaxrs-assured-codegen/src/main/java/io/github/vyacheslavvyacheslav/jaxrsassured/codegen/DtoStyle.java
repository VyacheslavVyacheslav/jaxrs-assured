package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.List;

/**
 * Как дописать POJO: аксессоры, toString/equals/hashCode, билдер. Общее — класс, Jackson-аннотации,
 * поля, энумы — печатает DtoWriter; стиль добавляет только своё.
 * <p>
 * Оба стиля дают один публичный API, чтобы код тестов не зависел от флага:
 * {@code getX()/setX()}, {@code toString()} вида {@code X(a=1, b=null)}, {@code equals/hashCode} по полям,
 * {@code X.builder().a(1).build()} с классом {@code XBuilder}, {@code x.toBuilder()}; публичный конструктор —
 * только без аргументов (его же использует Jackson). Совпадение API проверяет DtoStyleContractTest
 * на выводе обоих стилей в интеграционных тестах.
 */
interface DtoStyle {

    /** Фабрика: стиль по конфигурации генерации. */
    static DtoStyle of(GenConfig config) {
        return config.lombok() ? new LombokDtoStyle() : new PlainDtoStyle();
    }

    /** Дописать класс DTO (поля уже добавлены). */
    void complete(TypeSpec.Builder type, DtoModel model);

    /** Тип самого DTO: X или X&lt;T&gt; у дженерик-обёртки. */
    static TypeName selfType(DtoModel model) {
        return parameterized(ClassName.get(model.packageName(), model.className()), model);
    }

    /** Класс билдера — XBuilder, как у Lombok @Builder. */
    static ClassName builderClass(DtoModel model) {
        return ClassName.get(model.packageName(), model.className(), model.className() + "Builder");
    }

    static TypeName parameterized(ClassName raw, DtoModel model) {
        if (model.typeVariables().isEmpty()) {
            return raw;
        }
        return ParameterizedTypeName.get(raw, typeVariables(model).toArray(TypeName[]::new));
    }

    static List<TypeVariableName> typeVariables(DtoModel model) {
        return model.typeVariables().stream().map(TypeVariableName::get).toList();
    }
}
