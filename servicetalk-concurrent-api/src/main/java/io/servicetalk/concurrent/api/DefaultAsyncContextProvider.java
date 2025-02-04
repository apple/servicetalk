/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

final class DefaultAsyncContextProvider extends AbstractAsyncContextProvider {

    static final DefaultAsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();

    private DefaultAsyncContextProvider() {
        // singleton
    }

    @Override
    public CapturedContext captureContext() {
        ContextMap current = context();
        return () -> ContextMapThreadLocal.attachContext(current);
    }

    @Override
    public CapturedContext captureContextCopy() {
        ContextMap currentCopy = context().copy();
        return () -> ContextMapThreadLocal.attachContext(currentCopy);
    }

    private static ContextMap newContextMap() {
        return new CopyOnWriteContextMap();
    }
}
