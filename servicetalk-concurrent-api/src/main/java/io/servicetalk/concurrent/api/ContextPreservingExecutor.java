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

import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

final class ContextPreservingExecutor implements Executor {
    private final Executor delegate;

    private ContextPreservingExecutor(Executor delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(new ContextPreservingRunnable(command));
    }

    @Override
    public String toString() {
        return ContextPreservingExecutor.class.getSimpleName() + "{delegate=" + delegate + "}";
    }

    static ContextPreservingExecutor of(Executor executor) {
        return executor instanceof ContextPreservingExecutor ? (ContextPreservingExecutor) executor :
                new ContextPreservingExecutor(executor);
    }
}
