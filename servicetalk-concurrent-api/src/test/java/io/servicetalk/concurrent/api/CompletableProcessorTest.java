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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

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
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CompletableProcessorTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor().setClassLevel(true);
    private final TestCompletableSubscriber rule = new TestCompletableSubscriber();
    private final TestCompletableSubscriber rule2 = new TestCompletableSubscriber();

    @Test
    void maxSubscriberCountExceeded() {
        CompletableProcessor processor = new CompletableProcessor(1);
        toSource(processor).subscribe(rule);
        toSource(processor).subscribe(rule2);
        assertThat(rule2.awaitOnError(), instanceOf(IllegalStateException.class));
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onComplete();
        rule.awaitOnComplete();
    }

    @Test
    void testCompleteBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        toSource(processor).subscribe(rule);
        rule.awaitOnComplete();
    }

    @Test
    void testErrorBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        toSource(processor).subscribe(rule);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCompleteAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onComplete();
        rule.awaitOnComplete();
    }

    @Test
    void testErrorAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(nullValue()));
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCompleteThenError() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        processor.onError(DELIBERATE_EXCEPTION);
        toSource(processor).subscribe(rule);
        rule.awaitOnComplete();
    }

    @Test
    void testErrorThenComplete() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onComplete();
        toSource(processor).subscribe(rule);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(nullValue()));
        toSource(processor).subscribe(rule2);
        assertThat(rule2.pollTerminal(10, MILLISECONDS), is(nullValue()));
        rule.awaitSubscription().cancel();
        processor.onComplete();
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(nullValue()));
        rule2.awaitOnComplete();
    }

    @Test
    void synchronousCancelStillAllowsForGC() throws InterruptedException {
        CompletableProcessor processor = new CompletableProcessor();
        ReferenceQueue<Subscriber> queue = new ReferenceQueue<>();
        WeakReference<Subscriber> subscriberRef =
                synchronousCancelStillAllowsForGCDoSubscribe(processor, queue);
        System.gc();
        Thread.sleep(300);
        assertEquals(subscriberRef, queue.remove(100));
    }

    private WeakReference<Subscriber> synchronousCancelStillAllowsForGCDoSubscribe(
            CompletableProcessor processor, ReferenceQueue<Subscriber> queue) {
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                cancellable.cancel();
            }

            @Override
            public void onComplete() {
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
        multiThreadedAddAlwaysTerminates(DELIBERATE_EXCEPTION);
    }

    @Test
    void multiThreadedAddAlwaysTerminatesComplete() throws Exception {
        multiThreadedAddAlwaysTerminates(null);
    }

    private static void multiThreadedAddAlwaysTerminates(@Nullable Throwable cause) throws Exception {
        final int subscriberCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(subscriberCount + 1);
        List<Single<Subscriber>> subscriberSingles = new ArrayList<>(subscriberCount);
        CompletableProcessor processor = new CompletableProcessor(subscriberCount);
        for (int i = 0; i < subscriberCount; ++i) {
            subscriberSingles.add(EXECUTOR_RULE.executor().submit(() -> {
                Subscriber subscriber = mock(Subscriber.class);
                barrier.await();
                processor.subscribe(subscriber);
                return subscriber;
            }));
        }

        Future<Collection<Subscriber>> future = collectUnordered(subscriberSingles, subscriberCount).toFuture();
        barrier.await();
        if (cause != null) {
            processor.onError(cause);

            Collection<Subscriber> subscribers = future.get();
            for (Subscriber s : subscribers) {
                verify(s).onError(cause);
            }
        } else {
            processor.onComplete();

            Collection<Subscriber> subscribers = future.get();
            for (Subscriber s : subscribers) {
                verify(s).onComplete();
            }
        }
    }
}
