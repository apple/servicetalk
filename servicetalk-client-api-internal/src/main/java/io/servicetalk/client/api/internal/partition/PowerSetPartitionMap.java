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
package io.servicetalk.client.api.internal.partition;

import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionMap;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static java.lang.Integer.bitCount;
import static java.lang.Integer.numberOfTrailingZeros;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

/**
 * A {@link PartitionMap} that creates the full power set using the individual attributes in
 * {@link PartitionAttributes}es to create partitions for each {@link #add(PartitionAttributes)}.
 *
 * @param <T> The partition type.
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public final class PowerSetPartitionMap<T extends AsyncCloseable> implements PartitionMap<T> {
    private static final byte CLOSED_GRACEFULLY = 1;
    private static final byte HARD_CLOSE = 2;
    private static final int MAX_PARTITION_ATTRIBUTE_SIZE = 15;

    private final Function<PartitionAttributes, T> valueFactory;
    private final IntFunction<PartitionAttributesBuilder> partitionAttributesBuilderFunc;

    /**
     * Key = Absolute Attribute
     * Value = List of Wild Card Attributes. Each value must be a key in {@link #wildCardToValueMap}.
     *
     * <p>This map is used to prevent a complete iteration over {@link #wildCardToValueMap} when an attributes needs to
     * be removed. Since {@link #wildCardToValueMap} holds combinations it is expected to be big and we should avoid
     * iterating it when possible.
     */
    private final Map<PartitionAttributes, Set<PartitionAttributes>> absoluteToWildCardIndexMap;

    /**
     * Key = Wild Card Attribute.
     * Value = Object associated with the wildcard attributes.
     *
     * <p>This map contains all combinations of the attributes of each absolute Attribute to provide a fast lookup when
     * given a Wild Card Attributes.
     * <p>This map and the values contained within are unmodifiable and modifications are treated as copy on write.
     * This approach allows the {@link #get(PartitionAttributes)} method which reads from this map and is expected to be
     * called much more frequently than other methods which modify to the map can function with minimal synchronization.
     */
    private volatile Map<PartitionAttributes, ValueHolder<T>> wildCardToValueMap;

    private volatile byte closed;
    private final ListenableAsyncCloseable asyncCloseable = toAsyncCloseable(graceful -> {
        closed = graceful ? CLOSED_GRACEFULLY : HARD_CLOSE;
        return closeAllValues(wildCardToValueMap, graceful);
    });

    /**
     * Create a new instance with the {@link DefaultPartitionAttributesBuilder}.
     * @param valueFactory Generates values for new partitions.
     */
    PowerSetPartitionMap(Function<PartitionAttributes, T> valueFactory) {
        this(valueFactory, DefaultPartitionAttributesBuilder::new);
    }

    /**
     * Create a new instance.
     * @param valueFactory Generates values for new partitions.
     * @param partitionAttributesBuilderFunc Generates new {@link PartitionAttributes} objects, this factory must be
     * consistent with the factory used to build the {@link PartitionAttributes} objects for
     * {@link #add(PartitionAttributes)} and {@link #remove(PartitionAttributes)} to ensure {@link #hashCode()} and
     * {@link #equals(Object)} are consistent.
     */
    public PowerSetPartitionMap(Function<PartitionAttributes, T> valueFactory,
                                IntFunction<PartitionAttributesBuilder> partitionAttributesBuilderFunc) {
        this.valueFactory = requireNonNull(valueFactory);
        this.partitionAttributesBuilderFunc = requireNonNull(partitionAttributesBuilderFunc);
        absoluteToWildCardIndexMap = new HashMap<>();
        wildCardToValueMap = emptyMap();
    }

    @Override
    public T get(@Nullable PartitionAttributes wildCardAttributes) {
        ValueHolder<T> valueHolder = wildCardToValueMap.get(wildCardAttributes);
        return valueHolder == null ? null : valueHolder.value;
    }

    @Override
    public List<T> add(final PartitionAttributes partition) {
        final int partitionSize = partition.size();
        if (partitionSize <= 0 || partitionSize > MAX_PARTITION_ATTRIBUTE_SIZE) {
            throw new IllegalArgumentException("attribute size: " + partitionSize + " must be in the range [0," +
                    MAX_PARTITION_ATTRIBUTE_SIZE + ")");
        }

        // Make a copy of the current wildCardToValueMap because we will copy/swap
        Map<PartitionAttributes, ValueHolder<T>> newWildCardToAttributes = new HashMap<>(wildCardToValueMap);

        // Calculate all combinations of attributes from the new absoluteAttributes map
        // This involves first putting the elements into a fixed array, and then iterate over that array
        // and select all unique subsets to make all combinations.
        List<Object> entries = new ArrayList<>(partitionSize << 1);
        partition.forEach((key, value) -> {
            entries.add(key);
            entries.add(value);
        });

        final int numCombinations = 1 << partitionSize;
        final List<T> effectedPartitions = new ArrayList<>(numCombinations);
        for (int i = 1; i < numCombinations; ++i) {
            // wildCardAttributesBuilder will represent the current combination of attributes
            PartitionAttributesBuilder wildCardAttributesBuilder = partitionAttributesBuilderFunc.apply(bitCount(i));
            int remainingBits = i;
            do {
                int entriesIndex = numberOfTrailingZeros(remainingBits);
                remainingBits &= ~(1 << entriesIndex);
                wildCardAttributesBuilder.add((PartitionAttributes.Key) entries.get(entriesIndex << 1),
                        entries.get((entriesIndex << 1) + 1));
            } while (remainingBits != 0);

            PartitionAttributes wildCardAttributes = wildCardAttributesBuilder.build();

            // Update the new wild card attribute index
            ValueHolder<T> valueHolder = newWildCardToAttributes.get(wildCardAttributes);
            if (valueHolder != null) {
                ++valueHolder.refCount;
            } else {
                valueHolder = new ValueHolder<>(valueFactory.apply(wildCardAttributes));
                newWildCardToAttributes.put(wildCardAttributes, valueHolder);
            }
            // Update the absolute to wild card index ... this makes removal/replacement of entries easier later.
            absoluteToWildCardIndexMap.computeIfAbsent(partition,
                    attributes -> new HashSet<>(2)).add(wildCardAttributes);
            effectedPartitions.add(valueHolder.value);
        }

        wildCardToValueMap = newWildCardToAttributes;

        // It is likely/possible that we generated new objects above, and so we must ensure that these are closed.
        if (closed > 0) {
            closeAllValues(newWildCardToAttributes, closed == CLOSED_GRACEFULLY).subscribe();
        }

        return effectedPartitions;
    }

    @Override
    public List<T> remove(PartitionAttributes partition) {
        Set<PartitionAttributes> removedWildCardAttributes = absoluteToWildCardIndexMap.remove(partition);
        if (removedWildCardAttributes == null) {
            return emptyList();
        }

        // Make a copy of the current wildCardToValueMap because we will copy/swap
        Map<PartitionAttributes, ValueHolder<T>> newWildCardToAttributes = new HashMap<>(wildCardToValueMap);

        List<T> effectedPartitions = new ArrayList<>(removedWildCardAttributes.size());

        for (PartitionAttributes wildCardAttribute : removedWildCardAttributes) {
            ValueHolder<T> valueHolder = newWildCardToAttributes.get(wildCardAttribute);
            assert valueHolder != null;
            if (--valueHolder.refCount == 0) {
                newWildCardToAttributes.remove(wildCardAttribute);
            }
            effectedPartitions.add(valueHolder.value);
        }

        wildCardToValueMap = newWildCardToAttributes;

        return effectedPartitions;
    }

    @Override
    public Completable onClose() {
        return asyncCloseable.onClose();
    }

    @Override
    public Completable onClosing() {
        return asyncCloseable.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return asyncCloseable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return asyncCloseable.closeAsyncGracefully();
    }

    private Completable closeAllValues(Map<PartitionAttributes, ValueHolder<T>> wildCardToValueMap, boolean graceful) {
        List<Completable> completables = new ArrayList<>(wildCardToValueMap.size());
        wildCardToValueMap.forEach((attributes, holder) ->
                completables.add(graceful ? holder.value.closeAsyncGracefully() : holder.value.closeAsync()));
        return Completable.completed().mergeDelayError(completables);
    }

    private static final class ValueHolder<T> {
        final T value;
        int refCount;

        ValueHolder(T value) {
            this.value = requireNonNull(value);
            refCount = 1;
        }

        @Override
        public boolean equals(Object o) {
            return value.equals(o);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    boolean isEmpty() {
        return absoluteToWildCardIndexMap.isEmpty() && wildCardToValueMap.isEmpty();
    }

    int size() {
        return absoluteToWildCardIndexMap.size();
    }

    int wildCardIndexSize() {
        return wildCardToValueMap.size();
    }
}
