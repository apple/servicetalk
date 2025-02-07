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
 * A representation of a context state that can be attached to the current thread.
 *
 * Instances represent captured context state which includes things like the {@link AsyncContext} state and potentially
 * additional state if instances of {@link CapturedContextProvider} are found. This state can be attached to the current
 * thread via the {@link CapturedContext#attachContext()} method which will return a {@link Scope} used to detach this
 * state, restoring any context information that existed beforehand.
 */
interface CapturedContext {

    /**
     * The {@link ContextMap} that was captured.
     * @return {@link ContextMap} that was captured.
     */
    ContextMap captured();

    /**
     * Attach the captured context to the thread state.
     * @return a {@link Scope} that will be used to restore the previous context associated with the current thread
     * state when the scoped operation completes.
     */
    Scope attachContext();
}
