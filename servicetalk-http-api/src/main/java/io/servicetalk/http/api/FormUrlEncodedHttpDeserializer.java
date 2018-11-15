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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

/**
 * An {@link HttpDeserializer} that deserializes a key-values {@link Map} from an urlencoded form.
 */
final class FormUrlEncodedHttpDeserializer implements HttpDeserializer<Map<String, List<String>>> {
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;


    static final FormUrlEncodedHttpDeserializer UTF_8 = new FormUrlEncodedHttpDeserializer(
            headers -> headers.contains(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED + "; charset=UTF-8"));

    private final Predicate<HttpHeaders> checkContentType;

    FormUrlEncodedHttpDeserializer(final Predicate<HttpHeaders> checkContentType) {
        this.checkContentType = checkContentType;
    }

    @Override
    public Map<String, List<String>> deserialize(final HttpHeaders headers, final Buffer payload) {
        checkContentType(headers);
        return deserialize(payload);
    }

    @Override
    public BlockingIterable<Map<String, List<String>>> deserialize(final HttpHeaders headers,
                                                                   final BlockingIterable<Buffer> payload) {
        checkContentType(headers);
        return () -> {
            final BlockingIterator<Buffer> iterator = payload.iterator();
            return new BlockingIterator<Map<String, List<String>>>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public Map<String, List<String>> next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return deserialize(iterator.next(timeout, unit));
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
                public Map<String, List<String>> next() {
                    return deserialize(iterator.next());
                }
            };
        };
    }

    @Override
    public Publisher<Map<String, List<String>>> deserialize(final HttpHeaders headers,
                                                            final Publisher<Buffer> payload) {
        checkContentType(headers);
        return payload.map(this::deserialize);
    }

    private void checkContentType(final HttpHeaders headers) {
        if (!checkContentType.test(headers)) {
            throw new SerializationException("Unexpected headers, can not deserialize. Headers: "
                    + headers.toString());
        }
    }

    private Map<String, List<String>> deserialize(@Nullable final Buffer buffer) {
        if (buffer == null || buffer.capacity() == 0) {
            return Collections.emptyMap();
        }
        return QueryStringDecoder.decodeParams(buffer.toString(DEFAULT_CHARSET));
    }
}
