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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AbstractHandleSubscribeOffloadedTest;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ScalarValueSubscription;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static java.lang.Thread.currentThread;

public class HandleSubscribeOffloadedTest extends AbstractHandleSubscribeOffloadedTest {
    private final Publisher<Integer> source = new Publisher<Integer>() {
        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super Integer> subscriber) {
            handleSubscribeInvokerRef.set(currentThread());
            subscriber.onSubscribe(new ScalarValueSubscription<>(1, subscriber));
        }
    };

    @Test
    public void simpleSource() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyPublisherOffloadCount();
    }

    @Test
    public void withSyncOperatorsAddedAfter() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor()).beforeOnNext(__ -> { }));
        verifyHandleSubscribeInvoker();
        verifyPublisherOffloadCount();
    }

    @Test
    public void withSyncOperatorsAddedBefore() throws Exception {
        awaitTermination(source.beforeOnNext(__ -> { }).subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyPublisherOffloadCount();
    }

    @Test
    public void withAsyncOperatorsAddedAfter() throws Exception {
        awaitTermination(source.subscribeOn(newOffloadingAwareExecutor())
                .flatMapMergeSingle(t -> executorForTimerRule.executor().submit(() -> t)));
        verifyHandleSubscribeInvoker();
        verifyPublisherOffloadCount();
    }

    @Test
    public void withAsyncOperatorsAddedBefore() throws Exception {
        awaitTermination(source.flatMapMergeSingle(t -> executorForTimerRule.executor().submit(() -> t))
                .subscribeOn(newOffloadingAwareExecutor()));
        verifyHandleSubscribeInvoker();
        verifyPublisherOffloadCount();
    }

    private void awaitTermination(Publisher<Integer> publisher) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // publisher.toFuture() will use the toSingle() conversion and we can not verify offload for
        // Publisher.Subscriber. So we directly subscribe to the publisher.
        publisher.afterFinally(latch::countDown).forEach(__ -> { });
        latch.await();
    }
}
