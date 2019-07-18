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

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
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
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                delegate.cancel();
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            slowPath();
        }
    }

    private void slowPath() {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            delegate.cancel();
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public String toString() {
        return ContextPreservingCancellable.class.getSimpleName() + "(" + delegate + ')';
    }
}
