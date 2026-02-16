/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.NonBlockingThread.IllegalBlockingOperationException;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.NonBlockingThreadFactory;
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Single.fromFuture;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;

class FutureToSingleTest extends AbstractFutureToSingleTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> NON_BLOCKING_EXEC = ExecutorExtension
            .withExecutor(() -> Executors.newFixedSizeExecutor(1, new NonBlockingThreadFactory()))
            .setClassLevel(true);

    @Override
    Single<String> from(final CompletableFuture<String> future) {
        return fromFuture(future);
    }

    @Test
    void failure() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        EXEC.executor().execute(() -> future.completeExceptionally(DELIBERATE_EXCEPTION));
        try {
            single.toFuture().get();
            fail("Single expected to fail.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat("Unexpected cause.", cause, instanceOf(ExecutionException.class));
            Throwable nestedCause = cause.getCause();
            assertThat("Unexpected nested cause.", nestedCause, is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    void throwsIfHasToBlockOnNonBlockingThread() throws Exception {
        BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        single.subscribeOn(NON_BLOCKING_EXEC.executor()).subscribe(__ -> { }, errors::add);
        Throwable t = errors.take();
        assertThat(t, is(instanceOf(IllegalBlockingOperationException.class)));
        assertThat(t.getMessage(), startsWith("Not allowed to block a NonBlockingThread"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] terminal={0}")
    @EnumSource(FutureTerminal.class)
    void doesNotThrowOnNonBlockingThreadIfAlreadyComplete(FutureTerminal terminal) throws Exception {
        BlockingQueue<Object> result = new LinkedBlockingQueue<>();
        CompletableFuture<String> future = new CompletableFuture<>();
        switch (terminal) {
            case COMPLETED:
                future.complete("result");
                break;
            case FAILED:
                future.completeExceptionally(DELIBERATE_EXCEPTION);
                break;
            case CANCELLED:
                future.cancel(true);
                break;
            default:
                throw new IllegalArgumentException("Unknown FutureTerminal: " + terminal);
        }
        Single<String> single = from(future);
        single.subscribeOn(NON_BLOCKING_EXEC.executor()).subscribe(result::add, result::add);
        switch (terminal) {
            case COMPLETED:
                assertThat(result.take(), is(equalTo("result")));
                break;
            case FAILED:
                final Object error = result.take();
                assertThat(error, is(instanceOf(ExecutionException.class)));
                assertThat(((ExecutionException) error).getCause(), is(sameInstance(DELIBERATE_EXCEPTION)));
                break;
            case CANCELLED:
                assertThat(result.take(), is(instanceOf(CancellationException.class)));
                break;
            default:
                throw new IllegalArgumentException("Unknown FutureTerminal: " + terminal);
        }
        assertThat(result, is(empty()));
    }

    private enum FutureTerminal {
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
