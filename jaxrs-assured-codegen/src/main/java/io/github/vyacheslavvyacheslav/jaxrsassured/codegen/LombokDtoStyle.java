package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

/**
 * DTO на Lombok: {@code @Data @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor(access = PRIVATE)}.
 * <ul>
 *     <li>@Data — геттеры/сеттеры, toString {@code X(a=1, b=null)}, equals/hashCode;</li>
 *     <li>@NoArgsConstructor — для Jackson (он заполняет DTO сеттерами, как и в обычном стиле);</li>
 *     <li>@AllArgsConstructor(PRIVATE) — нужен @Builder, но наружу не торчит: публичный API как у PlainDtoStyle.</li>
 * </ul>
 * Сам генератор от Lombok не зависит — имена аннотаций строками; Lombok нужен проекту, который компилирует вывод.
 */
final class LombokDtoStyle implements DtoStyle {

    private static final ClassName DATA = ClassName.get("lombok", "Data");
    private static final ClassName BUILDER = ClassName.get("lombok", "Builder");
    private static final ClassName NO_ARGS_CONSTRUCTOR = ClassName.get("lombok", "NoArgsConstructor");
    private static final ClassName ALL_ARGS_CONSTRUCTOR = ClassName.get("lombok", "AllArgsConstructor");
    private static final ClassName ACCESS_LEVEL = ClassName.get("lombok", "AccessLevel");

    @Override
    public void complete(TypeSpec.Builder type, DtoModel model) {
        type.addAnnotation(DATA)
                .addAnnotation(AnnotationSpec.builder(BUILDER).addMember("toBuilder", "$L", true).build())
                .addAnnotation(NO_ARGS_CONSTRUCTOR);
        if (!model.fields().isEmpty()) {
            // без полей конструктор «со всеми полями» совпал бы с конструктором без аргументов
            type.addAnnotation(AnnotationSpec.builder(ALL_ARGS_CONSTRUCTOR)
                    .addMember("access", "$T.PRIVATE", ACCESS_LEVEL).build());
        }
    }
}
