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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SignalOffloaders.taskBasedOffloaderFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskBasedSignalOffloaderExecutorRejectionTests {

    @Rule
    public final ExpectedException expectedException = none();
    @Rule
    public final MockedSingleListenerRule<Integer> singleSubscriber = new MockedSingleListenerRule<>();
    @Rule
    public final MockedCompletableListenerRule completableSubscriber = new MockedCompletableListenerRule();

    private final AtomicBoolean rejectNextTask = new AtomicBoolean();
    private final AtomicInteger rejectTaskCount = new AtomicInteger();
    private final Executor mockExecutor;
    private final OffloaderAwareExecutor executor;

    public TaskBasedSignalOffloaderExecutorRejectionTests() {
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

    @Ignore("subscribeOn is currently broken; it does not offload handleSubscribe")
    @Test
    public void publisherSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        just(1).subscribeOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Ignore("subscribeOn is currently broken; it does not offload handleSubscribe")
    @Test
    public void singleSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        success(1).subscribeOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Ignore("subscribeOn is currently broken; it does not offload handleSubscribe")
    @Test
    public void completableSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        completed().subscribeOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Test
    public void publisherOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectRejection();
        Publisher.never().publishOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Test
    public void singleOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectRejection();
        Single.never().publishOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Test
    public void completableOnSubscribeRejects() throws Exception {
        rejectNextTask.set(true);
        expectRejection();
        Completable.never().publishOn(executor).toFuture().get();
        verifyRejectedTasks(1, 1);
    }

    @Test
    public void publisherOnNextRejects() {
        publisherPublishOnThrows(source -> source.onNext(1));
    }

    @Test
    public void publisherOnCompleteRejects() {
        publisherPublishOnThrows(TestPublisher::onComplete);
    }

    @Test
    public void publisherOnErrorRejects() {
        publisherPublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    public void singleOnSuccessRejects() {
        singlePublishOnThrows(source -> source.onSuccess(1));
    }

    @Test
    public void singleOnErrorRejects() {
        singlePublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    public void completableOnCompleteRejects() {
        completablePublishOnThrows(TestCompletable::onComplete);
    }

    @Test
    public void completableOnErrorRejects() {
        completablePublishOnThrows(source -> source.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    public void requestNRejects() {
        TestSubscription subscription = subscriptionRejects(s -> s.request(1));
        assertThat("Unexpected items requested from Subscription.", subscription.requested(), is(1));
    }

    @Test
    public void publisherCancelRejects() {
        TestSubscription subscription = subscriptionRejects(Cancellable::cancel);
        assertThat("Subscription not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    public void singleCancelRejects() {
        TestSingle<Integer> single = new TestSingle<>();
        singleSubscriber.listen(single.subscribeOn(executor));

        rejectNextTask.set(true);
        try {
            singleSubscriber.cancel();
            fail();
        } catch (RejectedExecutionException e) {
            assertThat("Unexpected rejection cause.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected rejection cause.", e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        }
        single.verifyCancelled();
        verifyRejectedTasks(1 /*handleSubscribe isn't offloaded due to a bug, else this should be 2*/, 1);
    }

    @Test
    public void completableCancelRejects() {
        TestCompletable completable = new TestCompletable();
        completableSubscriber.listen(completable.subscribeOn(executor));

        rejectNextTask.set(true);
        try {
            completableSubscriber.cancel();
            fail();
        } catch (RejectedExecutionException e) {
            assertThat("Unexpected rejection cause.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected rejection cause.", e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        }
        completable.verifyCancelled();
        verifyRejectedTasks(1 /*handleSubscribe isn't offloaded due to a bug, else this should be 2*/, 1);
    }

    private TestSubscription subscriptionRejects(final Consumer<Subscription> invokeMethodThatRejects) {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.subscribeOn(executor)).subscribe(subscriber);
        TestSubscription subscription = new TestSubscription();
        publisher.onSubscribe(subscription);
        assertThat("Subscription not received.", subscriber.subscriptionReceived(), is(true));
        rejectNextTask.set(true);

        invokeMethodThatRejects.accept(subscription);

        expectedException.expect(RejectedExecutionException.class);
        expectedException.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        subscriber.request(1);
        verifyRejectedTasks(1 /*handleSubscribe isn't offloaded due to a bug, else this should be 2*/, 1);
        return subscription;
    }

    private void publisherPublishOnThrows(Consumer<TestPublisher<Integer>> invokeMethodThatRejects) {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.publishOn(executor)).subscribe(subscriber);
        assertThat("Subscription not received.", subscriber.subscriptionReceived(), is(true));
        subscriber.request(1);
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(publisher);
        assertThat("Subscriber did not get a rejection error.", subscriber.isErrored(), is(true));
        assertThat("Unexpected error received by subscriber.", subscriber.error(),
                instanceOf(RejectedExecutionException.class));
        verifyRejectedTasks(2, 1);
    }

    private void singlePublishOnThrows(Consumer<TestSingle<Integer>> invokeMethodThatRejects) {
        TestSingle<Integer> single = new TestSingle<>();
        singleSubscriber.listen(single.publishOn(executor));
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(single);
        singleSubscriber.verifyFailure(RejectedExecutionException.class);
        verifyRejectedTasks(2, 1);
    }

    private void completablePublishOnThrows(Consumer<TestCompletable> invokeMethodThatRejects) {
        TestCompletable completable = new TestCompletable();
        completableSubscriber.listen(completable.publishOn(executor));
        rejectNextTask.set(true);
        invokeMethodThatRejects.accept(completable);
        completableSubscriber.verifyFailure(RejectedExecutionException.class);
        verifyRejectedTasks(2, 1);
    }

    private void verifyRejectedTasks(final int submitCount, final int rejectionCount) {
        verify(mockExecutor, times(submitCount)).execute(any());
        assertThat("Unexpected tasks rejected.", rejectTaskCount.get(), is(rejectionCount));
    }

    private void expectRejection() {
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(RejectedExecutionException.class));
    }
}
