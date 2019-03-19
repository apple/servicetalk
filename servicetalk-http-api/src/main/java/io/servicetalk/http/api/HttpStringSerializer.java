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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link HttpSerializer} that serializes to {@link String}.
 */
final class HttpStringSerializer implements HttpSerializer<String> {

    static final HttpStringSerializer UTF8_STRING_SERIALIZER = new HttpStringSerializer(UTF_8,
            headers -> headers.set(CONTENT_TYPE, TEXT_PLAIN_UTF_8));

    private final Charset charset;
    private final Consumer<HttpHeaders> addContentType;

    HttpStringSerializer(final Charset charset, Consumer<HttpHeaders> addContentType) {
        this.charset = charset;
        this.addContentType = addContentType;
    }

    @Override
    public Buffer serialize(final HttpHeaders headers, final String value, final BufferAllocator allocator) {
        addContentType.accept(headers);
        return allocator.fromSequence(value, charset);
    }

    @Override
    public BlockingIterable<Buffer> serialize(final HttpHeaders headers, final BlockingIterable<String> value,
                                              final BufferAllocator allocator) {
        addContentType.accept(headers);
        return () -> {
            final BlockingIterator<String> iterator = value.iterator();
            return new BlockingIterator<Buffer>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return toBuffer(iterator.next(timeout, unit), allocator);
                }

                @Override
                public void close() throws Exception {
                    iterator.close();
                }

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Buffer next() {
                    return toBuffer(iterator.next(), allocator);
                }
            };
        };
    }

    @Override
    public Publisher<Buffer> serialize(final HttpHeaders headers, final Publisher<String> value,
                                       final BufferAllocator allocator) {
        addContentType.accept(headers);
        return value.map(str -> toBuffer(str, allocator));
    }

    @Override
    public HttpPayloadWriter<String> serialize(final HttpHeaders headers, final HttpPayloadWriter<Buffer> payloadWriter,
                                               final BufferAllocator allocator) {
        addContentType.accept(headers);
        return new DelegatingToBufferHttpPayloadWriter<String>(payloadWriter, allocator) {
            @Override
            public void write(final String object) throws IOException {
                delegate.write(toBuffer(object, allocator));
            }
        };
    }

    @Nullable
    private Buffer toBuffer(@Nullable String value, BufferAllocator allocator) {
        return value == null ? null : allocator.fromSequence(value, charset);
    }
}
