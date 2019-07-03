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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.util.collection.Ref;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import static io.servicetalk.http.router.jersey.AbstractMessageBodyReaderWriter.getRequestContentLength;
import static io.servicetalk.http.router.jersey.AbstractMessageBodyReaderWriter.isSse;
import static io.servicetalk.http.router.jersey.AbstractMessageBodyReaderWriter.newBufferForRequestContent;
import static io.servicetalk.http.router.jersey.internal.BufferPublisherInputStream.handleEntityStream;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setResponseBufferPublisher;
import static javax.ws.rs.Priorities.ENTITY_CODER;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.WILDCARD;
import static org.glassfish.jersey.message.internal.ReaderWriter.BUFFER_SIZE;

/**
 * A combined {@link MessageBodyReader} / {@link MessageBodyWriter} that allows bypassing Java IO streams
 * when request/response entities need to be converted to/from {@link Buffer} instances.
 */
@Priority(ENTITY_CODER)
@Produces(WILDCARD)
final class BufferMessageBodyReaderWriter implements MessageBodyReader<Buffer>, MessageBodyWriter<Buffer> {
    // We can not use `@Context ConnectionContext` directly because we would not see the latest version
    // in case it has been rebound as part of offloading.
    @Context
    protected Provider<Ref<ConnectionContext>> ctxRefProvider;

    @Context
    private Provider<ContainerRequestContext> requestCtxProvider;

    @Override
    public boolean isReadable(final Class<?> type,
                              final Type genericType,
                              final Annotation[] annotations,
                              final MediaType mediaType) {
        return Buffer.class.isAssignableFrom(type);
    }

    @Override
    public Buffer readFrom(final Class<Buffer> type,
                           final Type genericType,
                           final Annotation[] annotations,
                           final MediaType mediaType,
                           final MultivaluedMap<String, String> httpHeaders,
                           final InputStream entityStream) throws WebApplicationException {

        return handleEntityStream(entityStream, ctxRefProvider.get().get().executionContext().bufferAllocator(),
                (p, a) -> {
                    final Buffer buf = newBufferForRequestContent(getRequestContentLength(requestCtxProvider), a);
                    p.toIterable().forEach(buf::writeBytes);
                    return buf;
                },
                (is, a) -> {
                    final int contentLength = getRequestContentLength(requestCtxProvider);
                    final Buffer buf = contentLength == -1 ? a.newBuffer() : a.newBuffer(contentLength);
                    try {
                        // Configured via the org.glassfish.jersey.message.MessageProperties#IO_BUFFER_SIZE property
                        final int written = buf.writeBytesUntilEndStream(is, BUFFER_SIZE);
                        if (contentLength > 0 && written != contentLength) {
                            throw new BadRequestException("Not enough bytes for content-length: " + contentLength
                                    + ", only got: " + written);
                        }
                        return buf;
                    } catch (final IOException e) {
                        throw new InternalServerErrorException(e);
                    }
                });
    }

    @Override
    public boolean isWriteable(final Class<?> type,
                               final Type genericType,
                               final Annotation[] annotations,
                               final MediaType mediaType) {
        return !isSse(requestCtxProvider.get()) && Buffer.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(final Buffer buffer,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) {
        httpHeaders.putSingle(CONTENT_LENGTH, buffer.readableBytes());
        setResponseBufferPublisher(Publisher.from(buffer), requestCtxProvider.get());
    }
}
