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
import io.servicetalk.client.api.partition.DuplicateAttributeException;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributes.Key;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import static java.lang.Integer.compare;
import static java.util.Arrays.copyOf;

/**
 * Default implementation of {@link PartitionAttributesBuilder}.
 * <p>
 * This class provides a relatively low memory overhead (when compared to {@link TreeMap}) for a
 * {@link PartitionAttributes}. The goals are to provide fast {@link Object#equals(Object)} and
 * {@link Object#hashCode()} which do not require intermediate object allocation and pointer traversal
 * (e.g. {@link Iterator}).
 *
 * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
 * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers describing
 * the use-case.
 */
@Deprecated
public final class DefaultPartitionAttributesBuilder implements PartitionAttributesBuilder {
    private static final int PARENT_MASK = ~0x1;
    private Object[] keyValueArray;
    private int nextIndex;

    /**
     * Create a new instance.
     *
     * @param initialSize The anticipated number of key/value pairs that will be added via
     * {@link #add add(Key&lt;T&gt; key, T value)}.
     */
    public DefaultPartitionAttributesBuilder(int initialSize) {
        keyValueArray = new Object[initialSize << 1];
    }

    @Override
    public <T> PartitionAttributesBuilder add(Key<T> key, T value) {
        if (keyValueArray.length == nextIndex) {
            keyValueArray = copyOf(keyValueArray, (keyValueArray.length << 1) + 2);
        }

        // left child index = n * 2 + 2
        // right child index = n * 2 + 4
        // parent index = ((n - 2) / 2) & ~0x1
        // Bubble up to preserve max heap constraint
        int currentIndex = nextIndex;
        while (currentIndex > 0) {
            final int parentKeyIndex = ((currentIndex - 2) >>> 1) & PARENT_MASK;
            final Key parentKey = (Key) keyValueArray[parentKeyIndex];

            final int cmp = parentKey.compareTo(key);
            if (cmp > 0) {
                break;
            } else if (cmp == 0) {
                // We maintain a max heap during insertion, note that we do a best effort check for duplicates here
                // but it is not reliable because of the heap data structure.
                throw new DuplicateAttributeException(key, "duplicate key [" + key + "] with values: [" + value + ", " +
                        keyValueArray[parentKeyIndex + 1] + "]");
            }

            // Bubble down the parent.
            keyValueArray[currentIndex] = parentKey;
            keyValueArray[currentIndex + 1] = keyValueArray[parentKeyIndex + 1];

            currentIndex = parentKeyIndex;
        }

        keyValueArray[currentIndex] = key;
        keyValueArray[currentIndex + 1] = value;
        nextIndex += 2;
        return this;
    }

    @Override
    public PartitionAttributes build() {
        return new SortedArrayPartitionAttributes(nextIndex == this.keyValueArray.length ? this.keyValueArray :
                copyOf(this.keyValueArray, nextIndex));
    }

