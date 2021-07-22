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
package io.servicetalk.serialization.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.CloseableIterator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.AbstractCloseableIterable;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Default implementation for {@link Serializer}.
 * @deprecated Use implementations of following types:
 * <ul>
 *     <li>{@link io.servicetalk.serializer.api.Serializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.StreamingSerializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.Deserializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.StreamingDeserializer}</li>
 * </ul>
 */
@Deprecated
public final class DefaultSerializer implements Serializer {

    /**
     * This applies a somewhat arbitrary limit (around 500kb) on the auto scaling of the next buffer allocation.
     */
    private static final int MAX_READABLE_BYTES_TO_ADJUST = MAX_VALUE >>> 12;
    private static final int DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE = 512;

    private static final IntUnaryOperator DEFAULT_SIZE_ESTIMATOR = lastSize -> {
        // Approximate the next buffer will be ~33% bigger than the current buffer as a best effort to avoid
        // re-allocation and copy.
        return max(DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE, (min(MAX_READABLE_BYTES_TO_ADJUST, lastSize) << 2) / 3);
    };

    private final SerializationProvider serializationProvider;

    /**
     * New instance.
     *
     * @param serializationProvider {@link SerializationProvider} to use.
     */
    public DefaultSerializer(final SerializationProvider serializationProvider) {
        this.serializationProvider = requireNonNull(serializationProvider);
    }

