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
import io.servicetalk.context.api.ContextMapHolder;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.CONTEXT_THREAD_LOCAL;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCancellable implements Cancellable {
    private final ContextMap saved;
    private final Cancellable delegate;

    private final Throwable creator = new Throwable("Cancellable creator");

    private ContextPreservingCancellable(Cancellable delegate, ContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    static Cancellable wrap(Cancellable delegate, ContextMap current) {
        // The double wrapping can be observed when folks manually create a Single/Completable and directly call the
        // onSubscribe method.
        return delegate instanceof ContextPreservingCancellable &&
                ((ContextPreservingCancellable) delegate).saved == current ? delegate :
                new ContextPreservingCancellable(delegate, current);
    }

    @Override
    public void cancel() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                delegate.cancel();
            } catch (Throwable all) {
                all.addSuppressed(creator);
                throw all;
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            slowPath();
        }
    }

    private void slowPath() {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            delegate.cancel();
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + delegate + ')';
    }
}
