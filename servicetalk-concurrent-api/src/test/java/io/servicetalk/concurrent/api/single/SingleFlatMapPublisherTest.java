/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SingleFlatMapPublisherTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();

    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestPublisher<String> publisher = new TestPublisher.Builder<String>()
            .disableAutoOnSubscribe().build();
    private final LegacyTestSingle<String> single = new LegacyTestSingle<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void testFirstAndSecondPropagate() {
        toSource(succeeded(1).flatMapPublisher(s1 -> from(new String[]{"Hello1", "Hello2"}).map(str1 -> str1 + s1)))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        assertThat(subscriber.takeOnNext(2), contains("Hello11", "Hello21"));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testSuccess() {
        toSource(succeeded(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("Hello1", "Hello2");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testPublisherEmitsError() {
        toSource(succeeded(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSingleEmitsError() {
        toSource(failed(DELIBERATE_EXCEPTION).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertFalse(publisher.isSubscribed());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelBeforeNextPublisher() {
        toSource(single.flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", single.isCancelled(), is(true));
    }

    @Test
    public void testCancelNoRequest() {
        toSource(single.flatMapPublisher(s -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        subscriber.awaitSubscription().request(1);
        single.verifyListenNotCalled();
    }

    @Test
    public void testCancelBeforeOnSubscribe() {
        toSource(single.flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        single.onSuccess("Hello");
        subscriber.awaitSubscription().cancel();
        single.verifyCancelled();
        publisher.onSubscribe(subscription);
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    public void testCancelPostOnSubscribe() {
        toSource(succeeded(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        toSource(succeeded(1).<String>flatMapPublisher(s1 -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        single.onSuccess("Hello");
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void nullInTerminalCallsOnError() {
        toSource(succeeded(1).<String>flatMapPublisher(s1 -> null)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        single.onSuccess("Hello");
        assertThat(subscriber.awaitOnError(), instanceOf(NullPointerException.class));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Single.never()
                .beforeCancel(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                                currentThread()));
                    }
                    analyzed.countDown();
                })
                .subscribeOn(executorExtension.executor())
                .flatMapPublisher(t -> Publisher.never())
                .forEach(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
