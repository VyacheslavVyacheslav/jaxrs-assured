package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.parameters;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;

/**
 * Дробные числа в параметрах запроса (query, path, header, cookie, значения локаторов) — без лишнего:
 * 42.0 → 42, 1.0E-4 → 0.0001, 1.0E7 → 10000000, BigDecimal 2.50 → 2.5.
 * Без него Double.toString() отправил бы «42.0» и экспоненту, и сервер, который ждет целое (например, id,
 * взятый из DTO, где number стал Double), ответил бы 400 или 404.
 * RestEasy подбирает конвертер по фактическому классу значения, поэтому конвертер работает и для параметров
 * типа Number. Целые числа он не трогает, NaN и бесконечности уходят как есть.
 * setQueryParam значения не конвертирует: там они уходят строкой как есть.
 */
public class DecimalParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType == Double.class || rawType == Float.class || rawType == BigDecimal.class) {
            return (ParamConverter<T>) new DecimalParamConverter(rawType);
        }
        return null;
    }

    private record DecimalParamConverter(Class<?> type) implements ParamConverter<Object> {

        @Override
        public Object fromString(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Value can not be null");
            }
            if (type == Double.class) {
                return Double.valueOf(value);
            }
            return type == Float.class ? Float.valueOf(value) : new BigDecimal(value);
        }

        @Override
        public String toString(Object value) {
            if (value == null) {
                throw new IllegalArgumentException("Value can not be null");
            }
            if (value instanceof Double d && (d.isNaN() || d.isInfinite())
                    || value instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return value.toString();
            }
            // через toString, а не new BigDecimal(double): 0.1 остается 0.1, без двоичного хвоста
            BigDecimal decimal = value instanceof BigDecimal bigDecimal ? bigDecimal : new BigDecimal(value.toString());
            return decimal.stripTrailingZeros().toPlainString();
        }
    }
}
