/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import javax.annotation.Nullable;

/**
 * Consumer of items from a buffer.
 *
 * @param <T>  Type of items {@link #consumeItem(Object) consumed} by this buffer.
 */
public interface BufferConsumer<T> {

    /**
     * Consumes the passed {@code item}.
     *
     * @param item to consume. This will be {@code null} if {@code null} was added to the buffer.
     */
    void consumeItem(@Nullable T item);

    /**
     * Consumes the {@link Throwable} that terminated the buffer.
     *
     * @param cause of termination of the buffer.
     */
    void consumeTerminal(Throwable cause);

    /**
     * Consumes the termination of the buffer.
     */
    void consumeTerminal();
}
