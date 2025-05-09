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
 * <p>
 * If you want to capture any external state you can create a wrapper {@link CapturedContext} to add additional
 * state capturing to the context pathway. This state can then be restored by wrapping the {@link CapturedContext}
 * with the additional functionality to restore and finally revert the context state.
 * <p>
 * An example provider may be implemented as follows:
 * <pre>{@code
 *     private class CapturedContextImpl implements CapturedContext {
 *         private final CapturedContext delegate;
 *         private final String state;
 *
 *         public Scope attachContext() {
 *             String old = getMyString();
 *             setMyString(state);
 *             Scope outer = delegate.attachContext();
 *             return () -> {
 *                 outer.close();
 *                 setMyString(old);
 *             };
 *         }
 *
 *         public ContextMap captured() {
 *             return delegate.captured();
 *         }
 *     }
 *
 *     private MyState getMyState() {
 *         // capture context state from the local environment
 *     }
 *
 *     private void setMyState(MyState myState) {
 *         // set the context state in the local environment
 *     }
 *
 *     CapturedContext captureContext(CapturedContext underlying) {
 *          return new CapturedContextImpl(delegate, getMyState());
 *     }
 *
 *     CapturedContext captureContextCopy(CapturedContext underlying) {
 *          return new CapturedContextImpl(delegate, getMyState().copy());
 *     }
 * }</pre>
 *
 * <b>Note:</b> If the MyState type is immutable then there is no distinction between
 * captureContext(..) and captureContextCopy(..)
 */

public interface CapturedContextProvider {

    /**
     * Capture a reference to existing context in preparation for an asynchronous boundary jump.
     * @param underlying additional context that <i><b>must</b></i> be utilized as part of the returned
     * {@link CapturedContext}, usually wrapped as described above.
     * @return the wrapped {@link CapturedContext}.
     */
    CapturedContext captureContext(CapturedContext underlying);

    /**
     * Capture a copy of existing context in preparation for an asynchronous boundary jump.
     * @param underlying additional context that <i><b>must</b></i> be utilized as part of the returned
     * {@link CapturedContext}, usually wrapped as described above.
     * @return the wrapped {@link CapturedContext}.
     */
    CapturedContext captureContextCopy(CapturedContext underlying);
}