    private static final class SortedArrayPartitionAttributes
            implements PartitionAttributes, Comparable<SortedArrayPartitionAttributes> {
        private final Object[] keyValueArray;
        private final int hashCode;

        SortedArrayPartitionAttributes(Object[] keyValueArray) {
            heapSort(keyValueArray);
            this.keyValueArray = keyValueArray;
            int hashCode = 1;
            for (int i = 0; i < keyValueArray.length; i += 2) {
                hashCode = 31 * (31 * hashCode + keyValueArray[i].hashCode()) + keyValueArray[i + 1].hashCode();
            }
            this.hashCode = hashCode;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(Key<T> key) {
            int highKeyIndex = keyValueArray.length - 2;
            int lowKeyIndex = 0;
            while (lowKeyIndex <= highKeyIndex) {
                int midKeyIndex = lowKeyIndex + (((highKeyIndex - lowKeyIndex) >>> 1) & PARENT_MASK);
                int cmp = key.compareTo((Key) keyValueArray[midKeyIndex]);
                if (cmp > 0) {
                    lowKeyIndex = midKeyIndex + 2;
                } else if (cmp < 0) {
                    highKeyIndex = midKeyIndex - 2;
                } else {
                    return (T) keyValueArray[midKeyIndex + 1];
                }
            }
            return null;
        }

        @Override
        public void forEach(BiConsumer<Key, Object> action) {
            for (int i = 0; i < keyValueArray.length; i += 2) {
                action.accept((Key) keyValueArray[i], keyValueArray[i + 1]);
            }
        }

        @Override
        public int size() {
            return keyValueArray.length >>> 1;
        }

        @Override
        public boolean isEmpty() {
            return keyValueArray.length == 0;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof SortedArrayPartitionAttributes) && Arrays.equals(keyValueArray,
                    ((SortedArrayPartitionAttributes) o).keyValueArray);
        }

        @Override
        public String toString() {
            if (keyValueArray.length < 2) {
                return "";
            }
            StringBuilder sb = new StringBuilder(256);
            sb.append('<').append(keyValueArray[0]).append(", ").append(keyValueArray[1]).append('>');
            for (int i = 2; i < keyValueArray.length; i += 2) {
                sb.append(" <").append(keyValueArray[i]).append(", ").append(keyValueArray[i + 1]).append('>');
            }
            return sb.toString();
        }

        @Override
        public int compareTo(SortedArrayPartitionAttributes rhs) {
            int cmp = compare(size(), rhs.size());
            if (cmp != 0) {
                return cmp;
            }

            for (int i = 0; i < keyValueArray.length; i += 2) {
                Key key = (Key) keyValueArray[i];
                Key rhsKey = (Key) rhs.keyValueArray[i];
                cmp = key.compareTo(rhsKey);
                if (cmp != 0 || (cmp = ((Key) keyValueArray[i + 1]).compareTo((Key) rhs.keyValueArray[i + 1])) != 0) {
                    return cmp;
                }
            }

            return 0;
        }
    }

    private static void heapSort(Object[] keyValueArray) {
        final int originalEndKeyIndex = keyValueArray.length - 2;
        int endKeyIndex = originalEndKeyIndex;
        while (endKeyIndex > 0) {
            // Swap the node at endKeyIndex with the root of the tree.
            Key bubbleKey = (Key) keyValueArray[endKeyIndex];
            Object bubbleValue = keyValueArray[endKeyIndex + 1];
            keyValueArray[endKeyIndex] = keyValueArray[0];
            keyValueArray[endKeyIndex + 1] = keyValueArray[1];
            keyValueArray[0] = bubbleKey;
            keyValueArray[1] = bubbleValue;

            // Check for a duplicate key now that we are in sorted order.
            if (endKeyIndex <= originalEndKeyIndex - 2) {
                final Key duplicateCheckKey = (Key) keyValueArray[endKeyIndex];
                if (duplicateCheckKey.equals(keyValueArray[endKeyIndex + 2])) {
                    throw new DuplicateAttributeException(duplicateCheckKey, "duplicate key [" + duplicateCheckKey +
                            "] with values: [" + keyValueArray[endKeyIndex] + ", " + keyValueArray[endKeyIndex + 3] +
                            "]");
                }
            }

            final int halfEnd = (endKeyIndex >>> 1) & PARENT_MASK;
            endKeyIndex -= 2;

            // Bubble down the node that was placed at the root of the tree to preserve the max heap property
            int keyIndex = 0;
            while (keyIndex < halfEnd) {
                // We need to get the larger of the two children to compare the bubble key against.
                int childKeyIndex = (keyIndex << 1) + 2;
                Key childKey = (Key) keyValueArray[childKeyIndex];
                final int rightChildKeyIndex = (keyIndex << 1) + 4;
                final Key rightChildKey;
                if (rightChildKeyIndex <= endKeyIndex && childKey.compareTo((rightChildKey =
                        (Key) keyValueArray[rightChildKeyIndex])) < 0) {
                    childKey = rightChildKey;
                    childKeyIndex = rightChildKeyIndex;
                }

                // If the child is less than or equal to the bubble key then we are done because the max heap criteria
                // is satisfied.
                if (childKey.compareTo(bubbleKey) <= 0) {
                    break;
                }

                // Bubble the child up, because it is greater than the bubbleKey
                keyValueArray[keyIndex] = childKey;
                keyValueArray[keyIndex + 1] = keyValueArray[childKeyIndex + 1];

                keyIndex = childKeyIndex;
            }

            keyValueArray[keyIndex] = bubbleKey;
            keyValueArray[keyIndex + 1] = bubbleValue;
        }
    }
}
