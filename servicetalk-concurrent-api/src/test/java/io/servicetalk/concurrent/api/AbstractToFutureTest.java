/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public abstract class AbstractToFutureTest<T> {

    @RegisterExtension
    protected final ExecutorExtension<Executor> exec = ExecutorExtension.withCachedExecutor();

    protected final Cancellable mockCancellable = Mockito.mock(Cancellable.class);

    protected abstract boolean isSubscribed();

    protected abstract Future<T> toFuture();

    protected abstract void completeSource();

    protected abstract void failSource(Throwable t);

    @Nullable
    protected abstract T expectedResult();

    @Test
    void testSubscribed() {
        assertThat(isSubscribed(), is(false));
        toFuture();
        assertThat(isSubscribed(), is(true));
    }

    @Test
    void testSucceeded() throws Exception {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        completeSource();
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is(expectedResult()));
        assertThat(future.get(0, MILLISECONDS), is(expectedResult()));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testSucceededAfterGet() throws Exception {
        Future<T> future = toFuture();
        exec.executor().schedule(this::completeSource, 10, MILLISECONDS);
        assertThat(future.get(), is(expectedResult()));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testSucceededAfterGetWithTimeout() throws Exception {
        Future<T> future = toFuture();
        exec.executor().schedule(this::completeSource, 10, MILLISECONDS);
        assertThat(future.get(3, SECONDS), is(expectedResult()));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testSucceededAfterGetWithEnoughTimeout() throws Exception {
        Future<T> future = toFuture();

        CountDownLatch latch = new CountDownLatch(1);
        exec.executor().execute(() -> {
            try {
                latch.await();
                completeSource();
            } catch (InterruptedException e) {
                throwException(e);
            }
        });
        try {
            future.get(50, MILLISECONDS);
            fail("Expected TimeoutException");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TimeoutException.class)));
            assertThat(future.isDone(), is(false));
            latch.countDown();
        }
        assertThat(future.get(), is(expectedResult()));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testFailed() throws Exception {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        failSource(DELIBERATE_EXCEPTION);
        assertThat(future.isDone(), is(true));
        try {
            future.get();
            fail("Expected DeliberateException");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        try {
            future.get(100, MILLISECONDS);
            fail("Expected DeliberateException");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testFailedWithNull() {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThrows(NullPointerException.class, () -> failSource(null));
    }

    @Test
    void testFailedAfterGet() throws Exception {
        Future<T> future = toFuture();
        exec.executor().schedule(() -> failSource(DELIBERATE_EXCEPTION), 10, MILLISECONDS);
        try {
            future.get();
            fail("Expected DeliberateException");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testFailedAfterGetWithTimeout() throws Exception {
        Future<T> future = toFuture();
        exec.executor().schedule(() -> failSource(DELIBERATE_EXCEPTION), 10, MILLISECONDS);
        try {
            future.get(3, SECONDS);
            fail("Expected DeliberateException");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testGetTimeoutException() {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThrows(TimeoutException.class, () -> future.get(10, MILLISECONDS));
    }

    @Test
    void testMultipleGets() throws Exception {
        Future<T> future = toFuture();

        CountDownLatch latch = new CountDownLatch(3);
        Completable task = exec.executor().submit(() -> {
            try {
                assertThat(future.get(), is(expectedResult()));
                latch.countDown();
            } catch (Exception e) {
                fail("Unexpected exception while waiting for the result: " + e.getMessage());
            }
        });
        task.subscribe();
        task.subscribe();
        task.subscribe();

        exec.executor().schedule(this::completeSource, 100, MILLISECONDS);
        latch.await();
        assertThat(future.isDone(), is(true));
    }

    @Test
    void testCancelWithMayInterruptIfRunning() {
        testCancel(true, future -> { });
    }

    @Test
    void testCancelWithoutMayInterruptIfRunning() {
        testCancel(false, future -> { });
    }

    @Test
    void testOnSuccessResultIsIgnoredAfterCancel() {
        testCancel(true, future -> {
            completeSource();
            assertThat(future.isCancelled(), is(true));
            assertThat(future.isDone(), is(true));
        });
    }

    @Test
    void testOnErrorResultIsIgnoredAfterCancel() {
        testCancel(true, future -> {
            failSource(DELIBERATE_EXCEPTION);
            assertThat(future.isCancelled(), is(true));
            assertThat(future.isDone(), is(true));
        });
    }

    private void testCancel(boolean mayInterruptIfRunning, Consumer<Future<T>> consumer) {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThat(future.isCancelled(), is(false));
        assertThat(future.cancel(mayInterruptIfRunning), is(true));
        assertThat(future.isCancelled(), is(true));
        assertThat(future.isDone(), is(true));
        consumer.accept(future);
        try {
            future.get();
            fail("Expected CancellationException");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(CancellationException.class)));
        }
        verify(mockCancellable).cancel();
    }

    @Test
    void testCancelIsIgnoredAfterOnSuccess() throws Exception {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThat(future.isCancelled(), is(false));
        completeSource();
        assertThat(future.cancel(true), is(false));
        assertThat(future.isCancelled(), is(false));
        assertThat(future.isDone(), is(true));
        assertThat(future.get(), is(expectedResult()));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testCancelIsIgnoredAfterOnError() throws Exception {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThat(future.isCancelled(), is(false));
        failSource(DELIBERATE_EXCEPTION);
        assertThat(future.cancel(true), is(false));
        assertThat(future.isCancelled(), is(false));
        assertThat(future.isDone(), is(true));
        try {
            future.get();
            fail("Expected DeliberateException");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        verify(mockCancellable, never()).cancel();
    }

    @Test
    void testSubsequentCancelsAreIgnored() {
        Future<T> future = toFuture();
        assertThat(future.isDone(), is(false));
        assertThat(future.isCancelled(), is(false));
        assertThat(future.cancel(true), is(true));
        assertThat(future.isCancelled(), is(true));
        assertThat(future.isDone(), is(true));
        try {
            future.get();
            fail("Expected CancellationException");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(CancellationException.class)));
        }
        assertThat(future.cancel(true), is(false));
        assertThat(future.cancel(false), is(false));
        verify(mockCancellable).cancel();
    }
}
