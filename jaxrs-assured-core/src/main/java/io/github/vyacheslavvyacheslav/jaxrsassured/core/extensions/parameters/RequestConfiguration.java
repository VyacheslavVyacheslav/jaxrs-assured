package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Неизменяемый набор настроек запроса, заданных через ConfigurationResource: заголовки, cookie, query-параметры, тело.
 * Каждое изменение порождает новый экземпляр, исходный не меняется.
 */
public final class RequestConfiguration {

    /**
     * Свойство запроса, через которое настройка доходит до RequestConfigurationFilter.
     */
    public static final String PROPERTY = RequestConfiguration.class.getName();

    public static final RequestConfiguration EMPTY = new RequestConfiguration(List.of());

    private final List<RequestSetting> settings;

    private RequestConfiguration(List<RequestSetting> settings) {
        this.settings = settings;
    }

    /**
     * Повторная настройка с тем же ключом (тот же заголовок без учета регистра, та же cookie, тот же параметр,
     * тело) заменяет предыдущую.
     */
    public RequestConfiguration with(RequestSetting setting) {
        List<RequestSetting> updated = new ArrayList<>(settings);
        updated.removeIf(existing -> existing.key().equals(setting.key()));
        updated.add(setting);
        return new RequestConfiguration(List.copyOf(updated));
    }

    public boolean isEmpty() {
        return settings.isEmpty();
    }

    /**
     * Тело — последним, чтобы взять итоговый Content-Type.
     */
    public void applyTo(ClientRequestContext request) {
        settings.stream().filter(setting -> !setting.isBody()).forEach(setting -> setting.apply(request));
        settings.stream().filter(RequestSetting::isBody).forEach(setting -> setting.apply(request));
    }

    /**
     * Например {@code [X-Actor=qa, -Authorization, cookie:session=abc, query:limit=abc, body={"bad"…]}:
     * заданные значения и удаленные (с минусом).
     */
    @Override
    public String toString() {
        return settings.toString();
    }
}
