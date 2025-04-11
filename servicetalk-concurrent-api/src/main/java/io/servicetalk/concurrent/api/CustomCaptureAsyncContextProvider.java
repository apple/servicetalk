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

import static java.util.Objects.requireNonNull;

final class CustomCaptureAsyncContextProvider extends DefaultAsyncContextProvider {

    private final CapturedContextProvider delegate;

    CustomCaptureAsyncContextProvider(CapturedContextProvider delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public CapturedContext captureContext(ContextMap contextMap) {
        return delegate.captureContext(super.captureContext(contextMap));
    }

    @Override
    public CapturedContext captureContextCopy() {
        return delegate.captureContextCopy(super.captureContextCopy());
    }
}
