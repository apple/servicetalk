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

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleWithExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.completable.AbstractPublishAndSubscribeOnTest.verifyCapturedThreads;
import static java.lang.Thread.currentThread;

public abstract class AbstractPublishAndSubscribeOnTest {

    static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    static final int OFFLOADED_SUBSCRIBER_THREAD = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = new ExecutorRule();

    protected AtomicReferenceArray<Thread> setupAndSubscribe(
            Function<Single<String>, Single<String>> offloadingFunction) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(2);

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.executor(), success("Hello"))
                .doBeforeSuccess(__ -> capturedThreads.set(ORIGINAL_SUBSCRIBER_THREAD, currentThread()));

        Single<String> offloaded = offloadingFunction.apply(original);

        offloaded.doAfterFinally(allDone::countDown)
                .doBeforeSuccess(__ -> capturedThreads.set(OFFLOADED_SUBSCRIBER_THREAD, currentThread()))
                .subscribe(val -> { });
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }

    protected AtomicReferenceArray<Thread> setupForCancelAndSubscribe(
            Function<Single<String>, Single<String>> offloadingFunction) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        AtomicReferenceArray<Thread> capturedThreads = new AtomicReferenceArray<>(2);

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.executor(),
                Single.<String>never())
                .doAfterCancel(() -> {
                    capturedThreads.set(ORIGINAL_SUBSCRIBER_THREAD, currentThread());
                    allDone.countDown();
                });

        Single<String> offloaded = offloadingFunction.apply(original);

        offloaded.doBeforeCancel(() -> capturedThreads.set(OFFLOADED_SUBSCRIBER_THREAD, currentThread()))
                .subscribe(val -> { }).cancel();
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }
}
