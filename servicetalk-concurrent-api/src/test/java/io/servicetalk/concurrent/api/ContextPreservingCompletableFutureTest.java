/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.NonBlockingThread;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class ContextPreservingCompletableFutureTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> NON_BLOCKING_EXEC = ExecutorExtension
            .withExecutor(() -> Executors.newFixedSizeExecutor(1, new NonBlockingThreadFactory()))
            .setClassLevel(true);

    private static List<Arguments> operationWithBoolean() {
        List<Arguments> result = new ArrayList<>();
        for (Operation operation : Operation.values()) {
            for (boolean b : asList(false, true)) {
                result.add(Arguments.of(operation, b));
            }
        }
        return result;
    }

    @ParameterizedTest(name = "{displayName} [{index}] operation={0} withWhenComplete={1}")
    @MethodSource("operationWithBoolean")
    void testThrowsIfHasToBlockOnNonBlockingThread(Operation operation, boolean withWhenComplete) throws Exception {
        BlockingQueue<Object> result = new LinkedBlockingQueue<>();
        AsyncContextProvider provider = DefaultAsyncContextProvider.INSTANCE;
        CompletableFuture<String> f = provider.wrapCompletableFuture(
                new CompletableFuture<>(), provider.captureContext());
        if (withWhenComplete) {
            f = f.whenComplete((r, t) -> { /* noop */ });
        }
        CompletableFuture<String> future = f;
        assertThat(future.isDone(), is(false));
        NON_BLOCKING_EXEC.executor().submit(() -> {
                    switch (operation) {
                        case GET:
                            return future.get();
                        case GET_WITH_TIMEOUT:
                            return future.get(3, SECONDS);
                        case JOIN:
                            return future.join();
                        default:
                            throw new IllegalArgumentException("Unknown Operation: " + operation);
                    }
                })
                .subscribe(result::add, result::add);
        assertThat(result.take(), is(instanceOf(NonBlockingThread.IllegalBlockingOperationException.class)));
        assertThat(future.isDone(), is(false));
    }

    private static List<Arguments> terminalAndOperationWithBoolean() {
        List<Arguments> result = new ArrayList<>();
        for (FutureTerminal terminal : FutureTerminal.values()) {
            for (Operation operation : Operation.values()) {
                for (boolean b : asList(false, true)) {
                    result.add(Arguments.of(terminal, operation, b));
                }
            }
        }
        return result;
    }

    @ParameterizedTest(name = "{displayName} [{index}] terminal={0} operation={1} withWhenComplete={2}")
    @MethodSource("terminalAndOperationWithBoolean")
    void testDoesNotThrowOnNonBlockingThreadIfAlreadyComplete(FutureTerminal terminal, Operation operation,
                                                              boolean withWhenComplete) throws Exception {
        BlockingQueue<Object> result = new LinkedBlockingQueue<>();
        CompletableFuture<String> f = new CompletableFuture<>();
        if (withWhenComplete) {
            f = f.whenComplete((r, t) -> { /* noop */ });
        }
        CompletableFuture<String> future = f;
        switch (terminal) {
            case COMPLETED:
                f.complete("foo");
                break;
            case FAILED:
                f.completeExceptionally(DELIBERATE_EXCEPTION);
                break;
            case CANCELLED:
                future.cancel(true);
                break;
            default:
                throw new IllegalArgumentException("Unknown FutureTerminal: " + terminal);
        }
        assertThat(future.isDone(), is(true));
        NON_BLOCKING_EXEC.executor().submit(() -> {
                    switch (operation) {
                        case GET:
                            return future.get();
                        case GET_WITH_TIMEOUT:
                            return future.get(3, SECONDS);
                        case JOIN:
                            return future.join();
                        default:
                            throw new IllegalArgumentException("Unknown Operation: " + operation);
                    }
                })
                .subscribe(result::add, result::add);
        switch (terminal) {
            case COMPLETED:
                assertThat(result.take(), is("foo"));
                break;
            case FAILED:
                final Object error = result.take();
                if (operation == Operation.JOIN) {
                    assertThat(error, is(instanceOf(CompletionException.class)));
                } else {
                    assertThat(error, is(instanceOf(ExecutionException.class)));
                }
                assertThat(((Throwable) error).getCause(), is(sameInstance(DELIBERATE_EXCEPTION)));
                break;
            case CANCELLED:
                assertThat(result.take(), is(instanceOf(CancellationException.class)));
                break;
            default:
                throw new IllegalArgumentException("Unknown FutureTerminal: " + terminal);
        }
        assertThat(result, is(empty()));
    }

    private enum Operation {
        GET,
        GET_WITH_TIMEOUT,
        JOIN
    }

    private enum FutureTerminal {
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
