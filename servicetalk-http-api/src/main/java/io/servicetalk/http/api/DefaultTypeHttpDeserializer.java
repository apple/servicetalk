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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serialization.api.Serializer;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.function.Predicate;

import static io.servicetalk.http.api.HeaderUtils.checkContentType;

/**
 * A {@link HttpDeserializer} that can deserialize a {@link TypeHolder} of type {@link T}.
 * @deprecated Will be removed with {@link HttpDeserializer}.
 * @param <T> Type to deserialize.
 * @see DefaultClassHttpDeserializer
 */
@Deprecated
final class DefaultTypeHttpDeserializer<T> implements HttpDeserializer<T> {

    private final Serializer serializer;
    private final TypeHolder<T> type;
    private final Predicate<HttpHeaders> checkContentType;

    DefaultTypeHttpDeserializer(final Serializer serializer, final TypeHolder<T> type,
                                final Predicate<HttpHeaders> checkContentType) {
        this.serializer = serializer;
        this.type = type;
        this.checkContentType = checkContentType;
    }

    @Override
    public T deserialize(final HttpHeaders headers, final Buffer payload) {
        checkContentType(headers, checkContentType);
        return serializer.deserializeAggregatedSingle(payload, type);
    }

    @Override
    public BlockingIterable<T> deserialize(final HttpHeaders headers,
                                           final BlockingIterable<Buffer> payload) {
        checkContentType(headers, checkContentType);
        return serializer.deserialize(payload, type);
    }

    @Override
    public Publisher<T> deserialize(final HttpHeaders headers, final Publisher<Buffer> payload) {
        checkContentType(headers, checkContentType);
        return serializer.deserialize(payload, type);
    }
}
