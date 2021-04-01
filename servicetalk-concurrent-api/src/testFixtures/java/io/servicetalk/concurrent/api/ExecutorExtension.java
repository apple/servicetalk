/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static java.lang.Thread.NORM_PRIORITY;

/**
 * An {@link Extension} wrapper for an {@link Executor}.
 * @param <E> The type of {@link Executor}.
 */
public final class ExecutorExtension<E extends Executor> implements BeforeEachCallback, AfterEachCallback {

    private final Supplier<E> eSupplier;
    @Nullable
    private E executor;

    private ExecutorExtension(final Supplier<E> eSupplier) {
        this.eSupplier = eSupplier;
    }

    /**
     * Create an {@link ExecutorExtension} with a default executor.
     *
     * @return a new {@link ExecutorExtension}.
     */
    public static ExecutorExtension<Executor> withCachedExecutor() {
        return new ExecutorExtension<>(Executors::newCachedThreadExecutor);
    }

    /**
     * Create an {@link ExecutorExtension} with a {@link TestExecutor}.
     * <p>
     * {@link #executor()} will return the {@link TestExecutor} to allow controlling the executor in tests.
     *
     * @return a new {@link ExecutorExtension}.
     */
    public static ExecutorExtension<TestExecutor> withTestExecutor() {
        return new ExecutorExtension<>(TestExecutor::new);
    }

    /**
     * Create an {@link ExecutorExtension} with the specified {@code executor}.
     *
     * @param executorSupplier The {@link Executor} {@link Supplier} to use.
     * @return a new {@link ExecutorExtension}.
     */
    public static ExecutorExtension<Executor> withExecutor(Supplier<Executor> executorSupplier) {
        return new ExecutorExtension<>(executorSupplier);
    }

    /**
     * Create an {@link ExecutorExtension} with a default executor, configured to prefix thread names
     * with {@code namePrefix}.
     *
     * @param namePrefix the name to prefix thread names with.
     * @return a new {@link ExecutorExtension}.
     */
    public static ExecutorExtension<Executor> withCachedExecutor(String namePrefix) {
        return new ExecutorExtension<>(() ->
                newCachedThreadExecutor(new DefaultThreadFactory(namePrefix, true, NORM_PRIORITY)));
    }

    /**
     * Returns {@link Executor} created on the last call to {@link #beforeEach(ExtensionContext)} ()}.
     *
     * @return {@link Executor} created on the last call to {@link #beforeEach(ExtensionContext)} ()}.
     * {@code null} if {@link #beforeEach(ExtensionContext)} ()} has
     * not been called yet.
     */
    public E executor() {
        assert executor != null : "Executor was not initialized";
        return executor;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        executor = eSupplier.get();
    }

    @Override
    public void afterEach(ExtensionContext context) throws ExecutionException, InterruptedException {
        if (executor == null) {
            return;
        }

        executor.closeAsync().toFuture().get();
    }
}
