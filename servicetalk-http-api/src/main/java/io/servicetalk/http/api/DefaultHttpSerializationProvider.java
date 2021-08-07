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

import io.servicetalk.serialization.api.Serializer;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * @deprecated Will be removed with {@link HttpSerializationProvider}.
 */
@Deprecated
final class DefaultHttpSerializationProvider implements HttpSerializationProvider {

    private final Serializer serializer;
    private final Consumer<HttpHeaders> addContentType;
    private final Predicate<HttpHeaders> checkContentType;

    DefaultHttpSerializationProvider(final Serializer serializer, final Consumer<HttpHeaders> addContentType,
                                     final Predicate<HttpHeaders> checkContentType) {
        this.serializer = serializer;
        this.addContentType = addContentType;
        this.checkContentType = checkContentType;
    }

    @Override
    public <T> HttpSerializer<T> serializerFor(final Class<T> type) {
        return new DefaultClassHttpSerializer<>(type, serializer, addContentType);
    }

    @Override
    public <T> HttpSerializer<T> serializerFor(final Class<T> type, final IntUnaryOperator bytesEstimator) {
        return new DefaultSizeAwareClassHttpSerializer<>(type, serializer, addContentType, bytesEstimator);
    }

    @Override
    public <T> HttpSerializer<T> serializerFor(final TypeHolder<T> type) {
        return new DefaultTypeHttpSerializer<>(type, serializer, addContentType);
    }

    @Override
    public <T> HttpSerializer<T> serializerFor(final TypeHolder<T> type, final IntUnaryOperator bytesEstimator) {
        return new DefaultSizeAwareTypeHttpSerializer<>(type, serializer, addContentType, bytesEstimator);
    }

    @Override
    public <T> HttpDeserializer<T> deserializerFor(final Class<T> type) {
        return new DefaultClassHttpDeserializer<>(serializer, type, checkContentType);
    }

    @Override
    public <T> HttpDeserializer<T> deserializerFor(final TypeHolder<T> type) {
        return new DefaultTypeHttpDeserializer<>(serializer, type, checkContentType);
    }
}
