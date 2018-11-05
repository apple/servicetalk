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

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link HttpSerializer} that serializes a key-value {@link Map} to an urlencoded form.
 */
public final class FormUrlEncodedHttpSerializer implements HttpSerializer<Map<String, String>> {

    static final FormUrlEncodedHttpSerializer UTF8 = new FormUrlEncodedHttpSerializer(UTF_8,
            headers -> headers.set(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED + "; charset=UTF-8"));

    private final Charset charset;
    private final Consumer<HttpHeaders> addContentType;

    public FormUrlEncodedHttpSerializer(final Charset charset, final Consumer<HttpHeaders> addContentType) {
        this.charset = charset;
        this.addContentType = addContentType;
    }

    @Override
    public Buffer serialize(final HttpHeaders headers,
                            final Map<String, String> value,
                            final BufferAllocator allocator) {
        addContentType.accept(headers);
        return toBuffer(value, allocator);
    }

    @Override
    public BlockingIterable<Buffer> serialize(final HttpHeaders headers,
                                              final BlockingIterable<Map<String, String>> value,
                                              final BufferAllocator allocator) {
        addContentType.accept(headers);
        return () -> {
            final BlockingIterator<Map<String, String>> iterator = value.iterator();
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
    public Publisher<Buffer> serialize(final HttpHeaders headers,
                                       final Publisher<Map<String, String>> value,
                                       final BufferAllocator allocator) {
        addContentType.accept(headers);
        return value.map(values -> toBuffer(values, allocator));
    }

    @Nullable
    private Buffer toBuffer(@Nullable final Map<String, String> value, final BufferAllocator allocator) {
        return value == null ? null : allocator.fromSequence(serialize(value), charset);
    }

    private String serialize(final Map<String, String> value) {
        return value.entrySet().stream()
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private String urlEncode(final String value) {
        try {
            return URLEncoder.encode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