    @Override
    public <T> Publisher<Buffer> serialize(final Publisher<T> source, final BufferAllocator allocator,
                                           final Class<T> type) {
        return serialize(source, allocator, type, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> Iterable<Buffer> serialize(final Iterable<T> source, final BufferAllocator allocator,
                                          final Class<T> type) {
        return serialize(source, allocator, type, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> BlockingIterable<Buffer> serialize(final BlockingIterable<T> source, final BufferAllocator allocator,
                                                  final Class<T> type) {
        return serialize(source, allocator, type, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> Publisher<Buffer> serialize(final Publisher<T> source, final BufferAllocator allocator,
                                           final Class<T> type, final IntUnaryOperator bytesEstimator) {
        return new SubscribablePublisher<Buffer>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Buffer> subscriber) {
                applySerializer0(subscriber, allocator, bytesEstimator, serializationProvider.getSerializer(type),
                        source);
            }
        };
    }

    @Override
    public <T> Iterable<Buffer> serialize(final Iterable<T> source, final BufferAllocator allocator,
                                          final Class<T> type, final IntUnaryOperator bytesEstimator) {
        return applySerializer0(allocator, bytesEstimator, source, serializationProvider.getSerializer(type));
    }

    @Override
    public <T> BlockingIterable<Buffer> serialize(final BlockingIterable<T> source, final BufferAllocator allocator,
                                                  final Class<T> type, final IntUnaryOperator bytesEstimator) {
        return applySerializer0(allocator, bytesEstimator, source, serializationProvider.getSerializer(type));
    }

    @Override
    public <T> Publisher<Buffer> serialize(final Publisher<T> source, final BufferAllocator allocator,
                                           final TypeHolder<T> typeHolder) {
        return serialize(source, allocator, typeHolder, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> Iterable<Buffer> serialize(final Iterable<T> source, final BufferAllocator allocator,
                                          final TypeHolder<T> typeHolder) {
        return serialize(source, allocator, typeHolder, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> BlockingIterable<Buffer> serialize(final BlockingIterable<T> source, final BufferAllocator allocator,
                                                  final TypeHolder<T> typeHolder) {
        return serialize(source, allocator, typeHolder, DEFAULT_SIZE_ESTIMATOR);
    }

    @Override
    public <T> Publisher<Buffer> serialize(final Publisher<T> source, final BufferAllocator allocator,
                                           final TypeHolder<T> typeHolder, final IntUnaryOperator bytesEstimator) {
        return new SubscribablePublisher<Buffer>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Buffer> subscriber) {
                applySerializer0(subscriber, allocator, bytesEstimator, serializationProvider.getSerializer(typeHolder),
                        source);
            }
        };
    }

    @Override
    public <T> Iterable<Buffer> serialize(final Iterable<T> source, final BufferAllocator allocator,
                                          final TypeHolder<T> typeHolder, final IntUnaryOperator bytesEstimator) {
        final StreamingSerializer serializer = serializationProvider.getSerializer(typeHolder);
        return applySerializer0(allocator, bytesEstimator, source, serializer);
    }

    @Override
    public <T> BlockingIterable<Buffer> serialize(final BlockingIterable<T> source, final BufferAllocator allocator,
                                                  final TypeHolder<T> typeHolder,
                                                  final IntUnaryOperator bytesEstimator) {
        return applySerializer0(allocator, bytesEstimator, source, serializationProvider.getSerializer(typeHolder));
    }

    @Override
    public <T> Buffer serialize(final T toSerialize, final BufferAllocator allocator) {
        return serialize(toSerialize, allocator, DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE);
    }

    @Override
    public <T> Buffer serialize(final T toSerialize, final BufferAllocator allocator, final int bytesEstimate) {
        final Buffer destination = allocator.newBuffer(bytesEstimate);
        serializationProvider.serialize(toSerialize, destination);
        return destination;
    }

    @Override
    public <T> void serialize(final T toSerialize, final Buffer destination) {
        serializationProvider.serialize(toSerialize, destination);
    }

    @Override
    public <T> Publisher<T> deserialize(final Publisher<Buffer> source, final TypeHolder<T> typeHolder) {
        return new SubscribablePublisher<T>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super T> subscriber) {
                applyDeserializer0(source, subscriber, serializationProvider.getDeserializer(typeHolder));
            }
        };
    }

    @Override
    public <T> CloseableIterable<T> deserialize(final Iterable<Buffer> source, final TypeHolder<T> typeHolder) {
        return applyDeserializer0(source, serializationProvider.getDeserializer(typeHolder));
    }

    @Override
    public <T> BlockingIterable<T> deserialize(final BlockingIterable<Buffer> source,
                                               final TypeHolder<T> typeHolder) {
        return serializationProvider.getDeserializer(typeHolder).deserialize(source);
    }

    @Override
    public <T> Publisher<T> deserialize(final Publisher<Buffer> source, final Class<T> type) {
        return new SubscribablePublisher<T>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super T> subscriber) {
                applyDeserializer0(source, subscriber, serializationProvider.getDeserializer(type));
            }
        };
    }

    @Override
    public <T> CloseableIterable<T> deserialize(final Iterable<Buffer> source, final Class<T> type) {
        return applyDeserializer0(source, serializationProvider.getDeserializer(type));
    }

    @Override
    public <T> BlockingIterable<T> deserialize(final BlockingIterable<Buffer> source, final Class<T> type) {
        return serializationProvider.getDeserializer(type).deserialize(source);
    }

    @Override
    public <T> CloseableIterable<T> deserializeAggregated(final Buffer serializedData, final Class<T> type) {
        return deserializeAggregated0(serializedData, serializationProvider.getDeserializer(type));
    }

    @Override
    public <T> CloseableIterable<T> deserializeAggregated(final Buffer serializedData, final TypeHolder<T> typeHolder) {
        return deserializeAggregated0(serializedData, serializationProvider.getDeserializer(typeHolder));
    }

    @Override
    public <T> T deserializeAggregatedSingle(final Buffer serializedData, final Class<T> type) {
        return getSingleValueOnly(deserializeAggregated(serializedData, type));
    }

    @Override
    public <T> T deserializeAggregatedSingle(final Buffer serializedData, final TypeHolder<T> typeHolder) {
        return getSingleValueOnly(deserializeAggregated(serializedData, typeHolder));
    }

    private static <T> void applySerializer0(final Subscriber<? super Buffer> subscriber,
                                             final BufferAllocator allocator, final IntUnaryOperator bytesEstimator,
                                             final StreamingSerializer serializer, final Publisher<T> source) {
        toSource(source.map(new SerializerFunction<>(bytesEstimator, allocator, serializer))).subscribe(subscriber);
    }

    private static <T> Iterable<Buffer> applySerializer0(final BufferAllocator allocator,
                                                         final IntUnaryOperator bytesEstimator,
                                                         final Iterable<T> source,
                                                         final StreamingSerializer serializer) {
        return stream(source.spliterator(), false)
                .map(new SerializerFunction<>(bytesEstimator, allocator, serializer))
                .collect(toList());
    }

    @Nonnull
    private static <T> BlockingIterable<Buffer> applySerializer0(final BufferAllocator allocator,
                                                                 final IntUnaryOperator bytesEstimator,
                                                                 final BlockingIterable<T> source,
                                                                 final StreamingSerializer serializer) {
        SerializerFunction<T> serializerFunction = new SerializerFunction<>(bytesEstimator, allocator, serializer);
        return () -> {
            final BlockingIterator<T> iterator = source.iterator();
            return new BlockingIterator<Buffer>() {
                @Override
                public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return iterator.hasNext(timeout, unit);
                }

                @Override
                public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                    return serializerFunction.apply(iterator.next(timeout, unit));
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
                    return serializerFunction.apply(iterator.next());
                }
            };
        };
    }

