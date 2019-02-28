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

import org.junit.rules.ExternalResource;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static java.lang.Thread.NORM_PRIORITY;

/**
 * An {@link ExternalResource} wrapper for an {@link Executor}.
 */
public final class ExecutorRule<E extends Executor> extends ExternalResource {

    private final E executor;

    private ExecutorRule(final E executor) {
        this.executor = executor;
    }

    public static ExecutorRule<Executor> newRule() {
        return withExecutor(Executors.newCachedThreadExecutor());
    }

    public static ExecutorRule<TestExecutor> withTestExecutor() {
        return new ExecutorRule<>(new TestExecutor());
    }

    public static ExecutorRule<Executor> withExecutor(Executor executor) {
        return new ExecutorRule<>(executor);
    }

    public static ExecutorRule<Executor> withNamePrefix(String namePrefix) {
        return new ExecutorRule<>(newCachedThreadExecutor(new DefaultThreadFactory(namePrefix, true, NORM_PRIORITY)));
    }

    /**
     * Returns {@link Executor} created on the last call to {@link #before()}.
     *
     * @return {@link Executor} created on the last call to {@link #before()}. {@code null} if {@link #before()} has
     * not been called yet.
     */
    public E executor() {
        return executor;
    }

    @Override
    protected void before() {
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
