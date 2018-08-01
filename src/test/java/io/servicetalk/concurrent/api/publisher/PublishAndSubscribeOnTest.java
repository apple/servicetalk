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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherWithExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.just;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class PublishAndSubscribeOnTest {

    private static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    private static final int ORIGINAL_SUBSCRIPTION_THREAD = 1;
    private static final int OFFLOADED_SUBSCRIBER_THREAD = 2;
    private static final int OFFLOADED_SUBSCRIPTION_THREAD = 3;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = new ExecutorRule();

    @Test
    public void testPublishOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::publishOn);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testPublishOnOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::publishOnOverride);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testSubscribeOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::subscribeOn);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    @Test
    public void testSubscribeOnOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::subscribeOnOverride);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    @Test
    public void testNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::publishAndSubscribeOn);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    @Test
    public void testOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::publishAndSubscribeOnOverride);

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    private Thread[] setupAndSubscribe(BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[4];

        Publisher<String> original = new PublisherWithExecutor<>(originalSourceExecutorRule.getExecutor(),
                just("Hello"))
                .doBeforeNext($ -> capturedThreads[ORIGINAL_SUBSCRIBER_THREAD] = Thread.currentThread())
                .doBeforeRequest($ -> capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD] = Thread.currentThread());

        final Executor executor = executorRule.getExecutor();
        Publisher<String> offloaded = offloadingFunction.apply(original, executor);

        offloaded.doBeforeNext($ -> capturedThreads[OFFLOADED_SUBSCRIBER_THREAD] = Thread.currentThread())
                .doBeforeRequest($ -> capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD] = Thread.currentThread())
                .doAfterFinally(allDone::countDown)
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                        // Do not request from the caller thread to make sure synchronous request-onNext does not
                        // pollute thread capturing of subscription.
                        executorRule.getExecutor().execute(() -> s.request(MAX_VALUE));
                    }

                    @Override
                    public void onNext(final String s) {
                        // noop
                    }

                    @Override
                    public void onError(final Throwable t) {
                        // noop
                    }

                    @Override
                    public void onComplete() {
                        // noop
                    }
                });
        allDone.await();

        assertThat("All threads were not captured.", capturedThreads, hasItemInArray(notNullValue()));

        return capturedThreads;
    }
}
