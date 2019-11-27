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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serialization.api.SerializationException;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link HttpDeserializer} that deserializes from {@link String}.
 */
final class HttpStringDeserializer implements HttpDeserializer<String> {
    static final HttpStringDeserializer UTF_8_STRING_DESERIALIZER =
            new HttpStringDeserializer(UTF_8, headers -> hasContentType(headers, TEXT_PLAIN, UTF_8));

    private final Charset charset;
    private final Predicate<HttpHeaders> checkContentType;

    HttpStringDeserializer(final Charset charset, final Predicate<HttpHeaders> checkContentType) {
        this.charset = charset;
        this.checkContentType = checkContentType;
    }

    @Override
    public String deserialize(final HttpHeaders headers, final Buffer payload) {
        checkContentType(headers);
        return payload.toString(charset);
    }

    @Override
    public BlockingIterable<String> deserialize(final HttpHeaders headers, final BlockingIterable<Buffer> payload) {
        checkContentType(headers);
        return () -> {
            final BlockingIterator<Buffer> iterator = payload.iterator();
            return new BlockingIterator<String>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public String next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return HttpStringDeserializer.this.toString(iterator.next(timeout, unit));
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
                public String next() {
                    return HttpStringDeserializer.this.toString(iterator.next());
                }
            };
        };
    }

    @Override
    public Publisher<String> deserialize(final HttpHeaders headers, final Publisher<Buffer> payload) {
        checkContentType(headers);
        return payload.map(this::toString);
    }

    @Nullable
    private String toString(@Nullable Buffer buffer) {
        return buffer == null ? null : buffer.toString(charset);
    }

    private void checkContentType(final HttpHeaders headers) {
        if (!checkContentType.test(headers)) {
            throw new SerializationException("Unexpected headers, can not deserialize. Headers: "
                    + headers.toString(DEFAULT_DEBUG_HEADER_FILTER));
        }
    }
}
