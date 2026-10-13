package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MediaType;

import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Подменяет тело запроса строкой как есть — например, битым JSON вместо DTO.
 * Content-Type остается итоговым (из @Consumes или setHeader), без него — application/octet-stream.
 * Тело уходит массивом байт в кодировке из Content-Type: кодировку задаем сами, и результат не зависит от того,
 * какие провайдеры зарегистрированы для String.
 * Для GET RestEasy тело не отправит и бросит ошибку.
 */
public class SetBody implements RequestSetting {

    private static final int PREVIEW_LENGTH = 40;

    private final String body;

    public SetBody(String body) {
        this.body = Objects.requireNonNull(body, "Body can not be null");
    }

    @Override
    public String key() {
        return "body";
    }

    @Override
    public boolean isBody() {
        return true;
    }

    @Override
    public void apply(ClientRequestContext request) {
        MediaType mediaType = request.getMediaType() != null ? request.getMediaType() : MediaType.APPLICATION_OCTET_STREAM_TYPE;
        request.setEntity(body.getBytes(charset(mediaType)), new Annotation[0], mediaType);
    }

    @Override
    public String toString() {
        return "body=" + (body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) + "…" : body);
    }

    private static Charset charset(MediaType mediaType) {
        String charset = mediaType.getParameters().get(MediaType.CHARSET_PARAMETER);
        return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
    }
}
