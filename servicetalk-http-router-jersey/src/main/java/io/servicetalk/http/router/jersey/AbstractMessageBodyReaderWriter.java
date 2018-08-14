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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.message.internal.EntityInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.router.jersey.Context.setResponseChunkPublisher;
import static javax.ws.rs.core.MediaType.WILDCARD;
import static org.glassfish.jersey.message.internal.ReaderInterceptorExecutor.closeableInputStream;

@Consumes(WILDCARD)
@Produces(WILDCARD)
abstract class AbstractMessageBodyReaderWriter<Source, T, SourceOfT, WrappedSourceOfT extends SourceOfT>
        implements MessageBodyReader<SourceOfT>, MessageBodyWriter<SourceOfT> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Class<Source> sourceClass;
    private final Class<T> contentClass;

    @Context
    protected ConnectionContext ctx;

    @Context
    protected Provider<ContainerRequestContext> requestContextProvider;

    protected AbstractMessageBodyReaderWriter(final Class<Source> sourceClass, final Class<T> contentClass) {
        this.sourceClass = sourceClass;
        this.contentClass = contentClass;
    }

    @Override
    public final boolean isReadable(final Class<?> type,
                                    final Type genericType,
                                    final Annotation[] annotations,
                                    final MediaType mediaType) {
        return isSupported(genericType, false);
    }

    final SourceOfT readFrom(final InputStream entityStream,
                             final BiFunction<Publisher<HttpPayloadChunk>, BufferAllocator, SourceOfT> bodyFunction,
                             final Function<SourceOfT, WrappedSourceOfT> sourceFunction)
            throws WebApplicationException {
        // Unwrap the entity stream created by Jersey to fetch the wrapped one
        final EntityInputStream eis = (EntityInputStream) closeableInputStream(entityStream);
        final InputStream wrappedStream = eis.getWrappedStream();
        final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();

        if (wrappedStream instanceof ChunkPublisherInputStream) {
            // If the wrapped stream is built around a Publisher, provide it to the resource as-is
            return bodyFunction.apply(((ChunkPublisherInputStream) wrappedStream).getChunkPublisher(), allocator);
        }

        return bodyFunction
                .andThen(sourceFunction)
                .apply(Publisher.from(() -> new InputStreamIterator(wrappedStream))
                        .map(bytes -> newPayloadChunk(allocator.wrap(bytes))), allocator);
    }

    @Override
    public final boolean isWriteable(final Class<?> type,
                                     final Type genericType,
                                     final Annotation[] annotations,
                                     final MediaType mediaType) {
        return isSupported(genericType, true);
    }

    final void writeTo(final Publisher<HttpPayloadChunk> publisher) throws WebApplicationException {
        // The response entity being a Publisher, we do not need to write it to the entity stream
        // but instead store it in request context to bypass the stream writing infrastructure.
        setResponseChunkPublisher(publisher, requestContextProvider.get());
    }

    private boolean isSupported(final Type entityType, final boolean unwrapCompletionStage) {
        if (!(entityType instanceof ParameterizedType)) {
            return false;
        }

        final ParameterizedType parameterizedType = (ParameterizedType) entityType;

        if (CompletionStage.class.equals(parameterizedType.getRawType()) && unwrapCompletionStage) {
            final Type type = getSingleTypeArgumentOrNull(parameterizedType);
            return type != null && isSupported(type, false);
        }

        final Type type;
        return sourceClass.isAssignableFrom((Class<?>) parameterizedType.getRawType()) &&
                (type = getSingleTypeArgumentOrNull(parameterizedType)) != null &&
                contentClass.isAssignableFrom((Class<?>) type);
    }

    @Nullable
    private static Type getSingleTypeArgumentOrNull(final ParameterizedType parameterizedType) {
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return typeArguments.length != 1 ? null : typeArguments[0];
    }
}
