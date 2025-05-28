/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

final class ContextPreservingCancellable implements Cancellable {
    // TODO: remove after 0.42.55
    private final ContextMap saved;
    private final CapturedContext capturedContext;
    private final Cancellable delegate;

    private ContextPreservingCancellable(Cancellable delegate, CapturedContext current) {
        this.capturedContext = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
        this.saved = capturedContext.captured();
    }

    static Cancellable wrap(Cancellable delegate, CapturedContext capturedContext) {
        // The double wrapping can be observed when folks manually create a Single/Completable and directly call the
        // onSubscribe method.
        return delegate instanceof ContextPreservingCancellable &&
                ((ContextPreservingCancellable) delegate).capturedContext == capturedContext ? delegate :
                new ContextPreservingCancellable(delegate, capturedContext);
    }

    @Override
    public void cancel() {
        try (Scope ignored = capturedContext.attachContext()) {
            delegate.cancel();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + delegate + ')';
    }
}
