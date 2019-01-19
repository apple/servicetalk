/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCancellable implements Cancellable {
    private final AsyncContextMap saved;
    private final Cancellable delegate;

    private ContextPreservingCancellable(Cancellable delegate, AsyncContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    static Cancellable wrap(Cancellable delegate, AsyncContextMap current) {
        // The double wrapping can be observed when folks manually create a Single/Completable and directly call the
        // onSubscribe method.
        return delegate instanceof ContextPreservingCancellable &&
                ((ContextPreservingCancellable) delegate).saved == current ? delegate :
                new ContextPreservingCancellable(delegate, current);
    }

    @Override
    public void cancel() {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            delegate.cancel();
        } finally {
            INSTANCE.contextMap(prev);
        }
    }
}
