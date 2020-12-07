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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

public class SingleProcessorTest {

    @Test
    public void testSuccessBeforeListen() {
        testSuccessBeforeListen("foo");
    }

    @Test
    public void testSuccessThrowableBeforeListen() {
        testSuccessBeforeListen(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessNullBeforeListen() {
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
    public void testErrorBeforeListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSuccessAfterListen() {
        testSuccessAfterListen("foo");
    }

    @Test
    public void testSuccessNullAfterListen() {
        testSuccessAfterListen(null);
    }

    @Test
    public void testSuccessThrowableAfterListen() {
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
    public void testErrorAfterListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSuccessThenError() {
        testSuccessThenError("foo");
    }

    @Test
    public void testSuccessThrowableThenError() {
        testSuccessThenError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessNullThenError() {
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
    public void testErrorThenSuccess() {
        testErrorThenSuccess("foo");
    }

    @Test
    public void testErrorThenSuccessThrowable() {
        testErrorThenSuccess(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorThenSuccessNull() {
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
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified("foo");
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithNull() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(null);
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithThrowable() {
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
    public void synchronousCancelStillAllowsForGC() throws InterruptedException {
        SingleProcessor<Integer> processor = new SingleProcessor<>();
        ReferenceQueue<SingleSource.Subscriber<Integer>> queue = new ReferenceQueue<>();
        WeakReference<SingleSource.Subscriber<Integer>> subscriberRef =
                synchronousCancelStillAllowsForGCDoSubscribe(processor, queue);
        System.gc();
        Thread.sleep(300);
        assertEquals(subscriberRef, queue.remove(100));
    }

    private WeakReference<SingleSource.Subscriber<Integer>> synchronousCancelStillAllowsForGCDoSubscribe(
            SingleProcessor<Integer> processor, ReferenceQueue<SingleSource.Subscriber<Integer>> queue) {
        SingleSource.Subscriber<Integer> subscriber = new SingleSource.Subscriber<Integer>() {
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
}
