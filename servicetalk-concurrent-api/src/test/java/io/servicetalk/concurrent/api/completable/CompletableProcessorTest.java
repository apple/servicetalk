/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.LegacyMockedCompletableListenerRule;

import org.junit.Rule;
import org.junit.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertEquals;

public class CompletableProcessorTest {

    @Rule
    public final LegacyMockedCompletableListenerRule rule = new LegacyMockedCompletableListenerRule();
    @Rule
    public final LegacyMockedCompletableListenerRule rule2 = new LegacyMockedCompletableListenerRule();

    @Test
    public void testCompleteBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        rule.listen(processor).verifyCompletion();
    }

    @Test
    public void testErrorBeforeListen() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        rule.listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCompleteAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        rule.listen(processor).verifyNoEmissions();
        processor.onComplete();
        rule.verifyCompletion();
    }

    @Test
    public void testErrorAfterListen() {
        CompletableProcessor processor = new CompletableProcessor();
        rule.listen(processor).verifyNoEmissions();
        processor.onError(DELIBERATE_EXCEPTION);
        rule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCompleteThenError() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onComplete();
        processor.onError(DELIBERATE_EXCEPTION);
        rule.listen(processor).verifyCompletion();
    }

    @Test
    public void testErrorThenComplete() {
        CompletableProcessor processor = new CompletableProcessor();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onComplete();
        rule.listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        CompletableProcessor processor = new CompletableProcessor();
        rule.listen(processor).verifyNoEmissions();
        rule2.listen(processor).verifyNoEmissions();
        rule.cancel();
        processor.onComplete();
        rule.verifyNoEmissions();
        rule2.verifyCompletion();
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
