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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.router.jersey.internal.SourceWrappers.PublisherSource;
import io.servicetalk.http.router.jersey.internal.SourceWrappers.SingleSource;
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
import java.util.NoSuchElementException;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
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

import static io.servicetalk.concurrent.internal.FutureUtils.awaitResult;
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

        final Serializer serializer = getSerializer(mediaType);
        final ExecutionContext executionContext = ctxRefProvider.get().get().executionContext();
        final BufferAllocator allocator = executionContext.bufferAllocator();
        final int contentLength = requestCtxProvider.get().getLength();

        if (Single.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> deserialize(p, serializer, getSourceClass(genericType), contentLength, a),
                    (is, a) -> new SingleSource<>(deserialize(toBufferPublisher(is, a), serializer,
                            getSourceClass(genericType), contentLength, a)));
        } else if (Publisher.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> serializer.deserialize(p, getSourceClass(genericType)),
                    (is, a) -> new PublisherSource<>(serializer.deserialize(toBufferPublisher(is, a),
                            getSourceClass(genericType))));
        }

        return handleEntityStream(entityStream, allocator,
                (p, a) -> deserializeObject(p, serializer, type, contentLength, a),
                (is, a) -> deserializeObject(toBufferPublisher(is, a), serializer, type, contentLength, a));
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
        final BufferAllocator allocator = ctxRefProvider.get().get().executionContext().bufferAllocator();
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
        return Publisher.from(is).map(a::wrap);
    }

    private static <T> Single<T> deserialize(final Publisher<Buffer> bufferPublisher, final Serializer ser,
                                             final Class<T> type, final int contentLength,
                                             final BufferAllocator allocator) {

        return bufferPublisher
                .reduce(() -> newBufferForRequestContent(contentLength, allocator), Buffer::writeBytes)
                .map(buf -> {
                    try {
                        return ser.deserializeAggregatedSingle(buf, type);
                    } catch (final NoSuchElementException e) {
                        throw new BadRequestException("No deserializable JSON content", e);
                    } catch (final SerializationException e) {
                        // SerializationExceptionMapper can't always tell for sure that the exception was thrown because
                        // of bad user data: here we are deserializing user data so we can assume we fail because of it
                        // and immediately throw the properly mapped JAX-RS exception
                        throw new BadRequestException("Invalid JSON data", e);
                    }
                });
    }

    static Buffer newBufferForRequestContent(final int contentLength,
                                             final BufferAllocator allocator) {
        return contentLength == -1 ? allocator.newBuffer() : allocator.newBuffer(contentLength);
    }

    // visible for testing
    static <T> T deserializeObject(final Publisher<Buffer> bufferPublisher, final Serializer ser,
                                   final Class<T> type, final int contentLength,
                                   final BufferAllocator allocator) {
        return awaitResult(deserialize(bufferPublisher, ser, type, contentLength, allocator).toFuture());
    }

    private static boolean isSupportedMediaType(final MediaType mediaType) {
        // At the moment, we only support the official JSON mime-type and its related micro-formats
        return mediaType.getType().equalsIgnoreCase(APPLICATION_JSON_TYPE.getType()) &&
                (mediaType.getSubtype().equalsIgnoreCase(APPLICATION_JSON_TYPE.getSubtype()) ||
                        mediaType.getSubtype().toLowerCase().endsWith('+' + APPLICATION_JSON_TYPE.getSubtype()));
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getSourceClass(final Type sourceType) {
        final Type sourceContentType = ((ParameterizedType) sourceType).getActualTypeArguments()[0];
        if (sourceContentType instanceof Class) {
            return (Class<T>) sourceContentType;
        } else if (sourceContentType instanceof ParameterizedType) {
            return (Class<T>) ((ParameterizedType) sourceContentType).getRawType();
        }

        throw new IllegalArgumentException("Unsupported source type: " + sourceType);
    }
}
