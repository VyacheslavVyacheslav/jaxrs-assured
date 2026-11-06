package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.WebTarget;

import java.util.Objects;

/**
 * Задает значение параметра пути {name}, заменяя и значение из @PathParam метода, и значение, переданное в локатор
 * подресурса. Значение — строка как есть, кодируется как сегмент пути (пробел — %20, «/» — %2F):
 * можно отправить то, что не пропускает типизированный параметр.
 * Подставляется в шаблон пути до создания запроса (resolvePath), к готовому запросу в фильтре ничего не добавляет.
 * Если в пути вызова нет {name}, на вызов не влияет.
 */
public class SetPathParam implements RequestSetting {

    private final String name;
    private final String value;

    public SetPathParam(String name, Object value) {
        this.name = Objects.requireNonNull(name, "Path param name can not be null");
        this.value = String.valueOf(Objects.requireNonNull(value, "Path param value can not be null"));
    }

    @Override
    public String key() {
        return "path:" + name;
    }

    @Override
    public WebTarget resolvePath(WebTarget target) {
        return target.resolveTemplate(name, value);
    }

    @Override
    public void apply(ClientRequestContext request) {
        // значение уже в пути: подставлено в resolvePath до создания запроса
    }

    @Override
    public String toString() {
        return "path:" + name + "=" + value;
    }
}
