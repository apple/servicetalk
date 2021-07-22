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
import io.servicetalk.concurrent.api.Publisher;

import java.lang.reflect.ParameterizedType;
import java.util.Iterator;
import java.util.function.IntUnaryOperator;

/**
 * A contract for serialization and deserialization.
 * @deprecated Use the following types:
 * <ul>
 *     <li>{@link io.servicetalk.serializer.api.Serializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.StreamingSerializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.Deserializer}</li>
 *     <li>{@link io.servicetalk.serializer.api.StreamingDeserializer}</li>
 * </ul>
 */
@Deprecated
public interface Serializer {

    /**
     * Transforms the passed {@link Publisher} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Publisher<Buffer> serialize(Publisher<T> source, BufferAllocator allocator, Class<T> type);

    /**
     * Transforms the passed {@link Iterable} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Iterable} such that each contained element in the original {@link Iterable} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Iterable<Buffer> serialize(Iterable<T> source, BufferAllocator allocator, Class<T> type);

    /**
     * Transforms the passed {@link BlockingIterable} such that each contained element of type {@link T} is serialized
     * into a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> BlockingIterable<Buffer> serialize(BlockingIterable<T> source, BufferAllocator allocator, Class<T> type);

    /**
     * Transforms the passed {@link Publisher} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Publisher<Buffer> serialize(Publisher<T> source, BufferAllocator allocator, Class<T> type,
                                    IntUnaryOperator bytesEstimator);

    /**
     * Transforms the passed {@link Iterable} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Iterable} such that each contained element in the original {@link Iterable} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Iterable<Buffer> serialize(Iterable<T> source, BufferAllocator allocator, Class<T> type,
                                   IntUnaryOperator bytesEstimator);

    /**
     * Transforms the passed {@link BlockingIterable} such that each contained element of type {@link T} is serialized
     * into a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param type The class for {@link T}, the object to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * @param <T> The data type to serialize.
     *
     * size of the next object to be serialized in bytes.
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> BlockingIterable<Buffer> serialize(BlockingIterable<T> source, BufferAllocator allocator, Class<T> type,
                                           IntUnaryOperator bytesEstimator);

    /**
     * Transforms the passed {@link Publisher} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Publisher<Buffer> serialize(Publisher<T> source, BufferAllocator allocator, TypeHolder<T> typeHolder);

    /**
     * Transforms the passed {@link Iterable} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Iterable} such that each contained element in the original {@link Iterable} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Iterable<Buffer> serialize(Iterable<T> source, BufferAllocator allocator, TypeHolder<T> typeHolder);

    /**
     * Transforms the passed {@link BlockingIterable} such that each contained element of type {@link T} is serialized
     * into a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> BlockingIterable<Buffer> serialize(BlockingIterable<T> source, BufferAllocator allocator,
                                           TypeHolder<T> typeHolder);

    /**
     * Transforms the passed {@link Publisher} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Publisher<Buffer> serialize(Publisher<T> source, BufferAllocator allocator, TypeHolder<T> typeHolder,
                                    IntUnaryOperator bytesEstimator);

    /**
     * Transforms the passed {@link Iterable} such that each contained element of type {@link T} is serialized into
     * a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link Iterable} such that each contained element in the original {@link Iterable} is
     * transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> Iterable<Buffer> serialize(Iterable<T> source, BufferAllocator allocator, TypeHolder<T> typeHolder,
                                   IntUnaryOperator bytesEstimator);

    /**
     * Transforms the passed {@link BlockingIterable} such that each contained element of type {@link T} is serialized
     * into a {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer#serialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing objects to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     *
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link T} to a {@link Buffer}.
     */
    @Deprecated
    <T> BlockingIterable<Buffer> serialize(BlockingIterable<T> source, BufferAllocator allocator,
                                           TypeHolder<T> typeHolder, IntUnaryOperator bytesEstimator);

    /**
     * Serializes the passed object {@code toSerialize} to the returned {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.Serializer#serialize(Object, BufferAllocator)}.
     * @param toSerialize Object to serialize.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @param <T> The data type to serialize.
     *
     * @return {@link Buffer} containing the serialized representation of {@code toSerialize}.
     */
    @Deprecated
    <T> Buffer serialize(T toSerialize, BufferAllocator allocator);

    /**
     * Serializes the passed object {@code toSerialize} to the returned {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.Serializer#serialize(Object, BufferAllocator)}.
     * @param toSerialize Object to serialize.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @param bytesEstimate An estimate for the size in bytes of the serialized representation of {@code toSerialize}.
     * @param <T> The data type to serialize.
     *
     * @return {@link Buffer} containing the serialized representation of {@code toSerialize}.
     */
    @Deprecated
    <T> Buffer serialize(T toSerialize, BufferAllocator allocator, int bytesEstimate);

