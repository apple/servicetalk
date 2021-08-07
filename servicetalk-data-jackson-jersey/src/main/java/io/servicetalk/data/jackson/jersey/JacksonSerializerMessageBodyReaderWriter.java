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
import io.servicetalk.data.jackson.JacksonSerializerFactory;
import io.servicetalk.http.router.jersey.internal.SourceWrappers.PublisherSource;
import io.servicetalk.http.router.jersey.internal.SourceWrappers.SingleSource;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.ResourceMethod;

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

import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitResult;
import static io.servicetalk.http.router.jersey.internal.BufferPublisherInputStream.handleEntityStream;
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
        final JacksonSerializerFactory serializerFactory = getJacksonSerializerFactory(mediaType);
        final ExecutionContext executionContext = ctxRefProvider.get().get().executionContext();
        final BufferAllocator allocator = executionContext.bufferAllocator();
        final int contentLength = requestCtxProvider.get().getLength();

        if (Single.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> deserialize(p, serializerFactory.serializerDeserializer(getSourceClass(genericType)),
                            contentLength, a),
                    (is, a) -> new SingleSource<>(deserialize(toBufferPublisher(is, a),
                            serializerFactory.serializerDeserializer(getSourceClass(genericType)), contentLength, a)));
        } else if (Publisher.class.isAssignableFrom(type)) {
            return handleEntityStream(entityStream, allocator,
                    (p, a) -> serializerFactory.streamingSerializerDeserializer(
                            getSourceClass(genericType)).deserialize(p, a),
                    (is, a) -> new PublisherSource<>(serializerFactory.streamingSerializerDeserializer(
                            getSourceClass(genericType)).deserialize(toBufferPublisher(is, a), a)));
        }

        return handleEntityStream(entityStream, allocator,
                (p, a) -> deserializeObject(p, serializerFactory.serializerDeserializer(type), contentLength, a),
                (is, a) -> deserializeObject(toBufferPublisher(is, a), serializerFactory.serializerDeserializer(type),
                        contentLength, a));
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
                               final MediaType mediaType) {
        return !isSse(requestCtxProvider.get()) && isSupportedMediaType(mediaType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void writeTo(final Object o, final Class<?> type, final Type genericType, final Annotation[] annotations,
                        final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws WebApplicationException {
        final BufferAllocator allocator = ctxRefProvider.get().get().executionContext().bufferAllocator();
        final Publisher<Buffer> bufferPublisher;
        if (o instanceof Single) {
            final Class<?> clazz = genericType instanceof Class ? (Class) genericType : getSourceClass(genericType);
            Serializer serializer = getJacksonSerializerFactory(mediaType).serializerDeserializer(clazz);
            bufferPublisher = ((Single) o).map(t -> serializer.serialize(t, allocator)).toPublisher();
        } else if (o instanceof Publisher) {
            final Class<?> clazz = genericType instanceof Class ? (Class) genericType : getSourceClass(genericType);
            StreamingSerializer serializer = getJacksonSerializerFactory(mediaType)
                    .streamingSerializerDeserializer(clazz);
            bufferPublisher = serializer.serialize((Publisher) o, allocator);
        } else {
            Serializer serializer = getJacksonSerializerFactory(mediaType).serializerDeserializer(o.getClass());
            bufferPublisher = Publisher.from(serializer.serialize(o, allocator));
        }

        setResponseBufferPublisher(bufferPublisher, requestCtxProvider.get());
    }

    private JacksonSerializerFactory getJacksonSerializerFactory(final MediaType mediaType) {
        final ContextResolver<JacksonSerializerFactory> contextResolver =
                providers.getContextResolver(JacksonSerializerFactory.class, mediaType);

        return contextResolver != null ? contextResolver.getContext(JacksonSerializerFactory.class) :
                JacksonSerializerFactory.JACKSON;
    }

    private static Publisher<Buffer> toBufferPublisher(final InputStream is, final BufferAllocator a) {
        return fromInputStream(is).map(a::wrap);
    }

    private static <T> Single<T> deserialize(
            final Publisher<Buffer> bufferPublisher, final Deserializer<T> deserializer, final int contentLength,
            final BufferAllocator allocator) {
        return bufferPublisher
                .collect(() -> newBufferForRequestContent(contentLength, allocator), Buffer::writeBytes)
                .map(buf -> {
                    try {
                        return deserializer.deserialize(buf, allocator);
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
    static <T> T deserializeObject(final Publisher<Buffer> bufferPublisher, final Deserializer<T> deserializer,
                                   final int contentLength, final BufferAllocator allocator) {
        return awaitResult(deserialize(bufferPublisher, deserializer, contentLength, allocator).toFuture());
    }

    private static boolean isSse(ContainerRequestContext requestCtx) {
        final ResourceMethod method = ((ExtendedUriInfo) requestCtx.getUriInfo()).getMatchedResourceMethod();
        return method != null && method.isSse();
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
