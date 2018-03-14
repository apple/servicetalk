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
package io.servicetalk.concurrent.context;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import static java.lang.System.arraycopy;

final class CopyOnWriteContext implements AsyncContextMap {
    private static final Object[] EMPTY_CONTEXT_ARRAY = new Object[0];
    static final CopyOnWriteContext EMPTY_CONTEXT = new CopyOnWriteContext(EMPTY_CONTEXT_ARRAY);
    /**
     * Array of <[i] = key, [i+1] = value> pairs.
     */
    private final Object[] context;

    private CopyOnWriteContext(Object[] context) {
        this.context = context;
    }

    @Override
    public boolean isEmpty() {
        return context.length == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Key<T> key) {
        int i = findIndex(key);
        return i < 0 ? null : (T) context[i + 1];
    }

    @Override
    public boolean contains(Key<?> key) {
        return findIndex(key) >= 0;
    }

    @Override
    public <T> AsyncContextMap put(Key<T> key, @Nullable T value) {
        int i = findIndex(Objects.requireNonNull(key));
        final Object[] context;
        if (i < 0) {
            context = new Object[this.context.length + 2];
            arraycopy(this.context, 0, context, 0, this.context.length);
            context[this.context.length] = key;
            context[this.context.length + 1] = value;
        } else {
            context = new Object[this.context.length];
            arraycopy(this.context, 0, context, 0, i + 1);
            context[i + 1] = value;
            if (i + 2 < context.length) {
                arraycopy(this.context, i + 2, context, i + 2, context.length - i - 2);
            }
        }
        return new CopyOnWriteContext(context);
    }

    @Override
    public AsyncContextMap putAll(AsyncContextMap c) {
        if (c.isEmpty()) {
            return this;
        }
        PutIndexItemsIterator createKeyIndexItr = new PutIndexItemsIterator();
        // First pass is to see how many new entries there are to get the correct size of the new context array
        c.forEach(createKeyIndexItr);

        Object[] context = new Object[this.context.length + (createKeyIndexItr.newItems << 1)];
        arraycopy(this.context, 0, context, 0, this.context.length);

        // Second pass is to fill entries where the keys overlap, or append new entries to the end
        PutPopulateNewArrayIterator populateItr = new PutPopulateNewArrayIterator(createKeyIndexItr.keyIndexes, this.context, context);
        c.forEach(populateItr);
        return new CopyOnWriteContext(context);
    }

    @Override
    public AsyncContextMap putAll(Map<Key<?>, Object> map) {
        PutAllConsumer consumer = new PutAllConsumer(map.size());
        // First pass is to see how many new entries there are to get the correct size of the new context array
        map.forEach(consumer);

        Object[] context = new Object[this.context.length + (consumer.newItems << 1)];
        arraycopy(this.context, 0, context, 0, this.context.length);

        // Second pass is to fill entries where the keys overlap, or append new entries to the end
        PutAllPopulateConsumer populateConsumer = new PutAllPopulateConsumer(consumer.keyIndexes, this.context, context);
        map.forEach(populateConsumer);
        return new CopyOnWriteContext(context);
    }

    @Override
    public AsyncContextMap remove(Key<?> key) {
        int i = findIndex(key);
        if (i < 0) {
            return this;
        }
        if (context.length == 2) {
            return EMPTY_CONTEXT;
        }
        Object[] context = new Object[this.context.length - 2];
        arraycopy(this.context, 0, context, 0, i);
        arraycopy(this.context, i + 2, context, i, this.context.length - i - 2);
        return new CopyOnWriteContext(context);
    }

    @Override
    public AsyncContextMap removeAll(AsyncContextMap c) {
        GrowableIntArray indexesToRemove = new GrowableIntArray(3);
        c.forEach((t, u) -> {
            int keyIndex = findIndex(t);
            if (keyIndex >= 0) {
                indexesToRemove.add(keyIndex);
            }
            return Boolean.TRUE;
        });
        return removeAll0(indexesToRemove);
    }

    @Override
    public AsyncContextMap removeAll(Iterable<Key<?>> entries) {
        GrowableIntArray indexesToRemove = new GrowableIntArray(3);
        entries.forEach(key -> {
            int keyIndex = findIndex(key);
            if (keyIndex >= 0) {
                indexesToRemove.add(keyIndex);
            }
        });

        return removeAll0(indexesToRemove);
    }

    @Override
    public AsyncContextMap clear() {
        return EMPTY_CONTEXT;
    }

