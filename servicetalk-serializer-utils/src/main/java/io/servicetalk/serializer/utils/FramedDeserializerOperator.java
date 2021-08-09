/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.serializer.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.StreamingDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * Utility which helps implementations of {@link StreamingDeserializer} leverage a {@link Deserializer} and apply a
 * framing to define the boundaries of each object.
 * @param <T> The type to serialize/deserialize.
 */
public final class FramedDeserializerOperator<T> implements PublisherOperator<Buffer, Iterable<T>> {
    private final Deserializer<T> deserializer;
    private final BufferAllocator allocator;
    private final Supplier<BiFunction<Buffer, BufferAllocator, Buffer>> deframerSupplier;

    /**
     * Create a new instance.
     * @param deserializer The {@link Deserializer} to deserialize each individual item.
     * @param deframerSupplier Provides a {@link Function} for each
     * {@link PublisherSource#subscribe(Subscriber) subscribe} which is invoked each time a {@link Buffer} arrives. The
     * {@link Function} is expected to return the a {@link Buffer} with enough data that the {@link Deserializer} can
     * deserialize, or {@code null} if there isn't enough data.
     * @param allocator Used to allocate {@link Buffer}s to aggregate data across deserialization calls if necessary.
     */
    public FramedDeserializerOperator(final Deserializer<T> deserializer,
                                      final Supplier<BiFunction<Buffer, BufferAllocator, Buffer>> deframerSupplier,
                                      final BufferAllocator allocator) {
        this.deserializer = requireNonNull(deserializer);
        this.allocator = requireNonNull(allocator);
        this.deframerSupplier = requireNonNull(deframerSupplier);
    }

    @Override
    public Subscriber<? super Buffer> apply(final Subscriber<? super Iterable<T>> subscriber) {
        return new FramedSubscriber(subscriber, deframerSupplier.get());
    }

    private final class FramedSubscriber implements Subscriber<Buffer> {
        @Nullable
        private Subscription subscription;
        @Nullable
        private CompositeBuffer compositeBuffer;
        private final BiFunction<Buffer, BufferAllocator, Buffer> deframer;
        private final Subscriber<? super Iterable<T>> subscriber;

        FramedSubscriber(final Subscriber<? super Iterable<T>> subscriber,
                         final BiFunction<Buffer, BufferAllocator, Buffer> deframer) {
            this.deframer = requireNonNull(deframer);
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            this.subscription = ConcurrentSubscription.wrap(subscription);
            subscriber.onSubscribe(this.subscription);
        }

        @Override
        public void onNext(@Nullable final Buffer buffer) {
            assert subscription != null;
            if (buffer == null) {
                subscription.request(1);
            } else if (compositeBuffer != null && compositeBuffer.readableBytes() != 0) {
                compositeBuffer.addBuffer(buffer);
                doDeserialize(compositeBuffer);
            } else {
                doDeserialize(buffer);
            }
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            if (compositeBuffer != null && compositeBuffer.readableBytes() != 0) {
                subscriber.onError(new SerializationException("Deserialization completed with " +
                        compositeBuffer.readableBytes() + " remaining bytes"));
            } else {
                subscriber.onComplete();
            }
        }

        private void doDeserialize(final Buffer input) {
            assert subscription != null;
            Buffer buff = deframer.apply(input, allocator);
            if (buff != null) {
                Buffer buff2 = deframer.apply(input, allocator);
                final List<T> result;
                if (buff2 == null) {
                    result = singletonList(deserializer.deserialize(buff, allocator));
                } else {
                    result = new ArrayList<>(3);
                    result.add(deserializer.deserialize(buff, allocator));
                    do {
                        result.add(deserializer.deserialize(buff2, allocator));
                    } while ((buff2 = deframer.apply(input, allocator)) != null);
                }
                if (input == compositeBuffer) {
                    compositeBuffer.discardSomeReadBytes();
                } else if (input.readableBytes() != 0) {
                    addBuffer(input);
                }
                subscriber.onNext(result);
            } else {
                if (input != compositeBuffer) {
                    addBuffer(input);
                }
                subscription.request(1);
            }
        }

        private void addBuffer(Buffer buffer) {
            if (compositeBuffer == null) {
                compositeBuffer = allocator.newCompositeBuffer(Integer.MAX_VALUE);
            }
            compositeBuffer.addBuffer(buffer, true);
        }
    }
}
