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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.message.internal.EntityInputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import static io.servicetalk.http.router.jersey.Context.getResponseChunkPublisherRef;
import static io.servicetalk.http.router.jersey.DummyInputStreamChunkPublisher.asCloseableChunkPublisher;
import static org.glassfish.jersey.message.internal.ReaderInterceptorExecutor.closeableInputStream;

/**
 * A combined {@link MessageBodyReader} / {@link MessageBodyWriter} that allows bypassing Java IO streams
 * when request/response entities need to be converted to {@code Publisher<HttpPayloadChunk>}.
 */
final class PublisherMessageBodyReaderWriter
        implements MessageBodyReader<Publisher<HttpPayloadChunk>>, MessageBodyWriter<Publisher<HttpPayloadChunk>> {

    @Context
    private ConnectionContext ctx;

    @Context
    private Provider<ContainerRequestContext> requestContextProvider;

    @Override
    public boolean isReadable(final Class<?> type,
                              final Type genericType,
                              final Annotation[] annotations,
                              final MediaType mediaType) {
        return isHttpChunkPublisher(genericType, false);
    }

    @Override
    public Publisher<HttpPayloadChunk> readFrom(final Class<Publisher<HttpPayloadChunk>> type,
                                                final Type genericType,
                                                final Annotation[] annotations,
                                                final MediaType mediaType,
                                                final MultivaluedMap<String, String> httpHeaders,
                                                final InputStream entityStream) throws WebApplicationException {
        // Unwrap the entity stream created by Jersey to fetch the wrapped one
        final EntityInputStream eis = (EntityInputStream) closeableInputStream(entityStream);
        final InputStream wrappedStream = eis.getWrappedStream();
        if (wrappedStream instanceof ChunkPublisherInputStream) {
            // If the wrapped stream is built around a Publisher, provide it to the resource as-is
            return ((ChunkPublisherInputStream) wrappedStream).getChunkPublisher();
        }

        // The wrappedStream has been replaced with a user stream (via filtering) so we use it to build a new
        // publisher, being careful to return a publisher instance that implements Closeable to prevent Jersey
        // from closing wrappedStream.
        return asCloseableChunkPublisher(wrappedStream, ctx.getBufferAllocator(), ctx.getExecutor());
    }

    @Override
    public boolean isWriteable(final Class<?> type,
                               final Type genericType,
                               final Annotation[] annotations,
                               final MediaType mediaType) {
        return isHttpChunkPublisher(genericType, true);
    }

    @Override
    public void writeTo(final Publisher<HttpPayloadChunk> publisher,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws WebApplicationException {
        // The response entity being a Publisher, we do not need to write it to the entity stream
        // but instead store it in request context to bypass the stream writing infrastructure.
        getResponseChunkPublisherRef(requestContextProvider.get()).set(publisher);
    }

    private static boolean isHttpChunkPublisher(final Type genericType, final boolean unwrapCompletionStage) {
        if (!(genericType instanceof ParameterizedType)) {
            return false;
        }

        final ParameterizedType parameterizedType = (ParameterizedType) genericType;
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 1) {
            return false;
        }

        if (CompletionStage.class.equals(parameterizedType.getRawType()) && unwrapCompletionStage) {
            return isHttpChunkPublisher(typeArguments[0], false);
        }

        return Publisher.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())
                && HttpPayloadChunk.class.isAssignableFrom((Class<?>) typeArguments[0]);
    }
}
