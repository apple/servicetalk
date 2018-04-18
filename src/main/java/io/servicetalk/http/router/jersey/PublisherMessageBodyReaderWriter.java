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

import org.glassfish.jersey.message.internal.EntityInputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import static org.glassfish.jersey.message.internal.ReaderInterceptorExecutor.closeableInputStream;

/**
 * A combined {@link MessageBodyReader} / {@link MessageBodyWriter} that allows bypassing Java IO streams
 * when request/response entities need to be converted to {@code Publisher<HttpPayloadChunk>}.
 */
final class PublisherMessageBodyReaderWriter implements MessageBodyReader<Publisher<HttpPayloadChunk>>, MessageBodyWriter<Publisher<HttpPayloadChunk>> {
    @Override
    public boolean isReadable(final Class type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        return isHttpChunkPublisher(genericType, false);
    }

    @Override
    public Publisher<HttpPayloadChunk> readFrom(final Class type, final Type genericType, final Annotation[] annotations,
                                                final MediaType mediaType, final MultivaluedMap httpHeaders,
                                                final InputStream entityStream) throws WebApplicationException {
        // Unwrap the entity stream created by Jersey to fetch the original request chunk publisher
        final EntityInputStream eis = (EntityInputStream) closeableInputStream(entityStream);
        return ((DummyBufferPublisherInputStream) eis.getWrappedStream()).getChunkPublisher();
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        return isHttpChunkPublisher(genericType, true);
    }

    @Override
    public void writeTo(final Publisher<HttpPayloadChunk> publisher, final Class<?> type, final Type genericType, final Annotation[] annotations,
                        final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws WebApplicationException {
        // Nothing to do: the response entity is a chunk publisher and thus can be used as-is by ServiceTalk
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

        return Publisher.class.equals(parameterizedType.getRawType()) && HttpPayloadChunk.class.equals(typeArguments[0]);
    }
}
