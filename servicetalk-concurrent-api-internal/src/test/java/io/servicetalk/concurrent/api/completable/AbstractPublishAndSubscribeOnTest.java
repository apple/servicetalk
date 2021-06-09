/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.test.resources.TestUtils.matchThreadNamePrefix;
import static java.lang.Thread.currentThread;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final String APP_EXECUTOR_PREFIX = "app";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offload";

    static final int APP_THREAD = 0;
    static final int ON_SUBSCRIBE_THREAD = 1;
    static final int TERMINAL_THREAD = 2;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> app = ExecutorRule.withNamePrefix(APP_EXECUTOR_PREFIX);
    protected Matcher<Thread> offloadExecutor = matchThreadNamePrefix(OFFLOAD_EXECUTOR_PREFIX);
    Matcher<Thread> appExecutor = matchThreadNamePrefix(APP_EXECUTOR_PREFIX);
    AtomicInteger offloadsStarted = new AtomicInteger();
    AtomicInteger offloadsFinished = new AtomicInteger();
    private volatile Runnable afterOffload;
    private final ThreadPoolExecutor offloadExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            new DefaultThreadFactory(OFFLOAD_EXECUTOR_PREFIX)) {

        @Override
        protected void beforeExecute(final Thread t, final Runnable r) {
            super.beforeExecute(t, r);
            offloadsStarted.getAndIncrement();
        }

        @Override
        protected void afterExecute(final Runnable r, final Throwable t) {
            offloadsFinished.getAndIncrement();
            super.afterExecute(r, t);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
            Runnable after = () -> {
                try {
                    runnable.run();
                } finally {
                    Runnable executeAfterOffload = afterOffload;
                    if (null != executeAfterOffload) {
                        executeAfterOffload.run();
                    }
                }
            };
            return super.newTaskFor(after, value);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
            Callable<T> after = () -> {
                try {
                    return callable.call();
                } finally {
                    Runnable executeAfterOffload = afterOffload;
                    if (null != executeAfterOffload) {
                        executeAfterOffload.run();
                    }
                }
            };
            return super.newTaskFor(after);
        }
    };
    @Rule
    public final ExecutorRule<Executor> offload = ExecutorRule.withExecutor(() -> from(offloadExecutorService));

    protected AtomicReferenceArray<Thread> setupAndSubscribe(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        return setupAndSubscribe(-1, offloadingFunction);
    }

    protected AtomicReferenceArray<Thread> setupAndSubscribe(int offloadsExpected,
                                                             Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(3);
        TestCompletable original = new TestCompletable.Builder().singleSubscriber().build();
        Completable offloaded = offloadingFunction.apply(original);
        CountDownLatch subscribed = new CountDownLatch(1 + (offloadsExpected > 0 ? (offloadsExpected - 1) : 0));
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            recordThread(capturedThreads, APP_THREAD);

            afterOffload = subscribed::countDown;

            Cancellable cancel = offloaded.afterOnComplete(allDone::countDown)
                    .afterOnSubscribe(cancellable -> {
                        recordThread(capturedThreads, ON_SUBSCRIBE_THREAD);
                    })
                    .afterFinally(() -> recordThread(capturedThreads, TERMINAL_THREAD))
                    .subscribe();
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            original.onComplete();
            try {
                // Waiting for "afterOnComplete" to fire
                allDone.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            if (cancel.hashCode() != cancel.hashCode()) {
                throw new RuntimeException("impossible, but keeps cancel alive.");
            }
        });
        // Waiting for "afterOnComplete" to fire
        allDone.await();
        offloadExecutorService.shutdown();
        boolean shutdown = offloadExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat("Executor still active " + offloadExecutorService, shutdown, is(true));

        assertThat("Started offloads != finished", offloadsFinished.intValue(), is(offloadsStarted.intValue()));
        if (-1 != offloadsExpected) {
            assertThat("Unexpected offloads", offloadsFinished.intValue(), is(offloadsExpected));
        }

        return verifyCapturedThreads(capturedThreads);
    }

    protected AtomicReferenceArray<Thread> setupAndCancel(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        return setupAndCancel(-1, offloadingFunction);
    }

    protected AtomicReferenceArray<Thread> setupAndCancel(int offloadsExpected,
                                                          Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(3);
        CountDownLatch allDone = new CountDownLatch(1);
        TestCompletable original = new TestCompletable.Builder().singleSubscriber().build();
        Completable offloaded = offloadingFunction.apply(original);
        CountDownLatch subscribed = new CountDownLatch(original == offloaded ? 1 : 2);

        app.executor().execute(() -> {
            recordThread(capturedThreads, APP_THREAD);

            afterOffload = subscribed::countDown;

            Cancellable cancel = offloaded.afterOnSubscribe(cancellable -> {
                recordThread(capturedThreads, ON_SUBSCRIBE_THREAD);
                subscribed.countDown();
            })
                    .afterCancel(() -> {
                        recordThread(capturedThreads, TERMINAL_THREAD);
                        allDone.countDown();
                    }).subscribe();
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            cancel.cancel();
            try {
                // Waiting for "afterOnComplete" to fire
                allDone.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            if (cancel.hashCode() != cancel.hashCode()) {
                throw new RuntimeException("impossible, but keeps cancel alive.");
            }
        });
        // Waiting for "afterOnComplete" to fire
        allDone.await();
        offloadExecutorService.shutdown();
        boolean shutdown = offloadExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat("Executor still active " + offloadExecutorService, shutdown, is(true));

        assertThat("Started offloads != finished", offloadsFinished.intValue(), is(offloadsStarted.intValue()));
        if (-1 != offloadsExpected) {
            assertThat("Unexpected offloads", offloadsFinished.intValue(), is(offloadsExpected));
        }

        return verifyCapturedThreads(capturedThreads);
    }

    private static void recordThread(AtomicReferenceArray<Thread> threads, final int index) {
        Thread was = threads.getAndSet(index, currentThread());
        assertThat("Thread already recorded at index: " + index, was, nullValue());
    }

    public static AtomicReferenceArray<Thread> verifyCapturedThreads(AtomicReferenceArray<Thread> capturedThreads) {
        for (int i = 0; i < capturedThreads.length(); i++) {
            final Thread capturedThread = capturedThreads.get(i);
            assertThat("No captured thread at index: " + i, capturedThread, notNullValue());
        }

        assertThat("Unexpected executor for app", capturedThreads.get(APP_THREAD),
                matchThreadNamePrefix(APP_EXECUTOR_PREFIX));

        return capturedThreads;
    }

    public static String capturedThreadsToString(AtomicReferenceArray<Thread> capturedThreads) {
        return IntStream.range(0, capturedThreads.length())
                .mapToObj(capturedThreads::get)
                .map(Thread::getName)
                .collect(Collectors.joining(", ", "[ ", " ]"));
    }
}
