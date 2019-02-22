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
package io.servicetalk.redis.api;

import static java.util.Objects.requireNonNull;

/**
 * A skeletal {@link RedisData} implementation to extend.
 *
 * @param <T> the type of this specific Redis data.
 */
public abstract class DefaultBaseRedisData<T> implements RedisData {

    private final T value;

    /**
     * Creates a new instance.
     *
     * @param value the data value.
     */
    protected DefaultBaseRedisData(final T value) {
        this.value = requireNonNull(value);
    }

    /**
     * Returns the associated value.
     *
     * @return The associated value.
     */
    public T value() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return o != null && o.getClass().equals(getClass()) && ((DefaultBaseRedisData) o).value.equals(value);
    }

    @Override
    public String toString() {
        return getClass().getName() + "{value=" + value + '}';
    }
}
