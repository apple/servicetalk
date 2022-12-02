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
package io.servicetalk.buffer.api;

/**
 * An object which contains a {@link Buffer}.
 *
 * @deprecated This API is going to be removed in future releases with no planned replacement. If it cannot be
 *             removed from your application, consider copying it into your codebase.
 */
@Deprecated // FIXME: 0.43 - Remove deprecation
public interface BufferHolder {
    /**
     * The buffer contained by this object.
     * @return The buffer contained by this object.
     */
    Buffer content();

    /**
     * Duplicates this {@link BufferHolder}.
     * @return Duplicates this {@link BufferHolder}.
     */
    BufferHolder duplicate();

    /**
     * Returns a new {@link BufferHolder} which contains the specified {@code content}.
     * @param content The {@link Buffer} to replace what is currently returned by {@link #content()}.
     * @return a new {@link BufferHolder} which contains the specified {@code content}.
     */
    BufferHolder replace(Buffer content);
}
