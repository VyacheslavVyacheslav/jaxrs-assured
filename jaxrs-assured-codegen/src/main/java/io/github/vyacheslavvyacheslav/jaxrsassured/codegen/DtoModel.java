package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import com.squareup.javapoet.ClassName;

import java.util.List;

/**
 * Модель DTO: POJO (поля + вложенные энумы) либо чисто-энумная схема (enumSelf).
 * typeVariables — типовые переменные дженерик-обёртки (PageDto&lt;T&gt;), у прочих пусто.
 * enclosingClass — внешний класс у вложенного POJO (бин query-параметров живет внутри интерфейса ресурса),
 * null — класс верхнего уровня.
 */
public record DtoModel(String packageName, String className, String description, List<String> typeVariables,
                       List<FieldModel> fields, List<EnumModel> nestedEnums, EnumModel enumSelf,
                       String enclosingClass) {

    public DtoModel {
        typeVariables = List.copyOf(typeVariables);
        fields = List.copyOf(fields);
        nestedEnums = List.copyOf(nestedEnums);
    }

    /** Класс верхнего уровня. */
    public DtoModel(String packageName, String className, String description, List<String> typeVariables,
                    List<FieldModel> fields, List<EnumModel> nestedEnums, EnumModel enumSelf) {
        this(packageName, className, description, typeVariables, fields, nestedEnums, enumSelf, null);
    }

    public boolean isEnum() {
        return enumSelf != null;
    }

    /** pkg.X или, у вложенного, pkg.Внешний.X. */
    public ClassName selfClass() {
        return enclosingClass == null ? ClassName.get(packageName, className)
                : ClassName.get(packageName, enclosingClass, className);
    }
}
