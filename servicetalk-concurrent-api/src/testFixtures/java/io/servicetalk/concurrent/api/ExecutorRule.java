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

import org.junit.rules.ExternalResource;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * An {@link ExternalResource} wrapper for an {@link Executor}.
 */
public final class ExecutorRule extends ExternalResource {

    private Executor executor;
    private final Supplier<Executor> executorFactory;

    /**
     * New instance. Uses a default {@link Executor}.
     */
    public ExecutorRule() {
        this(Executors::newCachedThreadExecutor);
    }

    /**
     * New instance.
     * @param executorFactory {@link Supplier} called to create a new {@link Executor} every time {@link #before()} is
     * invoked.
     */
    public ExecutorRule(final Supplier<Executor> executorFactory) {
        this.executorFactory = executorFactory;
    }

    /**
     * Returns {@link Executor} created on the last call to {@link #before()}.
     *
     * @return {@link Executor} created on the last call to {@link #before()}. {@code null} if {@link #before()} has not
     * been called yet.
     */
    public Executor executor() {
        return executor;
    }

    @Override
    protected void before() {
        executor = executorFactory.get();
    }

    @Override
    protected void after() {
        try {
            executor.closeAsync().toFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
