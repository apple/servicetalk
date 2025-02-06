/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;

/**
 * An abstraction for detaching a context from the current thread.
 *
 * This abstraction is intended to allow the modifications performed by
 * {@link AsyncContextProvider#attachContextMap(ContextMap)} to be undone. In practice, this may look like restoring
 * a {@link ThreadLocal} to the state it had before the call to
 * {@link AsyncContextProvider#attachContextMap(ContextMap)}.
 */
interface Scope extends AutoCloseable {

    /**
     * No-op {@link Scope}.
     */
    Scope NOOP = () -> { };

    @Override
    void close();
}
