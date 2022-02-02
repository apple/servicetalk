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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SingleProcessorTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor().setClassLevel(true);
    @Test
    void testSuccessBeforeListen() {
        testSuccessBeforeListen("foo");
    }

    @Test
    void testSuccessThrowableBeforeListen() {
        testSuccessBeforeListen(DELIBERATE_EXCEPTION);
    }

    @Test
    void testSuccessNullBeforeListen() {
        testSuccessBeforeListen(null);
    }

    private <T> void testSuccessBeforeListen(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.awaitOnSuccess(), is(expected));
    }

    @Test
    void testErrorBeforeListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSuccessAfterListen() {
        testSuccessAfterListen("foo");
    }

    @Test
    void testSuccessNullAfterListen() {
        testSuccessAfterListen(null);
    }

    @Test
    void testSuccessThrowableAfterListen() {
        testSuccessAfterListen(DELIBERATE_EXCEPTION);
    }

    private <T> void testSuccessAfterListen(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onSuccess(expected);
        assertThat(subscriber.awaitOnSuccess(), is(expected));
    }

    @Test
    void testErrorAfterListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSuccessThenError() {
        testSuccessThenError("foo");
    }

    @Test
    void testSuccessThrowableThenError() {
        testSuccessThenError(DELIBERATE_EXCEPTION);
    }

    @Test
    void testSuccessNullThenError() {
        testSuccessThenError(null);
    }

    private <T> void testSuccessThenError(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        processor.onError(DELIBERATE_EXCEPTION);
        TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.awaitOnSuccess(), is(expected));
    }

    @Test
    void testErrorThenSuccess() {
        testErrorThenSuccess("foo");
    }

    @Test
    void testErrorThenSuccessThrowable() {
        testErrorThenSuccess(DELIBERATE_EXCEPTION);
    }

    @Test
    void testErrorThenSuccessNull() {
        testErrorThenSuccess(null);
    }

    private <T> void testErrorThenSuccess(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onSuccess(expected);
        TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified("foo");
    }

    @Test
    void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithNull() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(null);
    }

    @Test
    void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithThrowable() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(DELIBERATE_EXCEPTION);
    }

    private <T> void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        TestSingleSubscriber<T> subscriber = new TestSingleSubscriber<>();
        TestSingleSubscriber<T> subscriber2 = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.subscribe(subscriber2);
        assertThat(subscriber2.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        processor.onSuccess(expected);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber2.awaitOnSuccess(), is(expected));
    }

    @Test
    void synchronousCancelStillAllowsForGC() throws InterruptedException {
        SingleProcessor<Integer> processor = new SingleProcessor<>();
        ReferenceQueue<Subscriber<Integer>> queue = new ReferenceQueue<>();
        WeakReference<Subscriber<Integer>> subscriberRef =
                synchronousCancelStillAllowsForGCDoSubscribe(processor, queue);
        System.gc();
        Thread.sleep(300);
        assertEquals(subscriberRef, queue.remove(100));
    }

    private WeakReference<Subscriber<Integer>> synchronousCancelStillAllowsForGCDoSubscribe(
            SingleProcessor<Integer> processor, ReferenceQueue<Subscriber<Integer>> queue) {
        Subscriber<Integer> subscriber = new Subscriber<Integer>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                cancellable.cancel();
            }

            @Override
            public void onSuccess(@Nullable final Integer result) {
            }

            @Override
            public void onError(final Throwable t) {
            }
        };
        processor.subscribe(subscriber);
        return new WeakReference<>(subscriber, queue);
    }

    @Test
    void multiThreadedAddAlwaysTerminatesError() throws Exception {
        multiThreadedAddAlwaysTerminates(null, DELIBERATE_EXCEPTION);
    }

    @Test
    void multiThreadedAddAlwaysTerminatesSuccess() throws Exception {
        multiThreadedAddAlwaysTerminates("foo", null);
    }

    @Test
    void multiThreadedAddAlwaysTerminatesSuccessNull() throws Exception {
        multiThreadedAddAlwaysTerminates(null, null);
    }

    private static void multiThreadedAddAlwaysTerminates(@Nullable String value, @Nullable Throwable cause)
            throws Exception {
        final int subscriberCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(subscriberCount + 1);
        List<Single<Subscriber<String>>> subscriberSingles = new ArrayList<>(subscriberCount);
        SingleProcessor<String> processor = new SingleProcessor<>();
        for (int i = 0; i < subscriberCount; ++i) {
            subscriberSingles.add(EXECUTOR_RULE.executor().submit(() -> {
                @SuppressWarnings("unchecked")
                Subscriber<String> subscriber = mock(Subscriber.class);
                barrier.await();
                processor.subscribe(subscriber);
                return subscriber;
            }));
        }

        Future<Collection<Subscriber<String>>> future = collectUnordered(subscriberSingles, subscriberCount).toFuture();
        barrier.await();
        if (cause != null) {
            processor.onError(cause);

            Collection<Subscriber<String>> subscribers = future.get();
            for (Subscriber<String> s : subscribers) {
                verify(s).onError(cause);
            }
        } else {
            processor.onSuccess(value);

            Collection<Subscriber<String>> subscribers = future.get();
            for (Subscriber<String> s : subscribers) {
                verify(s).onSuccess(value);
            }
        }
    }
}
