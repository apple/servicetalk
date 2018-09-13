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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.router.jersey.internal.InputStreamIterator;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.Serializer;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.glassfish.jersey.internal.util.collection.Ref;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;

import static io.servicetalk.http.router.jersey.BufferPublisherInputStream.handleEntityStream;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setResponseBufferPublisher;
import static javax.ws.rs.Priorities.ENTITY_CODER;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.WILDCARD;

// Less priority than the *MessageBodyReaderWriters provided by the Jersey Router itself to avoid attempting
// JSON (de)serialization of core types like Buffer.
@Priority(ENTITY_CODER + 100)
@Consumes(WILDCARD)
@Produces(WILDCARD)
final class JacksonSerializerMessageBodyReaderWriter implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
    private static final JacksonSerializationProvider DEFAULT_JACKSON_SERIALIZATION_PROVIDER =
            new JacksonSerializationProvider();

    private static final Object NOTHING_DESERIALIZED = new Object();

    // We can not use `@Context ConnectionContext` directly because we would not see the latest version
    // in case it has been rebound as part of offloading.
    @Context
    private Provider<Ref<ConnectionContext>> ctxRefProvider;

    @Context
    private Provider<ContainerRequestContext> requestCtxProvider;

    @Context
    private Providers providers;

    @Context
    private HttpHeaders headers;

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
                              final MediaType mediaType) {
        return isSupportedMediaType(mediaType);
    }

    @Override
    public Object readFrom(final Class<Object> type, final Type genericType, final Annotation[] annotations,
                           final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders,
                           final InputStream entityStream) throws WebApplicationException {

        final Serializer ser = getSerializer(mediaType);
        final ExecutionContext executionContext = ctxRefProvider.get().get().getExecutionContext();
        final BufferAllocator allocator = executionContext.getBufferAllocator();

        if (Single.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> ser.deserialize(p, getSourceClass(genericType)).first(),
                    (is, a) -> ser.deserialize(toBufferPublisher(is, a), getSourceClass(genericType)).first());
        } else if (Publisher.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> ser.deserialize(p, getSourceClass(genericType)),
                    (is, a) -> ser.deserialize(toBufferPublisher(is, a), getSourceClass(genericType)));
        }

        return handleEntityStream(entityStream, allocator,
                (p, a) -> deserialize(p, ser, type),
                (is, a) -> deserialize(toBufferPublisher(is, a), ser, type));
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
                               final MediaType mediaType) {

        return isSupportedMediaType(mediaType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeTo(final Object o, final Class<?> type, final Type genericType, final Annotation[] annotations,
                        final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws WebApplicationException {

        final Publisher<Buffer> bufferPublisher;
        if (o instanceof Single) {
            bufferPublisher = getResponseBufferPublisher(((Single) o).toPublisher(), genericType, mediaType);
        } else if (o instanceof Publisher) {
            bufferPublisher = getResponseBufferPublisher((Publisher) o, genericType, mediaType);
        } else {
            bufferPublisher = getResponseBufferPublisher(Publisher.from(o), o.getClass(), mediaType);
        }

        setResponseBufferPublisher(bufferPublisher, requestCtxProvider.get());
    }

    @SuppressWarnings("unchecked")
    private Publisher<Buffer> getResponseBufferPublisher(final Publisher publisher, final Type type,
                                                         final MediaType mediaType) {
        final BufferAllocator allocator = ctxRefProvider.get().get().getExecutionContext().getBufferAllocator();
        return getSerializer(mediaType).serialize(publisher, allocator,
                type instanceof Class ? (Class) type : getSourceClass(type));
    }

    private Serializer getSerializer(final MediaType mediaType) {
        return new DefaultSerializer(getJacksonSerializationProvider(mediaType));
    }

    private JacksonSerializationProvider getJacksonSerializationProvider(final MediaType mediaType) {
        final ContextResolver<JacksonSerializationProvider> contextResolver =
                providers.getContextResolver(JacksonSerializationProvider.class, mediaType);

        return contextResolver != null ? contextResolver.getContext(JacksonSerializationProvider.class) :
                DEFAULT_JACKSON_SERIALIZATION_PROVIDER;
    }

    private static Publisher<Buffer> toBufferPublisher(final InputStream is, final BufferAllocator a) {
        return Publisher.from(() -> new InputStreamIterator(is)).map(a::wrap);
    }

    @Nullable
    private static Object deserialize(final Publisher<Buffer> bufferPublisher, final Serializer ser,
                                      final Class<Object> type) {

        final Object result;

        try (final BlockingIterator<Object> i = ser.deserialize(bufferPublisher.toIterable(), type).iterator()) {
            result = !i.hasNext() ? NOTHING_DESERIALIZED : i.next();
        } catch (final SerializationException se) {
            throw se; // handled by the exception mapper
        } catch (final Exception e) {
            throw new InternalServerErrorException(e);
        }

        if (result == NOTHING_DESERIALIZED) {
            throw new BadRequestException("No deserializable JSON content");
        }

        return result;
    }

    private static boolean isSupportedMediaType(final MediaType mediaType) {
        // At the moment, we only support the official JSON mime-type and its related micro-formats
        return mediaType.getType().equalsIgnoreCase(APPLICATION_JSON_TYPE.getType()) &&
                (mediaType.getSubtype().equalsIgnoreCase(APPLICATION_JSON_TYPE.getSubtype()) ||
                        mediaType.getSubtype().toLowerCase().endsWith('+' + APPLICATION_JSON_TYPE.getSubtype()));
    }

    private static Class<?> getSourceClass(final Type sourceType) {
        final Type sourceContentType = ((ParameterizedType) sourceType).getActualTypeArguments()[0];
        if (sourceContentType instanceof Class) {
            return (Class<?>) sourceContentType;
        } else if (sourceContentType instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) sourceContentType).getRawType();
        }

        throw new IllegalArgumentException("Unsupported source type: " + sourceType);
    }
}
