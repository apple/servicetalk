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
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.api.Executors.from;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final String APP_EXECUTOR_PREFIX = "app";
    protected static final String SOURCE_EXECUTOR_PREFIX = "source";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offloader";

    static final int APP_THREAD = 0;
    static final int SOURCE_THREAD = 1;
    static final int SUBSCRIBE_THREAD = 2;
    static final int TERMINAL_THREAD = 3;

    // @Rule
    // public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> app = ExecutorRule.withNamePrefix(APP_EXECUTOR_PREFIX);

    public final ExecutorService offloadExecutorService = Executors.newCachedThreadPool(
            new DefaultThreadFactory(OFFLOAD_EXECUTOR_PREFIX));
    @Rule
    public final ExecutorRule<Executor> offloader = ExecutorRule.withExecutor(() ->
            from(offloadExecutorService::execute));

    public final ExecutorService sourceExecutorService = Executors.newCachedThreadPool(
            new DefaultThreadFactory(SOURCE_EXECUTOR_PREFIX));
    @Rule
    public final ExecutorRule<Executor> source = ExecutorRule.withExecutor(() -> from(sourceExecutorService::execute));

    protected AtomicReferenceArray<Thread> setupAndSubscribe(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
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
                    .afterOnSubscribe(cancellable -> subscribed.countDown());

            Completable offloaded = offloadingFunction.apply(original);

            Cancellable cancel = offloaded.afterOnComplete(allDone::countDown)
                    .afterOnSubscribe(cancellable -> recordThread(capturedThreads, SUBSCRIBE_THREAD))
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

    public static TypeSafeMatcher<Thread> matchPrefix(String prefix) {
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
