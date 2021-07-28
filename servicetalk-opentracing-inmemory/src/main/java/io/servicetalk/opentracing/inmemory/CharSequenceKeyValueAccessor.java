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
package io.servicetalk.opentracing.inmemory;

import javax.annotation.Nullable;

/**
 * An Key Value accessor interface that works with {@link CharSequence} types.
 */
public interface CharSequenceKeyValueAccessor {

    /**
     * Returns the {@link CharSequence} value related to the given {@link CharSequence} key, if one exists.
     * @param key The key to be used for value lookup.
     * @return A {@link CharSequence} value if one found for the given {@link CharSequence} key.
     */
    @Nullable
    CharSequence get(CharSequence key);

    /**
     * Stores the {@link CharSequence} value to the given {@link CharSequence} key.
     * If the key already exists, its expected that it will be replaced with this {@link CharSequence} value.
     * @param key The key to be used for key-value association.
     * @param value The value to be stored for the given key.
     */
    void set(CharSequence key, CharSequence value);
}
