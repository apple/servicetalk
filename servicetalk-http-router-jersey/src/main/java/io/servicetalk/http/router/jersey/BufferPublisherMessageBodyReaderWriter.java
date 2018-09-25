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
import io.servicetalk.http.router.jersey.internal.SourceWrappers.PublisherSource;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

/**
 * A combined {@link MessageBodyReader} / {@link MessageBodyWriter} that allows bypassing Java IO streams
 * when request/response entities need to be converted to/from {@code Publisher<Buffer>}.
 */
final class BufferPublisherMessageBodyReaderWriter
        extends AbstractMessageBodyReaderWriter<Publisher, Buffer, Publisher<Buffer>, PublisherSource<Buffer>> {

    BufferPublisherMessageBodyReaderWriter() {
        super(Publisher.class, Buffer.class);
    }

    @Override
    public Publisher<Buffer> readFrom(final Class<Publisher<Buffer>> type,
                                      final Type genericType,
                                      final Annotation[] annotations,
                                      final MediaType mediaType,
                                      final MultivaluedMap<String, String> httpHeaders,
                                      final InputStream entityStream) throws WebApplicationException {
        return readFrom(entityStream, (p, a) -> p, PublisherSource::new);
    }

    @Override
    public void writeTo(final Publisher<Buffer> publisher,
                        final Class<?> type,
                        final Type genericType,
                        final Annotation[] annotations,
                        final MediaType mediaType,
                        final MultivaluedMap<String, Object> httpHeaders,
                        final OutputStream entityStream) throws WebApplicationException {
        writeTo(publisher);
    }
}
