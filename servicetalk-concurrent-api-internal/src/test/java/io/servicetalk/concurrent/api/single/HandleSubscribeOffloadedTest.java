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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AbstractHandleSubscribeOffloadedTest;
import io.servicetalk.concurrent.api.Single;

import org.junit.Test;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.lang.Thread.currentThread;

public class HandleSubscribeOffloadedTest extends AbstractHandleSubscribeOffloadedTest {

    private final Single<Integer> source = new Single<Integer>() {
        @Override
        protected void handleSubscribe(final SingleSource.Subscriber<? super Integer> subscriber) {
            handleSubscribeInvokerRef.set(currentThread());
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onSuccess(1);
        }
    };

    @Test
    public void simpleSource() throws Exception {
        source.subscribeOn(newOffloadingAwareExecutor()).toFuture().get();
        verifyHandleSubscribeInvoker();
        verifySingleOffloadCount();
    }

    @Test
    public void withSyncOperatorsAddedAfter() throws Exception {
        source.subscribeOn(newOffloadingAwareExecutor()).doBeforeOnSuccess(__ -> { }).toFuture().get();
        verifyHandleSubscribeInvoker();
        verifySingleOffloadCount();
    }

    @Test
    public void withSyncOperatorsAddedBefore() throws Exception {
        source.doBeforeOnSuccess(__ -> { }).subscribeOn(newOffloadingAwareExecutor()).toFuture().get();
        verifyHandleSubscribeInvoker();
        verifySingleOffloadCount();
    }

    @Test
    public void withAsyncOperatorsAddedAfter() throws Exception {
        source.subscribeOn(newOffloadingAwareExecutor())
                .flatMap(t -> executorForTimerRule.executor().submit(() -> t))
                .toFuture().get();
        verifyHandleSubscribeInvoker();
        verifySingleOffloadCount();
    }

    @Test
    public void withAsyncOperatorsAddedBefore() throws Exception {
        source.flatMap(t -> executorForTimerRule.executor().submit(() -> t))
                .subscribeOn(newOffloadingAwareExecutor())
                .toFuture().get();
        verifyHandleSubscribeInvoker();
        verifySingleOffloadCount();
    }
}
