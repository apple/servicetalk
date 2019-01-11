/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.NoopOffloader.NOOP_OFFLOADER;

final class ImmediateExecutor extends AbstractOffloaderAwareExecutor {

    private static final Executor IMMEDIATE = from(Runnable::run);
    static final Executor IMMEDIATE_EXECUTOR = new ImmediateExecutor();

    private ImmediateExecutor() {
        // No instances
    }

    @Override
    public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
        return NOOP_OFFLOADER;
    }

    @Override
    public boolean threadAffinity() {
        return false;
    }

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        task.run();
        return IGNORE_CANCEL;
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        return IMMEDIATE.schedule(task, delay, unit);
    }

    @Override
    void doClose() {
        // Noop
    }
}