    private int findIndex(Key<?> key) {
        for (int i = 0; i < context.length; i += 2) {
            if (key.equals(context[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Key<?> forEach(BiFunction<Key<?>, Object, Boolean> consumer) {
        for (int i = 0; i < context.length; i += 2) {
            final Key<?> key = (Key<?>) context[i];
            if (!consumer.apply(key, context[i + 1])) {
                return key;
            }
        }
        return null;
    }

    private AsyncContextMap removeAll0(GrowableIntArray indexesToRemove) {
        if (this.context.length >>> 1 == indexesToRemove.size) {
            return EMPTY_CONTEXT;
        } else if (indexesToRemove.size == 0) {
            return this;
        }

        Object[] context = new Object[this.context.length - (indexesToRemove.size << 1)];

        // Preserve entries that were not found in the first pass
        int newContextIndex = 0;
        for (int i = 0; i < this.context.length; i += 2) {
            if (!indexesToRemove.contains(i)) {
                context[newContextIndex] = this.context[i];
                context[newContextIndex + 1] = this.context[i + 1];
                newContextIndex += 2;
            }
        }
        return new CopyOnWriteContext(context);
    }

    private static final class GrowableIntArray {
        private int[] array;
        int size;

        GrowableIntArray(int size) {
            array = new int[size];
        }

        void add(int value) {
            if (size == array.length) {
                int[] newArray = new int[array.length << 1];
                arraycopy(array, 0, newArray, 0, array.length);
                array = newArray;
            }
            array[size++] = value;
        }

        int get(int i) {
            return array[i];
        }

        boolean contains(int value) {
            for (int i = 0; i < size; ++i) {
                if (array[i] == value) {
                    return true;
                }
            }
            return false;
        }
    }

    private final class PutIndexItemsIterator implements BiFunction<Key<?>, Object, Boolean> {
        int newItems;
        final GrowableIntArray keyIndexes = new GrowableIntArray(3);

        @Override
        public Boolean apply(Key<?> t, Object u) {
            int keyIndex = findIndex(t);
            if (keyIndex < 0) {
                ++newItems;
            }
            keyIndexes.add(keyIndex);
            return Boolean.TRUE;
        }
    }

    private static final class PutPopulateNewArrayIterator implements BiFunction<Key<?>, Object, Boolean> {
        private int i;
        private int newItemIndex;
        private final GrowableIntArray keyIndexes;
        private final Object[] newContext;

        PutPopulateNewArrayIterator(GrowableIntArray keyIndexes, Object[] oldContext, Object[] newContext) {
            this.keyIndexes = keyIndexes;
            this.newContext = newContext;
            newItemIndex = oldContext.length;
        }

        @Override
        public Boolean apply(Key<?> t, Object u) {
            final int keyIndex = keyIndexes.get(i++);
            if (keyIndex < 0) {
                newContext[newItemIndex] = t;
                newContext[newItemIndex + 1] = u;
                newItemIndex += 2;
            } else {
                newContext[keyIndex] = t;
                newContext[keyIndex + 1] = u;
            }
            return Boolean.TRUE;
        }
    }

    private final class PutAllConsumer implements BiConsumer<Key<?>, Object> {
        int newItems;
        final GrowableIntArray keyIndexes;

        PutAllConsumer(int mapSize) {
            keyIndexes = new GrowableIntArray(mapSize);
        }

        @Override
        public void accept(Key<?> key, Object o) {
            int keyIndex = findIndex(key);
            if (keyIndex < 0) {
                ++newItems;
            }
            keyIndexes.add(keyIndex);
        }
    }

    private static final class PutAllPopulateConsumer implements BiConsumer<Key<?>, Object> {
        private int i;
        private int newItemIndex;
        private final GrowableIntArray keyIndexes;
        private final Object[] newContext;

        PutAllPopulateConsumer(GrowableIntArray keyIndexes, Object[] oldContext, Object[] newContext) {
            this.keyIndexes = keyIndexes;
            this.newContext = newContext;
            newItemIndex = oldContext.length;
        }

        @Override
        public void accept(Key<?> key, Object o) {
            final int keyIndex = keyIndexes.get(i++);
            if (keyIndex < 0) {
                newContext[newItemIndex] = key;
                newContext[newItemIndex + 1] = o;
                newItemIndex += 2;
            } else {
                newContext[keyIndex] = key;
                newContext[keyIndex + 1] = o;
            }
        }
    }
}
