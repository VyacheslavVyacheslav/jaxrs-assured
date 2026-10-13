package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.extractors;

import io.restassured.config.LogConfig;
import io.restassured.internal.support.Prettifier;
import io.restassured.parsing.Parser;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.response.RequestLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отправленный запрос в формате лога rest-assured (RequestPrinter): метод, URI, заголовки, тело.
 * Заголовки — итоговые, после фильтров и настройки ConfigurationResource; значения заголовков из черного списка
 * LogConfig скрываются. Тело заново сериализуется тем же MessageBodyWriter, что и при отправке.
 */
final class ClientInvocationRequestLog implements RequestLog {

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String NONE = "<none>";
    private static final String BLACKLISTED = "[ BLACKLISTED ]";

    private final ClientInvocation invocation;

    ClientInvocationRequestLog(ClientInvocation invocation) {
        this.invocation = invocation;
    }

    @Override
    public String print(LogConfig logConfig) {
        StringBuilder log = new StringBuilder();
        log.append("Request method:\t").append(invocation.getMethod()).append(LINE_SEPARATOR);
        log.append("Request URI:\t").append(invocation.getUri()).append(LINE_SEPARATOR);
        appendHeaders(log, logConfig.blacklistedHeaders());
        log.append("Body:");
        String body = body(logConfig.isPrettyPrintingEnabled());
        log.append(body == null ? "\t\t\t" + NONE : LINE_SEPARATOR + body);
        return log.toString();
    }

    private void appendHeaders(StringBuilder log, Set<String> blacklistedHeaders) {
        log.append("Headers:");
        boolean first = true;
        for (Map.Entry<String, List<String>> header : invocation.getHeaders().asMap().entrySet()) {
            for (String value : header.getValue()) {
                log.append(first ? "\t\t" : "\t\t\t\t").append(header.getKey()).append('=')
                        .append(blacklistedHeaders.contains(header.getKey()) ? BLACKLISTED : value)
                        .append(LINE_SEPARATOR);
                first = false;
            }
        }
        if (first) {
            log.append("\t\t").append(NONE).append(LINE_SEPARATOR);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String body(boolean prettyPrint) {
        Object entity = invocation.getEntity();
        if (entity == null) {
            return null;
        }
        MediaType mediaType = invocation.getHeaders().getMediaType();
        String body;
        try {
            MessageBodyWriter writer = invocation.getClientConfiguration().getMessageBodyWriter(invocation.getEntityClass(),
                    invocation.getEntityGenericType(), invocation.getEntityAnnotations(), mediaType);
            if (writer == null) {
                return String.valueOf(entity);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writer.writeTo(entity, invocation.getEntityClass(), invocation.getEntityGenericType(),
                    invocation.getEntityAnnotations(), mediaType, new MultivaluedHashMap<>(), out);
            body = out.toString(charset(mediaType));
        } catch (IOException | RuntimeException e) {
            // например, поток, который уже прочитан при отправке
            return String.valueOf(entity);
        }
        Parser parser = mediaType == null ? null : Parser.fromContentType(mediaType.toString());
        return prettyPrint && parser != null ? new Prettifier().prettify(body, parser) : body;
    }

    private static Charset charset(MediaType mediaType) {
        String charset = mediaType == null ? null : mediaType.getParameters().get(MediaType.CHARSET_PARAMETER);
        return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
    }
}
