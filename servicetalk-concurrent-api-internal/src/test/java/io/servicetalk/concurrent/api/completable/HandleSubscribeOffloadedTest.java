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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.AbstractHandleSubscribeOffloadedTest;
import io.servicetalk.concurrent.api.Completable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.lang.Thread.currentThread;

class HandleSubscribeOffloadedTest extends AbstractHandleSubscribeOffloadedTest {

    private final Completable source = new Completable() {
        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            handleSubscribeInvokerRef.set(currentThread());
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onComplete();
        }
    };

    @Test
    void simpleSource() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyCompletableOffloadCount();
    }

    @Test
    void withSyncOperatorsAddedAfter() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor()).beforeOnComplete(() -> { }));
        verifyHandleSubscribeInvoker();
        verifyCompletableOffloadCount();
    }

    @Test
    void withSyncOperatorsAddedBefore() throws Exception {
        awaitTermination(source.beforeOnComplete(() -> { }).subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyCompletableOffloadCount();
    }

    @Test
    void withAsyncOperatorsAddedAfter() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor())
                .concat(executorForTimerRule.executor().submit(() -> { })));
        verifyHandleSubscribeInvoker();
        verifyCompletableOffloadCount();
    }

    @Test
    void withAsyncOperatorsAddedBefore() throws Exception {
        awaitTermination(source.concat(executorForTimerRule.executor().submit(() -> { }))
                .subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyCompletableOffloadCount();
    }

    private void awaitTermination(Completable completable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // completable.toFuture() will use the toSingle() conversion and we can not verify offload for
        // Completable.Subscriber. So we directly subscribe to the completable.
        completable.afterFinally(latch::countDown).subscribe();
        latch.await();
    }
}
