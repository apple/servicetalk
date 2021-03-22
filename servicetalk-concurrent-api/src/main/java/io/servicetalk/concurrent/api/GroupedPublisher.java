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
package io.servicetalk.concurrent.api;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A group as emitted by {@link #groupBy(Function, int)} or its variants.
 *
 * @param <Key> Key for the group.
 * @param <T> Items emitted by this {@link Publisher}.
 */
public abstract class GroupedPublisher<Key, T> extends Publisher<T> {
    private final Key key;

    GroupedPublisher(Key key) {
        this.key = requireNonNull(key);
    }

    /**
     * Returns the key for this group.
     *
     * @return Key for this group.
     */
    public final Key key() {
        return key;
    }

    /**
     * Provide the maximum queue size to use for a particular {@link GroupedPublisher} key.
     * @deprecated Use {@link Publisher#groupBy(Function, int)} instead.
     */
    @Deprecated
    public interface QueueSizeProvider {
        /**
         * Calculate the maximum queue size for a particular {@link GroupedPublisher} key.
         * @param groupMaxQueueSize The maximum queue size for {@link GroupedPublisher} objects.
         * @return The maximum queue size for a particular {@link GroupedPublisher} key.
         */
        int calculateMaxQueueSize(int groupMaxQueueSize);
    }

    @Override
    public String toString() {
        return getClass().getName() + '{' +
                "key=" + key +
                ", publisher=" + super.toString() +
                '}';
    }
}
