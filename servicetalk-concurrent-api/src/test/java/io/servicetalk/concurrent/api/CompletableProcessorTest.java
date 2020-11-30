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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

public class CompletableProcessorTest {
    private final TestCompletableSubscriber rule = new TestCompletableSubscriber();
    private final TestCompletableSubscriber rule2 = new TestCompletableSubscriber();

    @Test
    public void testCompleteBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        toSource(processor).subscribe(rule);
        rule.awaitOnComplete();
    }

    @Test
    public void testErrorBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        toSource(processor).subscribe(rule);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCompleteAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(false));
        processor.onComplete();
        rule.awaitOnComplete();
    }

    @Test
    public void testErrorAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(false));
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCompleteThenError() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        processor.onError(DELIBERATE_EXCEPTION);
        toSource(processor).subscribe(rule);
        rule.awaitOnComplete();
    }

    @Test
    public void testErrorThenComplete() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onComplete();
        toSource(processor).subscribe(rule);
        assertThat(rule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        CompletableProcessor processor = new CompletableProcessor();
        toSource(processor).subscribe(rule);
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(false));
        toSource(processor).subscribe(rule2);
        assertThat(rule2.pollTerminal(10, MILLISECONDS), is(false));
        rule.awaitSubscription().cancel();
        processor.onComplete();
        assertThat(rule.pollTerminal(10, MILLISECONDS), is(false));
        rule2.awaitOnComplete();
    }

    @Test
    public void synchronousCancelStillAllowsForGC() throws InterruptedException {
        CompletableProcessor processor = new CompletableProcessor();
        ReferenceQueue<CompletableSource.Subscriber> queue = new ReferenceQueue<>();
        WeakReference<CompletableSource.Subscriber> subscriberRef =
                synchronousCancelStillAllowsForGCDoSubscribe(processor, queue);
        System.gc();
        Thread.sleep(300);
        assertEquals(subscriberRef, queue.remove(100));
    }

    private WeakReference<CompletableSource.Subscriber> synchronousCancelStillAllowsForGCDoSubscribe(
            CompletableProcessor processor, ReferenceQueue<CompletableSource.Subscriber> queue) {
        CompletableSource.Subscriber subscriber = new CompletableSource.Subscriber() {
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
}
