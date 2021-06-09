/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

/**
 * Provides access to variations of {@link AsyncCloseable#closeAsync()} and
 * {@link AsyncCloseable#closeAsyncGracefully()} that omit any offloading. As the returned {@code Completable} is not
 * required to be blocking-safe it should be offloaded if the
 * {@link io.servicetalk.concurrent.CompletableSource.Subscriber} may block.
 */
public interface PrivilegedListenableAsyncCloseable extends ListenableAsyncCloseable {

    /**
     * Returns a {@link Completable} that is notified once the {@link ListenableAsyncCloseable} has closed.
     * @return the {@code Completable} that is notified on close. The {@code Completable} is not required to be
     * blocking-safe and should be offloaded if the {@link io.servicetalk.concurrent.CompletableSource.Subscriber}
     * may block.
     */
    Completable closeAsyncNoOffload();

    /**
     * Returns a {@link Completable} that is notified once the {@link ListenableAsyncCloseable} has closed.
     * @return the {@code Completable} that is notified on close. The {@code Completable} is not required to be
     * blocking-safe and should be offloaded if the {@link io.servicetalk.concurrent.CompletableSource.Subscriber}
     * may block.
     *
     * @see AsyncCloseable#closeAsyncGracefully
     */
    default Completable closeAsyncGracefullyNoOffload() {
        return closeAsyncNoOffload();
    }
}
