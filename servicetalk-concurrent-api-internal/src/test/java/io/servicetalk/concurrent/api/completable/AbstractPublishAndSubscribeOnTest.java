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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import static java.lang.Thread.currentThread;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final String APP_EXECUTOR_PREFIX = "app";
    protected static final String SOURCE_EXECUTOR_PREFIX = "source";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offload";

    static final int APP_THREAD = 0;
    static final int SOURCE_THREAD = 1;
    static final int SUBSCRIBE_THREAD = 2;
    static final int TERMINAL_THREAD = 3;

    // @Rule
    // public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> app = ExecutorRule.withNamePrefix(APP_EXECUTOR_PREFIX);

    private volatile Runnable afterOffload;

    private final ThreadPoolExecutor offloadExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>(),
                                      new DefaultThreadFactory(OFFLOAD_EXECUTOR_PREFIX)) {
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

    private final ExecutorService sourceExecutorService = Executors.newSingleThreadExecutor(
            new DefaultThreadFactory(SOURCE_EXECUTOR_PREFIX));
    @Rule
    public final ExecutorRule<Executor> source = ExecutorRule.withExecutor(() -> from(sourceExecutorService));

    protected AtomicReferenceArray<Thread> setupAndSubscribe(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        return setupAndSubscribe(-1, offloadingFunction);
    }

    protected AtomicReferenceArray<Thread> setupAndSubscribe(int offloadsExpected,
                                                             Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        CountDownLatch subscribed = new CountDownLatch(offloadsExpected > 0 ? 3 : 2);
        CountDownLatch allDone = new CountDownLatch(1);
        AtomicInteger offloads = new AtomicInteger();
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(4);

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                // don't emit until AFTER subscribe is complete.
                subscribed.await();
                // Emit the result
                while (offloadExecutorService.getActiveCount() > 0) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
                recordThread(capturedThreads, SOURCE_THREAD);
            } catch (InterruptedException woken) {
                Thread.interrupted();
                CancellationException cancel = new CancellationException("Source cancelled due to interrupt");
                cancel.initCause(woken);
                throw cancel;
            }

        }, sourceExecutorService);

        app.executor().execute(() -> {
            recordThread(capturedThreads, APP_THREAD);
            Completable original = Completable.fromStage(task);

            Completable offloaded = offloadingFunction.apply(original);

            afterOffload = () -> {
                if (0 == offloads.getAndIncrement()) {
                    subscribed.countDown();
                }
            };

            Cancellable cancel = offloaded.afterOnComplete(allDone::countDown)
                    .afterOnSubscribe(cancellable -> {
                        recordThread(capturedThreads, SUBSCRIBE_THREAD);
                        subscribed.countDown();
                    })
                    .afterFinally(() -> recordThread(capturedThreads, TERMINAL_THREAD))
                    .subscribe(() -> System.out.println("done!"));
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
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

        if (-1 != offloadsExpected) {
            assertThat("Unexpected offloads", offloads.intValue(), is(offloadsExpected));
        }

        return verifyCapturedThreads(capturedThreads);
    }

    protected AtomicReferenceArray<Thread> setupAndCancel(
            Function<Completable, Completable> offloadingFunction) throws InterruptedException {
        CountDownLatch subscribed = new CountDownLatch(2);
        CountDownLatch allDone = new CountDownLatch(1);
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(4);

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            recordThread(capturedThreads, SOURCE_THREAD);
            try {
                // don't emit until AFTER subscribe is complete.
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                CancellationException cancel = new CancellationException("Source cancelled due to interrupt");
                cancel.initCause(woken);
                throw cancel;
            }
            // Emit the result
        }, sourceExecutorService);

        app.executor().execute(() -> {
            recordThread(capturedThreads, APP_THREAD);
            Completable original = Completable.fromStage(task)
                    .afterOnSubscribe(cancellable -> {
                        recordThread(capturedThreads, SUBSCRIBE_THREAD);
                        subscribed.countDown();
                    })
                    .afterCancel(() -> {
                        recordThread(capturedThreads, TERMINAL_THREAD);
                        allDone.countDown();
                    });

            Completable offloaded = offloadingFunction.apply(original);

            Cancellable cancel = offloaded.subscribe();
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

        return verifyCapturedThreads(capturedThreads);
    }

    private static void recordThread(AtomicReferenceArray<Thread> threads, final int index) {
        Thread was = threads.getAndUpdate(index, AbstractPublishAndSubscribeOnTest::updateThread);
        assertThat("Thread already recorded at index: " + index, was, nullValue());
    }

    private static Thread updateThread(Thread current) {
        assertThat(current, nullValue());
        return currentThread();
    }

    public static AtomicReferenceArray<Thread> verifyCapturedThreads(AtomicReferenceArray<Thread> capturedThreads) {
        for (int i = 0; i < capturedThreads.length(); i++) {
            final Thread capturedThread = capturedThreads.get(i);
            assertThat("No captured thread at index: " + i, capturedThread, notNullValue());
        }

        assertThat("Unexpected executor for app", capturedThreads.get(APP_THREAD),
                matchPrefix(APP_EXECUTOR_PREFIX));
        assertThat("Unexpected executor for source", capturedThreads.get(SOURCE_THREAD),
                matchPrefix(SOURCE_EXECUTOR_PREFIX));

        return capturedThreads;
    }

    public static String capturedThreadsToString(AtomicReferenceArray<Thread> capturedThreads) {
        return IntStream.range(0, capturedThreads.length())
                .mapToObj(capturedThreads::get)
                .map(Thread::getName)
                .map(AbstractPublishAndSubscribeOnTest::getNamePrefix)
                .collect(Collectors.joining(", ", "[ ", " ]"));
    }

    public static Matcher<Thread> matchPrefix(String prefix) {
        return new TypeSafeMatcher<Thread>() {
            final String matchPrefix = prefix;

            @Override
            public void describeTo(final Description description) {
                description.appendText("a prefix of ")
                        .appendValue(matchPrefix);
            }

            @Override
            public void describeMismatchSafely(Thread item, Description mismatchDescription) {
                mismatchDescription
                        .appendText("was ")
                        .appendValue(getNamePrefix(item.getName()));
            }

            @Override
            protected boolean matchesSafely(final Thread item) {
                return item.getName().startsWith(matchPrefix);
            }
        };
    }

    private static String getNamePrefix(String name) {
        int firstDash = name.indexOf('-');
        return -1 == firstDash ?
                name :
                name.substring(0, firstDash);
    }
}
