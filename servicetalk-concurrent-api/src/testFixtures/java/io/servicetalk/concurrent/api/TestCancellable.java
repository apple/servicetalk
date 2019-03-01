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

/**
 * A {@link Cancellable} that tracks cancellation.
 */
public final class TestCancellable implements Cancellable {

    private volatile boolean cancelled;

    @Override
    public void cancel() {
        cancelled = true;
    }

    /**
     * Returns {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     *
     * @return {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