    /**
     * Serializes the passed object {@code toSerialize} to the passed {@link Buffer}.
     * @deprecated Use {@link io.servicetalk.serializer.api.Serializer}
     * @param toSerialize Object to serialize.
     * @param destination The {@link Buffer} to which the serialized representation of {@code toSerialize} is written.
     * @param <T> The data type to serialize.
     */
    @Deprecated
    <T> void serialize(T toSerialize, Buffer destination);

    /**
     * Applies a deserializer on the passed {@link Publisher} to convert into a {@link Publisher} of deserialized
     * instances of {@link T}. If the {@code source} {@link Publisher} terminates with some left over data in a
     * previously emitted {@link Buffer} but not deserialized in an object, then the returned {@link Publisher} will
     * terminate with an error.
     * <p>
     * If all content has been aggregated into a single {@link Buffer}, {@link #deserializeAggregated(Buffer, Class)}
     * can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing {@link Buffer}s to deserialize.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> Publisher<T> deserialize(Publisher<Buffer> source, TypeHolder<T> typeHolder);

    /**
     * Applies a deserializer on the passed {@link Iterable} to convert into a {@link Iterable} of deserialized
     * instances of {@link T}. If the {@code source} {@link Iterable} completes with some left over data in a
     * previously emitted {@link Buffer} but not deserialized in an object, then {@link Iterator} from the returned
     * {@link Iterable} will throw an {@link SerializationException} from {@link Iterator#hasNext()} after all
     * deserialized instances are emitted.
     * <p>
     * If all content has been aggregated into a single {@link Buffer},
     * {@link #deserializeAggregated(Buffer, TypeHolder)} can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing {@link Buffer}s to deserialize.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link CloseableIterable} such that each contained element in the original {@link Iterable}
     * is transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> CloseableIterable<T> deserialize(Iterable<Buffer> source, TypeHolder<T> typeHolder);

    /**
     * Applies a deserializer on the passed {@link BlockingIterable} to convert into a {@link BlockingIterable} of
     * deserialized instances of {@link T}. If the {@code source} {@link BlockingIterable} completes with some left
     * over data in a previously emitted {@link Buffer} but not deserialized in an object, then {@link BlockingIterator}
     * from the returned {@link BlockingIterable} will throw an {@link SerializationException} from
     * {@link BlockingIterator#hasNext()} after all deserialized instances are emitted.
     * <p>
     * If all content has been aggregated into a single {@link Buffer}, {@link #deserializeAggregated(Buffer, Class)}
     * can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing {@link Buffer}s to deserialize.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> BlockingIterable<T> deserialize(BlockingIterable<Buffer> source, TypeHolder<T> typeHolder);

    /**
     * Applies a deserializer on the passed {@link Publisher} to convert into a {@link Publisher} of deserialized
     * instances of {@link T}. If the {@code source} {@link Publisher} terminates with some left over data in a
     * previously emitted {@link Buffer} but not deserialized in an object, then the returned {@link Publisher} will
     * terminate with an error.
     * <p>
     * If all content has been aggregated into a single {@link Buffer}, {@link #deserializeAggregated(Buffer, Class)}
     * can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Publisher, BufferAllocator)}.
     * @param source {@link Publisher} containing {@link Buffer}s to deserialize.
     * @param type The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link Publisher} such that each contained element in the original {@link Publisher} is
     * transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> Publisher<T> deserialize(Publisher<Buffer> source, Class<T> type);

    /**
     * Applies a deserializer on the passed {@link Iterable} to convert into a {@link CloseableIterable} of deserialized
     * instances of {@link T}. If the {@code source} {@link Iterable} completes with some left over data in a
     * previously emitted {@link Buffer} but not deserialized in an object, then {@link CloseableIterator} from the
     * returned {@link CloseableIterable} will throw an {@link SerializationException} from
     * {@link CloseableIterator#hasNext()} after all deserialized instances are emitted.
     * <p>
     * If all content has been aggregated into a single {@link Buffer}, {@link #deserializeAggregated(Buffer, Class)}
     * can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param source {@link Iterable} containing {@link Buffer}s to deserialize.
     * @param type The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link CloseableIterable} such that each contained element in the original {@link Iterable}
     * is transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> CloseableIterable<T> deserialize(Iterable<Buffer> source, Class<T> type);

    /**
     * Applies a deserializer on the passed {@link BlockingIterable} to convert into a {@link Iterable} of deserialized
     * instances of {@link T}. If the {@code source} {@link BlockingIterable} completes with some left over data in a
     * previously emitted {@link Buffer} but not deserialized in an object, then {@link BlockingIterator} from the
     * returned {@link BlockingIterable} will throw an {@link SerializationException} from
     * {@link BlockingIterator#hasNext()} after all deserialized instances are emitted.
     * <p>
     * If all content has been aggregated into a single {@link Buffer}, {@link #deserializeAggregated(Buffer, Class)}
     * can be used.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param source {@link BlockingIterable} containing {@link Buffer}s to deserialize.
     * @param type The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return A transformed {@link BlockingIterable} such that each contained element in the original
     * {@link BlockingIterable} is transformed from type {@link Buffer} to an instance of {@link T}.
     */
    @Deprecated
    <T> BlockingIterable<T> deserialize(BlockingIterable<Buffer> source, Class<T> type);

