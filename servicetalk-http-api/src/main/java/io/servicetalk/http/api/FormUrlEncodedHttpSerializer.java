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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serialization.api.SerializationException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link HttpSerializer} that serializes a key-values {@link Map} to an urlencoded form.
 */
final class FormUrlEncodedHttpSerializer implements HttpSerializer<Map<String, List<String>>> {
    static final FormUrlEncodedHttpSerializer UTF8 = new FormUrlEncodedHttpSerializer(UTF_8,
            headers -> headers.set(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED_UTF_8));

    private final Charset charset;
    private final Consumer<HttpHeaders> addContentType;

    FormUrlEncodedHttpSerializer(final Charset charset, final Consumer<HttpHeaders> addContentType) {
        this.charset = charset;
        this.addContentType = addContentType;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Buffer serialize(final HttpHeaders headers,
                            final Map<String, List<String>> parameters,
                            final BufferAllocator allocator) {
        addContentType.accept(headers);
        return serialize(parameters, allocator, false);
    }

    @Override
    public BlockingIterable<Buffer> serialize(final HttpHeaders headers,
                                              final BlockingIterable<Map<String, List<String>>> parameters,
                                              final BufferAllocator allocator) {
        addContentType.accept(headers);
        return () -> {
            final BlockingIterator<Map<String, List<String>>> iterator = parameters.iterator();
            return new BlockingIterator<Buffer>() {
                private boolean isContinuation;

                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    Map<String, List<String>> next = iterator.next(timeout, unit);
                    Buffer buffer = serialize(next, allocator, isContinuation);
                    isContinuation = isContinuation || buffer.readableBytes() > 0;
                    return buffer;
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
                    Map<String, List<String>> next = iterator.next();
                    Buffer buffer = serialize(next, allocator, isContinuation);
                    isContinuation = isContinuation || buffer.readableBytes() > 0;
                    return buffer;
                }
            };
        };
    }

    @Override
    public Publisher<Buffer> serialize(final HttpHeaders headers,
                                       final Publisher<Map<String, List<String>>> parameters,
                                       final BufferAllocator allocator) {

        addContentType.accept(headers);
        return parameters.liftSync(subscriber -> new PublisherSource.Subscriber<Map<String, List<String>>>() {

            private boolean isContinuation;

            @Override
            public void onSubscribe(PublisherSource.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(@Nullable Map<String, List<String>> values) {
                Buffer buffer = serialize(values, allocator, isContinuation);
                isContinuation = isContinuation || buffer.readableBytes() > 0;
                subscriber.onNext(buffer);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    @Override
    public HttpPayloadWriter<Map<String, List<String>>> serialize(final HttpHeaders headers,
                                                                  final HttpPayloadWriter<Buffer> payloadWriter,
                                                                  final BufferAllocator allocator) {
        addContentType.accept(headers);
        return new DelegatingToBufferHttpPayloadWriter<Map<String, List<String>>>(payloadWriter, allocator) {

            private boolean isContinuation;

            @SuppressWarnings("ConstantConditions")
            @Override
            public void write(final Map<String, List<String>> values) throws IOException {
                Buffer buffer = serialize(values, allocator, isContinuation);
                isContinuation = isContinuation || buffer.readableBytes() > 0;
                delegate.write(buffer);
            }
        };
    }

    private Buffer serialize(@Nullable final Map<String, List<String>> parameters, final BufferAllocator allocator,
                             final boolean isContinuation) {

        if (parameters == null) {
            return EMPTY_BUFFER;
        }

        final Buffer buffer = allocator.newBuffer();
        // Null values may be omitted
        // https://tools.ietf.org/html/rfc1866#section-8.2
        parameters.forEach((key, values) -> {
            if (key == null || key.isEmpty()) {
                throw new SerializationException("Null or empty keys are not supported " +
                        "for x-www-form-urlencoded params");
            }

            if (values == null) {
                return;
            }

            values.forEach(value -> {
                if (value == null) {
                    return;
                }

                if (buffer.writerIndex() != 0 || isContinuation) {
                    buffer.writeBytes("&".getBytes(charset));
                }
                buffer.writeBytes(urlEncode(key).getBytes(charset));
                buffer.writeBytes("=".getBytes(charset));
                buffer.writeBytes(urlEncode(value).getBytes(charset));
            });
        });
        return buffer;
    }

    private String urlEncode(final String value) {
        try {
            return URLEncoder.encode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
