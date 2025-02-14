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

/**
 * Functionality related to capturing thread-local like context for later restoration across async boundaries.
 */
@FunctionalInterface
interface CapturedContextProvider {

    /**
     * Capture existing context in preparation for an asynchronous thread jump.
     *
     * If you want to capture any external state you can create a wrapper {@link CapturedContext} to add additional
     * state capturing to the context pathway. This state can then be restored by wrapping the {@link CapturedContext}
     * with the additional functionality to restore and finally revert the context state.
     * <p>
     * An example provider may be implemented as follows:
     * <pre>{@code
     *     private class CapturedContextImpl {
     *         private final CapturedContext delegate;
     *         private final String state;
     *
     *         Scope restoreContext() {
     *             String old = getMyString();
     *             setMyString(state);
     *             Scope outer = delegate.restoreContext();
     *             return () -> {
     *                 outer.close();
     *                 setMyString(old);
     *             };
     *         }
     *     }
     *
     *     private String getMyString() {
     *         // capture context state from the local environment
     *     }
     *
     *     private void setMyString(String string) {
     *         // set the context state in the local environment
     *     }
     *
     *     CapturedContext captureContext(CapturedContext underlying) {
     *          return new CapturedContextImpl(delegate, getMyString());
     *     }
     * }</pre>
     * @param underlying additional context that <i><b>must</b></i> be utilized as part of the returned
     * {@link CapturedContext}, usually wrapped as described above.
     * @return the wrapped {@link CapturedContext}, or the original if there was no additional state captured.
     */
    CapturedContext captureContext(CapturedContext underlying);
}
