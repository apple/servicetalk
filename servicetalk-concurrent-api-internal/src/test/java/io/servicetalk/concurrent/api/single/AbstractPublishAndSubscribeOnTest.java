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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleWithExecutor;
import io.servicetalk.concurrent.api.internal.CaptureThreads;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.succeeded;

public abstract class AbstractPublishAndSubscribeOnTest {

    static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    static final int OFFLOADED_SUBSCRIBER_THREAD = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> originalSourceExecutorRule = ExecutorRule.newRule();
    CaptureThreads capturedThreads = new CaptureThreads(2);

    protected Thread[] setupAndSubscribe(
            Function<Single<String>, Single<String>> offloadingFunction) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.executor(), succeeded("Hello"))
                .beforeOnSuccess(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD));

        Single<String> offloaded = offloadingFunction.apply(original);

        offloaded.afterFinally(allDone::countDown)
                .beforeOnSuccess(__ -> capturedThreads.capture(OFFLOADED_SUBSCRIBER_THREAD))
                .subscribe(val -> { });
        allDone.await();

        return capturedThreads.verify();
    }

    protected Thread[] setupForCancelAndSubscribe(
            Function<Single<String>, Single<String>> offloadingFunction) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.executor(),
                Single.<String>never())
                .afterCancel(() -> {
                    capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD);
                    allDone.countDown();
                });

        Single<String> offloaded = offloadingFunction.apply(original);

        offloaded.beforeCancel(() -> capturedThreads.capture(OFFLOADED_SUBSCRIBER_THREAD))
                .subscribe(val -> { }).cancel();
        allDone.await();

        return capturedThreads.verify();
    }
}
