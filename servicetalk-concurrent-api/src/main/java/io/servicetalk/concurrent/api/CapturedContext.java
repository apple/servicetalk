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
 * An interface representing the restoration of the thread-local like context that can be restored later
 * during an async operation.
 */
interface CapturedContext {

    /**
     * The {@link ContextMap} that was captured as part of the context.
     * @return {@link ContextMap} that was captured as part of the context.
     */
    ContextMap captured();

    /**
     * Restore the thread-local like context.
     * @return a {@link Scope} that will revert the restoration and return the thread-local like state to the state
     * that it had before restoring this context.
     */
    Scope restoreContext();
}
