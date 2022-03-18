/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.data.protobuf.jersey;

import io.servicetalk.data.protobuf.ProtobufSerializerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.ContextResolver;

import static io.servicetalk.data.protobuf.ProtobufSerializerFactory.PROTOBUF;
import static org.glassfish.jersey.CommonProperties.getValue;
import static org.glassfish.jersey.internal.util.PropertiesHelper.getPropertyNameForRuntime;

/**
 * Feature enabling ServiceTalk protobuf serializer request/response content handling.
 */
public final class ServiceTalkProtobufSerializerFeature implements Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkProtobufSerializerFeature.class);
    // Only one Protobuf Feature can be registered per Jersey ApplicationHandler, so using simple name will not be a
    // cause of conflict even if several different shaded versions of ServiceTalk are in use, as each of them will
    // be registered to a different ApplicationHandler.
    static final String ST_PROTOBUF_FEATURE = ServiceTalkProtobufSerializerFeature.class.getSimpleName();
    static final String PROTOBUF_FEATURE = "jersey.config.protobufFeature";

    @Override
    public boolean configure(final FeatureContext context) {
        final Configuration config = context.getConfiguration();

        final String protobufFeature = getValue(config.getProperties(), config.getRuntimeType(),
                ST_PROTOBUF_FEATURE, PROTOBUF_FEATURE, String.class);

        // Do not register our Protobuf feature if another one is already registered
        if (!PROTOBUF_FEATURE.equalsIgnoreCase(protobufFeature)) {
            LOGGER.warn("Skipping registration of: {} as Protobuf support is already provided by: {}",
                    ST_PROTOBUF_FEATURE, protobufFeature);
            return false;
        }

        // Prevent other not yet registered JSON features to register themselves
        context.property(getPropertyNameForRuntime(PROTOBUF_FEATURE, config.getRuntimeType()),
                ST_PROTOBUF_FEATURE);

        if (!config.isRegistered(ProtobufSerializerMessageBodyReaderWriter.class)) {
            context.register(SerializationExceptionMapper.class);
            context.register(ProtobufSerializerMessageBodyReaderWriter.class);
        }

        return true;
    }

    /**
     * Create a new {@link ContextResolver} for {@link ProtobufSerializerFactory} used by this feature.
     * @return a {@link ContextResolver}.
     */
    public static ContextResolver<ProtobufSerializerFactory> newContextResolver() {
        return new ProtobufSerializerFactoryContextResolver(PROTOBUF);
    }
}