    /**
     * Deserializes the passed encoded {@link Buffer} to one or more instances of {@link T}.
     *
     * <p><strong>Incomplete data</strong></p>
     *
     * This method assumes that the passed {@link Buffer} contains exact amount of data required to deserialize into
     * at least one complete instance of {@link T}. If there is any left over data in the {@link Buffer} after the
     * deserialization is complete, {@link CloseableIterator#hasNext()} and {@link CloseableIterator#next()} methods
     * will eventually throw a {@link SerializationException} from the {@link CloseableIterator} returned by
     * {@link CloseableIterable#iterator()}. In such a case, all deserialized data will first be returned from the
     * {@link CloseableIterator}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingDeserializer} that understands your protocol's
     * framing.
     * @param serializedData A {@link Buffer} containing serialized representation of one or more instances of
     * {@link T}.
     * @param type The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return An {@link CloseableIterable} containing one or more deserialized instances of {@link T}. If there is any
     * left over data in the {@link Buffer} after the deserialization is complete, {@link CloseableIterator#hasNext()}
     * and {@link CloseableIterator#next()} methods will eventually throw a {@link SerializationException} from the
     * {@link CloseableIterator} returned by {@link CloseableIterable#iterator()}. In such a case, all deserialized
     * data will first be returned from the {@link Iterator}.
     */
    @Deprecated
    <T> CloseableIterable<T> deserializeAggregated(Buffer serializedData, Class<T> type);

    /**
     * Deserializes the passed encoded {@link Buffer} to zero or more instances of {@link T}.
     *
     * <p><strong>Incomplete data</strong></p>
     *
     * This method assumes that the passed {@link Buffer} contains exact amount of data required to deserialize into
     * at least one complete instance of {@link T}. If there is any left over data in the {@link Buffer} after
     * deserialization is complete, {@link CloseableIterator#hasNext()} and {@link CloseableIterator#next()} methods
     * will eventually throw a {@link SerializationException} from the {@link CloseableIterator} returned by
     * {@link CloseableIterable#iterator()}. In such a case, all deserialized data will first be returned from the
     * {@link CloseableIterator}.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingDeserializer} that understands your protocol's
     * framing.
     * @param serializedData A {@link Buffer} containing serialized representation of one or more instances of
     * {@link T}.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return An {@link CloseableIterable} containing one or more deserialized instances of {@link T}. If there is any
     * left over data in the {@link Buffer} after the deserialization is complete, {@link CloseableIterator#hasNext()}
     * and {@link CloseableIterator#next()} methods will eventually throw a {@link SerializationException} from the
     * {@link CloseableIterator} returned by {@link CloseableIterable#iterator()}. In such a case, all deserialized
     * data will first be returned from the {@link CloseableIterator}.
     */
    @Deprecated
    <T> CloseableIterable<T> deserializeAggregated(Buffer serializedData, TypeHolder<T> typeHolder);

    /**
     * Deserializes the passed encoded {@link Buffer} to a single instance of {@link T}.
     * @deprecated Use {@link io.servicetalk.serializer.api.Deserializer}.
     * @param serializedData A {@link Buffer} containing serialized representation of a single instance of {@link T}.
     * @param type The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return The deserialized object.
     *
     * @throws SerializationException If the passed {@link Buffer} contains an incomplete object or if there is any
     * left over data in the {@link Buffer} after the deserialization is complete.
     */
    @Deprecated
    <T> T deserializeAggregatedSingle(Buffer serializedData, Class<T> type);

    /**
     * Deserializes the passed encoded {@link Buffer} to a single instance of {@link T}.
     * @deprecated Use {@link io.servicetalk.serializer.api.Deserializer}.
     * @param serializedData A {@link Buffer} containing serialized representation of a single instance of {@link T}.
     * @param typeHolder {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return The deserialized object.
     *
     * @throws SerializationException If the passed {@link Buffer} contains an incomplete object or if there is any
     * left over data in the {@link Buffer} after the deserialization is complete.
     */
    @Deprecated
    <T> T deserializeAggregatedSingle(Buffer serializedData, TypeHolder<T> typeHolder);
}
