package io.github.vyacheslavvyacheslav.jaxrsassured.core.extensions.extractors;

import io.restassured.response.ValidatableResponse;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.DefaultEntityExtractorFactory;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.EntityExtractor;

import java.lang.reflect.Method;

public class AssuredResponseExtractorFactory extends DefaultEntityExtractorFactory {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EntityExtractor createExtractor(final Method method) {
        if (ValidatableResponse.class.isAssignableFrom(method.getReturnType())) {
            return new AssuredEntityExtractor();
        } else {
            return super.createExtractor(method);
        }
    }
}
