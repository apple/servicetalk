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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static java.lang.Thread.NORM_PRIORITY;

/**
 * An {@link Extension} wrapper that creates and disposes an {@link Executor} for your test case or suite.
 * <p>
 * Can be used with a {@link RegisterExtension} field. If used with a class static field then use
 * {@link #setClassLevel(boolean) setClassLevel(true)} to create contained {@link Executor} instance only once for
 * all tests in class.
 *
 * @param <E> The type of {@link Executor}.
 */
public final class ExecutorExtension<E extends Executor> implements AfterEachCallback, BeforeEachCallback,
                                                                    AfterAllCallback, BeforeAllCallback {

    private final Supplier<E> eSupplier;
    private boolean classLevel;
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
     * <p>
     * The {@link TestExecutor} is designed to be used for a single test and should not be shared. Specifically, it is
     * generally incompatible with {@link #setClassLevel(boolean) setClassLevel(true)}.
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
    public static <E extends Executor> ExecutorExtension<E> withExecutor(Supplier<E> executorSupplier) {
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

    /**
     * Set to true if the extension is being shared among all tests in a class.
     *
     * @param classLevel true if extension is shared between tests within test class otherwise false to create a new
     * instance for each test.
     * @return this
     */
    public ExecutorExtension<E> setClassLevel(final boolean classLevel) {
        this.classLevel = classLevel;
        return this;
    }

    @Override
    public void afterEach(ExtensionContext context) throws ExecutionException, InterruptedException {
        if (!classLevel) {
            closeExecutor();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!classLevel) {
            createExecutor();
        }
    }

    private void createExecutor() {
        executor = Objects.requireNonNull(eSupplier.get());
    }

    private void closeExecutor() throws ExecutionException, InterruptedException {
        if (executor == null) {
            return;
        }

        executor.closeAsync().toFuture().get();
        executor = null;
    }

    @Override
    public void afterAll(final ExtensionContext context) throws ExecutionException, InterruptedException {
        if (classLevel) {
            closeExecutor();
        }
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (classLevel) {
            createExecutor();
        }
    }
}