    private static <T> void applyDeserializer0(final Publisher<Buffer> source, final Subscriber<? super T> subscriber,
                                               final StreamingDeserializer<T> deSerializer) {
        // The StreamingDeserializer will be used to buffer data in between Buffers. It is not thread safe but
        // the concatMap should ensure there is no concurrency, and will ensure visibility when transitioning
        // between Buffers.
        toSource(source.flatMapConcatIterable(deSerializer::deserialize)
                .beforeOnComplete(deSerializer::close))
                .subscribe(subscriber);
    }

    @Nonnull
    private static <T> CloseableIterable<T> applyDeserializer0(final Iterable<Buffer> source,
                                                      final StreamingDeserializer<T> deSerializer) {
        return deserializeAndClose(source, deSerializer::deserialize, deSerializer);
    }

    @Nonnull
    private static <T> CloseableIterable<T> deserializeAggregated0(final Buffer serializedData,
                                                   final StreamingDeserializer<T> deSerializer) {
        return deserializeAndClose(serializedData, deSerializer::deserialize, deSerializer);
    }

    private static <S, T> CloseableIterable<T> deserializeAndClose(final S source,
                                                                   final Function<S, Iterable<T>> doDeserialize,
                                                                   final StreamingDeserializer<T> deSerializer) {
        final Iterable<T> deserialized;
        try {
            deserialized = doDeserialize.apply(source);
        } catch (Throwable throwable) {
            try {
                deSerializer.close();
            } catch (SerializationException e) {
                throwable.addSuppressed(e);
            }
            throw throwable;
        }

        return new AbstractCloseableIterable<T>(deserialized) {
            @Override
            protected void closeIterator(final Iterator<T> iterator) {
                deSerializer.close();
            }
        };
    }

    private static <T> T getSingleValueOnly(CloseableIterable<T> iterable) {
        final CloseableIterator<T> iterator = iterable.iterator();
        final T value = iterator.next();
        closeIterator(iterator,
                iterator.hasNext() ? new SerializationException("More than one value was deserialized.") : null);
        return value;
    }

    private static void closeIterator(CloseableIterator<?> iterator, @Nullable SerializationException cause) {
        try {
            iterator.close(); // May throw in case of incomplete accumulated data
        } catch (Exception e) {
            if (cause != null) {
                cause.addSuppressed(e);
                throw cause;
            }
            if (e instanceof SerializationException) {
                throw (SerializationException) e;
            }
            throw new SerializationException("Failed to close iterator", e);
        }

        if (cause != null) {
            throw cause;
        }
    }

    private static class SerializerFunction<T> implements Function<T, Buffer> {
        private final IntUnaryOperator bytesEstimator;
        private final BufferAllocator allocator;
        private final StreamingSerializer serializer;
        private int lastSize;

        SerializerFunction(final IntUnaryOperator bytesEstimator, final BufferAllocator allocator,
                           final StreamingSerializer serializer) {
            this.bytesEstimator = bytesEstimator;
            this.allocator = allocator;
            this.serializer = serializer;
        }

        @Override
        public Buffer apply(final T t) {
            lastSize = bytesEstimator.applyAsInt(lastSize);
            final Buffer destination = allocator.newBuffer(lastSize);
            serializer.serialize(t, destination);
            return destination;
        }
    }
}
