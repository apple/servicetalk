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
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static java.lang.Thread.NORM_PRIORITY;

/**
 * An {@link ExternalResource} wrapper for an {@link Executor}.
 * @param <E> The type of {@link Executor}.
 */
public final class ExecutorRule<E extends Executor> extends ExternalResource {

    private final Supplier<E> eSupplier;
    private E executor;

    private ExecutorRule(final Supplier<E> eSupplier) {
        this.eSupplier = eSupplier;
    }

    /**
     * Create an {@link ExecutorRule} with a default executor.
     *
     * @return a new {@link ExecutorRule}.
     */
    public static ExecutorRule<Executor> newRule() {
        return new ExecutorRule<>(Executors::newCachedThreadExecutor);
    }

    /**
     * Create an {@link ExecutorRule} with a {@link TestExecutor}.
     * <p>
     * {@link #executor()} will return the {@link TestExecutor} to allow controlling the executor in tests.
     *
     * @return a new {@link ExecutorRule}.
     */
    public static ExecutorRule<TestExecutor> withTestExecutor() {
        return new ExecutorRule<>(TestExecutor::new);
    }

    /**
     * Create an {@link ExecutorRule} with the specified {@code executor}.
     *
     * @param executorSupplier The {@link Executor} {@link Supplier} to use.
     * @return a new {@link ExecutorRule}.
     */
    public static ExecutorRule<Executor> withExecutor(Supplier<Executor> executorSupplier) {
        return new ExecutorRule<>(executorSupplier);
    }

    /**
     * Create an {@link ExecutorRule} with a default executor, configured to prefix thread names
     * with {@code namePrefix}.
     *
     * @param namePrefix the name to prefix thread names with.
     * @return a new {@link ExecutorRule}.
     */
    public static ExecutorRule<Executor> withNamePrefix(String namePrefix) {
        return new ExecutorRule<>(() ->
                newCachedThreadExecutor(new DefaultThreadFactory(namePrefix, true, NORM_PRIORITY)));
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
        executor = eSupplier.get();
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
