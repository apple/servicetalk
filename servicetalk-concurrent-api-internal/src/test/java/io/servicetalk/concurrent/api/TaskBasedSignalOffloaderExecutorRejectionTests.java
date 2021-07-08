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

import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SignalOffloaders.taskBasedOffloaderFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskBasedSignalOffloaderExecutorRejectionTests {

    private final AtomicBoolean rejectNextTask = new AtomicBoolean();
    private final AtomicInteger rejectTaskCount = new AtomicInteger();
    private final Executor mockExecutor;
    private final OffloaderAwareExecutor executor;

    TaskBasedSignalOffloaderExecutorRejectionTests() {
        mockExecutor = mock(Executor.class);
        executor = new OffloaderAwareExecutor(mockExecutor, taskBasedOffloaderFactory());
        when(executor.execute(any())).then(invocation -> {
            if (rejectNextTask.get()) {
                rejectTaskCount.incrementAndGet();
                throw new RejectedExecutionException(DELIBERATE_EXCEPTION);
            }
            Runnable task = invocation.getArgument(0);
            task.run();
            return IGNORE_CANCEL;
        });
    }

    @Test
    void publisherSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(from(1).subscribeOn(executor).toFuture());
    }

    @Test
    void singleSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(succeeded(1).subscribeOn(executor).toFuture());
    }

    @Test
    void completableSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(completed().subscribeOn(executor).toFuture());
    }

    @Test
    void publisherOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(Publisher.never().publishOn(executor).toFuture());
    }

    @Test
    void singleOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(Single.never().publishOn(executor).toFuture());
    }

    @Test
    void completableOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectFailureAndVerify(Completable.never().publishOn(executor).toFuture());
    }

    @Test
    void publisherOnNextRejects() {
        publisherPublishOnThrows(source -> source.onNext(1));
    }

    @Test
    void publisherOnCompleteRejects() {
        publisherPublishOnThrows(TestPublisher::onComplete);
    }

    @Test
    void publisherOnErrorRejects() {
        publisherPublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    void singleOnSuccessRejects() {
        singlePublishOnThrows(source -> source.onSuccess(1));
    }

    @Test
    void singleOnErrorRejects() {
        singlePublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    void completableOnCompleteRejects() {
        completablePublishOnThrows(TestCompletable::onComplete);
    }

    @Test
    void completableOnErrorRejects() {
        completablePublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    void requestNRejects() {
        TestSubscription subscription = subscriptionRejects(s -> s.awaitSubscription().request(1));
        assertThat("Unexpected items requested from Subscription.", subscription.requested(), is(1L));
    }

    @Test
    void publisherCancelRejects() {
        TestSubscription subscription = subscriptionRejects(sub -> sub.awaitSubscription().cancel());
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void singleCancelRejects() {
        TestSingle<Integer> single = new TestSingle<>();
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        toSource(single.subscribeOn(executor)).subscribe(subscriber);

        TestCancellable cancellable = new TestCancellable();
        single.onSubscribe(cancellable);
        rejectNextTask.set(true);
        subscriber.awaitSubscription().cancel();
        assertThat("Unexpected cancelled state.", cancellable.isCancelled(), is(true));
        verifyRejectedTasks(2, 1);
    }

    @Test
    void completableCancelRejects() {
        TestCompletable single = new TestCompletable();
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(single.subscribeOn(executor)).subscribe(subscriber);

        TestCancellable cancellable = new TestCancellable();
        single.onSubscribe(cancellable);
        rejectNextTask.set(true);
        subscriber.awaitSubscription().cancel();
        assertThat("Unexpected cancelled state.", cancellable.isCancelled(), is(true));
        verifyRejectedTasks(2, 1);
    }

    private TestSubscription subscriptionRejects(final Consumer<TestPublisherSubscriber> invokeMethodThatRejects) {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.subscribeOn(executor)).subscribe(subscriber);
        TestSubscription subscription = new TestSubscription();
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription();
        rejectNextTask.set(true);

        invokeMethodThatRejects.accept(subscriber);

        verifyRejectedTasks(2, 1);
        return subscription;
    }

    private void publisherPublishOnThrows(Consumer<TestPublisher<Integer>> invokeMethodThatRejects) {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.publishOn(executor)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(publisher);
        assertThat(subscriber.awaitOnError(), instanceOf(RejectedExecutionException.class));
        verifyRejectedTasks(2, 1);
    }

    private void singlePublishOnThrows(Consumer<TestSingle<Integer>> invokeMethodThatRejects) {
        TestSingle<Integer> single = new TestSingle<>();
        TestSingleSubscriber<Integer> subscriber =
                new TestSingleSubscriber<>();
        toSource(single.publishOn(executor)).subscribe(subscriber);
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(single);
        assertThat("Unexpected failure.", subscriber.awaitOnError(), instanceOf(RejectedExecutionException.class));
        verifyRejectedTasks(2, 1);
    }

    private void completablePublishOnThrows(Consumer<TestCompletable> invokeMethodThatRejects) {
        TestCompletable completable = new TestCompletable();
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        toSource(completable.publishOn(executor)).subscribe(subscriber);
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(completable);
        assertThat("Unexpected failure.", subscriber.awaitOnError(), instanceOf(RejectedExecutionException.class));
        verifyRejectedTasks(2, 1);
    }

    private void expectFailureAndVerify(final Future<?> result) throws InterruptedException {
        try {
            result.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Unexpected rejection cause.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected rejection cause.", e.getCause().getCause(),
                    sameInstance(DELIBERATE_EXCEPTION));
        }
        verifyRejectedTasks(1, 1);
    }

    private void verifyRejectedTasks(final int submitCount, final int rejectionCount) {
        verify(mockExecutor, times(submitCount)).execute(any());
        assertThat("Unexpected tasks rejected.", rejectTaskCount.get(), is(rejectionCount));
    }
}
