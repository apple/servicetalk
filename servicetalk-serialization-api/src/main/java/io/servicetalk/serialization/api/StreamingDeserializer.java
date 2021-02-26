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
import io.servicetalk.concurrent.GracefulAutoCloseable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A contract capable of deserializing a stream of {@link Buffer}s into a stream of {@link T}s.
 * This interface is designed to be used as a function that can convert a {@link Buffer} into a {@link T}.
 * {@link #deserialize(Buffer)} maybe called multiple times.
 * <p>
 * Implementations are assumed to be stateful since a single {@link Buffer} may not hold
 * enough data to deserialize an entire object. It is expected that {@link #deserialize(Buffer)} or
 * {@link #deserialize(Iterable)} may be called multiple times to deserialize a single instance of {@link T} as more
 * data is available.
 * <p>
 * <em>Implementations are assumed to be synchronous.</em>
 * @deprecated Use {@link io.servicetalk.serializer.api.StreamingDeserializer}.
 * @param <T> Type of object to be deserialized.
 */
@Deprecated
public interface StreamingDeserializer<T> extends GracefulAutoCloseable {

    /**
     * Deserialize the passed {@link Buffer} into an {@link Iterable} of {@link T}s. If this {@link Buffer} and any
     * previous left over data is insufficient to deserialize to a single instance of {@link T} then the returned
     * {@link Iterable} will be empty, i.e. the returned {@link Iterator} from {@link Iterable#iterator()} will always
     * return {@code false} from {@link Iterator#hasNext()}.
     * <p>
     * It is assumed that a single instance of {@link StreamingDeserializer} may receive calls to both this method and
     * {@link #deserialize(Iterable)}. Any left over data from one call is used by a subsequent call to the same or
     * different method.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingDeserializer} that understands your protocol's
     * framing.
     * @param toDeserialize {@link Buffer} to deserialize.
     * @return {@link Iterable} containing zero or more deserialized instances of {@link T}, if any can be deserialized
     * from the data received till now.
     */
    @Deprecated
    Iterable<T> deserialize(Buffer toDeserialize);

    /**
     * Deserialize the passed {@link Iterable} of {@link Buffer}s into an {@link Iterable} of {@link T}s. If these
     * {@link Buffer}s and any previous left over data is insufficient to deserialize to a single instance of {@link T}
     * then the returned {@link Iterable} will be empty, i.e. the returned {@link Iterator} from
     * {@link Iterable#iterator()} will always return {@code false} from {@link Iterator#hasNext()}.
     * <p>
     * It is assumed that a single instance of {@link StreamingDeserializer} may receive calls to both this method and
     * {@link #deserialize(Buffer)}. Any left over data from one call is used by a subsequent call to the same or
     * different method.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param toDeserialize {@link Iterable} of {@link Buffer}s to deserialize.
     * @return {@link Iterable} containing zero or more deserialized instances of {@link T}, if any can be deserialized
     * from the data received till now.
     */
    @Deprecated
    default Iterable<T> deserialize(Iterable<Buffer> toDeserialize) {
        List<T> deserialized = new ArrayList<>(2);
        for (Buffer buffer : toDeserialize) {
            for (T t : deserialize(buffer)) {
                deserialized.add(t);
            }
        }
        return deserialized;
    }

    /**
     * Deserialize the passed {@link BlockingIterable} of {@link Buffer}s into a {@link BlockingIterable} of {@link T}s.
     * If these {@link Buffer}s and any previous left over data is insufficient to deserialize to a single instance of
     * {@link T} then the returned {@link BlockingIterable} will be empty, i.e. the returned {@link BlockingIterable}
     * from {@link BlockingIterable#iterator()} will always return {@code false} from
     * {@link BlockingIterator#hasNext()}.
     * <p>
     * It is assumed that a single instance of {@link StreamingDeserializer} may receive calls to both this method and
     * {@link #deserialize(Buffer)}. Any left over data from one call is used by a subsequent call to the same or
     * different method.
     * @deprecated Use
     * {@link io.servicetalk.serializer.api.StreamingDeserializer#deserialize(Iterable, BufferAllocator)}.
     * @param toDeserialize {@link BlockingIterable} of {@link Buffer}s to deserialize.
     * @return {@link BlockingIterable} containing zero or more deserialized instances of {@link T}, if any can be
     * deserialized from the data received till now.
     */
    @Deprecated
    default BlockingIterable<T> deserialize(BlockingIterable<Buffer> toDeserialize) {
        return new BlockingIterableFlatMap<>(toDeserialize, this::deserialize);
    }

    /**
     * Returns {@code true} if this {@link StreamingDeserializer} contains any data from a previous invocation of
     * {@link #deserialize(Buffer)} that has not yet been deserialized.
     *
     * @return {@code true} if this {@link StreamingDeserializer} contains any data from a previous invocation of
     * {@link #deserialize(Buffer)} that has not yet been deserialized.
     */
    boolean hasData();

    /**
     * Disposes this {@link StreamingDeserializer}. If there is any left-over data left in this
     * {@link StreamingDeserializer} from a previous call to {@link #deserialize(Buffer)} which has not been consumed,
     * this method should throw an {@link SerializationException} to indicate that there is more data that is going to
     * be disposed.
     *
     * @throws SerializationException If there is some over data left but not consumed as returned by
     * {@link #hasData()}.
     */
    @Override
    void close();
}
