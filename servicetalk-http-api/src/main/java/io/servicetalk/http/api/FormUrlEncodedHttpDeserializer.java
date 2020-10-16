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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultHttpRequestMetaData.DEFAULT_MAX_QUERY_PARAMS;
import static io.servicetalk.http.api.HeaderUtils.checkContentType;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.servicetalk.http.api.UriUtils.decodeQueryParams;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;

/**
 * An {@link HttpDeserializer} that deserializes a key-values {@link Map} from an urlencoded form.
 */
final class FormUrlEncodedHttpDeserializer implements HttpDeserializer<Map<String, List<String>>> {
    static final FormUrlEncodedHttpDeserializer UTF8 = new FormUrlEncodedHttpDeserializer(UTF_8,
            headers -> hasContentType(headers, APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));

    private final Charset charset;
    private final Predicate<HttpHeaders> checkContentType;

    FormUrlEncodedHttpDeserializer(final Charset charset, final Predicate<HttpHeaders> checkContentType) {
        this.charset = charset;
        this.checkContentType = checkContentType;
    }

    @Override
    public Map<String, List<String>> deserialize(final HttpHeaders headers, final Buffer payload) {
        checkContentType(headers, checkContentType);
        return deserialize(payload);
    }

    @Override
    public BlockingIterable<Map<String, List<String>>> deserialize(final HttpHeaders headers,
                                                                   final BlockingIterable<Buffer> payload) {
        checkContentType(headers, checkContentType);
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
        checkContentType(headers, checkContentType);
        return payload.map(this::deserialize);
    }

    private Map<String, List<String>> deserialize(@Nullable final Buffer buffer) {
        if (buffer == null || buffer.readableBytes() == 0) {
            return emptyMap();
        }
        return decodeQueryParams(buffer.toString(charset), charset, DEFAULT_MAX_QUERY_PARAMS, (value, charset) -> {
            try {
                return URLDecoder.decode(value, charset.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("URLDecoder failed to find Charset: " + charset, e);
            }
        });
    }
}
