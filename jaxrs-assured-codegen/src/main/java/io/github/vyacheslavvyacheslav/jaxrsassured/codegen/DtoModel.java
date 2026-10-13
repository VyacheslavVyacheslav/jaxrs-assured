package io.github.vyacheslavvyacheslav.jaxrsassured.codegen;

import java.util.List;

/**
 * Модель DTO: POJO (поля + вложенные энумы) либо чисто-энумная схема (enumSelf).
 * typeVariables — типовые переменные дженерик-обёртки (PageDto&lt;T&gt;), у прочих пусто.
 */
public record DtoModel(String packageName, String className, String description, List<String> typeVariables,
                       List<FieldModel> fields, List<EnumModel> nestedEnums, EnumModel enumSelf) {

    public DtoModel {
        typeVariables = List.copyOf(typeVariables);
        fields = List.copyOf(fields);
        nestedEnums = List.copyOf(nestedEnums);
    }

    public boolean isEnum() {
        return enumSelf != null;
    }
}
