/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.data.jackson.JacksonSerializerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.ContextResolver;

import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.CommonProperties.getValue;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;
import static org.glassfish.jersey.internal.util.PropertiesHelper.getPropertyNameForRuntime;

/**
 * Feature enabling ServiceTalk Jackson serializer request/response content handling.
 */
public final class ServiceTalkJacksonSerializerFeature implements Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkJacksonSerializerFeature.class);
    // Only one JSON Feature can be registered per Jersey ApplicationHandler, so using simple name will not be a
    // cause of conflict even if several different shaded versions of ServiceTalk are in use, as each of them will
    // be registered to a different ApplicationHandler.
    static final String ST_JSON_FEATURE = ServiceTalkJacksonSerializerFeature.class.getSimpleName();

    @Override
    public boolean configure(final FeatureContext context) {
        final Configuration config = context.getConfiguration();

        final String jsonFeature = getValue(config.getProperties(), config.getRuntimeType(),
                JSON_FEATURE, ST_JSON_FEATURE, String.class);

        // Do not register our JSON feature if another one is already registered
        if (!ST_JSON_FEATURE.equalsIgnoreCase(jsonFeature)) {
            LOGGER.warn("Skipping registration of: {} as JSON support is already provided by: {}",
                    ST_JSON_FEATURE, jsonFeature);
            return false;
        }

        // Prevent other not yet registered JSON features to register themselves
        context.property(getPropertyNameForRuntime(JSON_FEATURE, config.getRuntimeType()),
                ST_JSON_FEATURE);

        if (!config.isRegistered(JacksonSerializerMessageBodyReaderWriter.class)) {
            context.register(SerializationExceptionMapper.class);
            context.register(JacksonSerializerMessageBodyReaderWriter.class);
        }

        return true;
    }

    /**
     * Create a new {@link ContextResolver} for {@link JacksonSerializationProvider} used by this feature.
     * @deprecated Use {@link #newContextResolver(ObjectMapper)}.
     * @param objectMapper the {@link ObjectMapper} to use for creating a {@link JacksonSerializationProvider}.
     * @return a {@link ContextResolver}.
     */
    @Deprecated
    public static ContextResolver<JacksonSerializationProvider> contextResolverFor(final ObjectMapper objectMapper) {
        return contextResolverFor(new JacksonSerializationProvider(objectMapper));
    }

    /**
     * Create a new {@link ContextResolver} for {@link JacksonSerializationProvider} used by this feature.
     * @deprecated Use {@link #newContextResolver(JacksonSerializerFactory)}.
     * @param serializationProvider the {@link JacksonSerializationProvider} to use.
     * @return a {@link ContextResolver}.
     */
    @Deprecated
    public static ContextResolver<JacksonSerializationProvider> contextResolverFor(
            final JacksonSerializationProvider serializationProvider) {
        return new JacksonSerializationProviderContextResolver(requireNonNull(serializationProvider));
    }

    /**
     * Create a new {@link ContextResolver} for {@link ObjectMapper} used by this feature.
     * @param objectMapper the {@link ObjectMapper} to use for creating a {@link JacksonSerializerFactory}.
     * @return a {@link ContextResolver}.
     */
    public static ContextResolver<JacksonSerializerFactory> newContextResolver(ObjectMapper objectMapper) {
        return newContextResolver(new JacksonSerializerFactory(objectMapper));
    }

    /**
     * Create a new {@link ContextResolver} for {@link JacksonSerializerFactory} used by this feature.
     * @param cache the {@link JacksonSerializerFactory} to use.
     * @return a {@link ContextResolver}.
     */
    public static ContextResolver<JacksonSerializerFactory> newContextResolver(JacksonSerializerFactory cache) {
        return new JacksonSerializerFactoryContextResolver(cache);
    }
}
