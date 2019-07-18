/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingRunnable implements Runnable {
    private final AsyncContextMap saved;
    private final Runnable delegate;

    ContextPreservingRunnable(Runnable delegate) {
        this(delegate, INSTANCE.contextMap());
    }

    ContextPreservingRunnable(Runnable delegate, AsyncContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                delegate.run();
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
            delegate.run();
        } finally {
            contextThreadLocal.set(prev);
        }
    }
}
