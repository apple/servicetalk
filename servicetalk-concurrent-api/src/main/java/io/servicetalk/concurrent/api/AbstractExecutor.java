/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;

abstract class AbstractExecutor implements Executor {
    private final ListenableAsyncCloseable listenableAsyncCloseable = toListenableAsyncCloseable(
            toAsyncCloseable(graceful -> defer(() -> {
                // If closeAsync() is subscribed multiple times, we will call this method as many times.
                // Since doClose() is idempotent and usually cheap, it is OK as compared to implementing at most
                // once semantics.
                doClose();
                return completed().shareContextOnSubscribe();
            })));

    @Override
    public Completable onClose() {
        return listenableAsyncCloseable.onClose();
    }

    @Override
    public Completable onClosing() {
        return listenableAsyncCloseable.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return listenableAsyncCloseable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return listenableAsyncCloseable.closeAsyncGracefully();
    }

    /**
     * Do any close actions required for this {@link Executor}.
     * This method MUST be idempotent.
     */
    abstract void doClose();
}
