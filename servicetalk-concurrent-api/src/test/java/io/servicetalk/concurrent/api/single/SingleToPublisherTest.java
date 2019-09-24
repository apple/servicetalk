/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;

public class SingleToPublisherTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<Executor> executorRule = ExecutorRule.newRule();

    private final TestPublisherSubscriber<String> verifier = new TestPublisherSubscriber<>();

    @Test
    public void testSuccessBeforeRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.request(1);
        assertThat(verifier.takeItems(), contains("Hello"));
        assertThat(verifier.takeTerminal(), is(complete()));
    }

    @Test
    public void testFailureBeforeRequest() {
        toSource(Single.<String>failed(DELIBERATE_EXCEPTION).toPublisher()).subscribe(verifier);
        verifier.request(1);
        assertThat(verifier.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSuccessAfterRequest() {
        TestSingle<String> single = new TestSingle<>();
        toSource(single.toPublisher()).subscribe(verifier);
        verifier.request(1);
        single.onSuccess("Hello");
        assertThat(verifier.takeItems(), contains("Hello"));
        assertThat(verifier.takeTerminal(), is(complete()));
    }

    @Test
    public void testFailedFuture() {
        toSource(Single.<String>failed(DELIBERATE_EXCEPTION).toPublisher()).subscribe(verifier);
        verifier.request(1);
        assertThat(verifier.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelBeforeRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        assertTrue(verifier.subscriptionReceived());
        assertThat(verifier.takeItems(), hasSize(0));
        assertThat(verifier.takeTerminal(), nullValue());
    }

    @Test
    public void testCancelAfterRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.request(1);
        assertThat(verifier.takeItems(), contains("Hello"));
        assertThat(verifier.takeTerminal(), is(complete()));
        verifier.cancel();
    }

    @Test
    public void testInvalidRequestN() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.request(-1);
        assertThat(verifier.takeError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void testSuccessAfterInvalidRequestN() {
        TestSingle<String> single = new TestSingle<>();
        toSource(single.toPublisher()).subscribe(verifier);
        verifier.request(-1);
        assertThat(verifier.takeError(), instanceOf(IllegalArgumentException.class));
        single.onSuccess("Hello");
        assertThat(verifier.takeTerminal(), is(nullValue()));
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        toSource(Single.succeeded("Hello").toPublisher().whenOnNext(n -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(verifier);
        // The mock behavior must be applied after subscribe, because a new mock is created as part of this process.
        verifier.request(1);
        assertThat(verifier.takeItems(), contains("Hello"));
        assertThat(verifier.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        toSource(single.beforeCancel(() -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                        currentThread()));
            }
        }).afterCancel(analyzed::countDown).subscribeOn(executorRule.executor()).toPublisher()).subscribe(subscriber);
        TestCancellable cancellable = new TestCancellable();
        single.onSubscribe(cancellable); // waits till subscribed.
        assertThat("Single not subscribed.", single.isSubscribed(), is(true));
        assertThat("Subscription not received.", subscriber.subscriptionReceived(), is(true));
        subscriber.cancel();
        analyzed.await();
        assertThat("Single did not get a cancel.", cancellable.isCancelled(), is(true));
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    @Test
    public void publishOnOriginalIsPreservedOnCompleteFromRequest() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch receivedOnSuccess = new CountDownLatch(1);
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, receivedOnSuccess);
        single.onSuccess("Hello");
        receivedOnSuccess.await();
        subscriber.request(1);
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        assertThat("No terminal received.", subscriber.takeItems(), contains("Hello"));
        assertThat("No terminal received.", subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void publishOnOriginalIsPreservedOnCompleteFromOnSuccess() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        subscriber.request(1);
        single.onSuccess("Hello");
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        assertThat("No terminal received.", subscriber.takeItems(), contains("Hello"));
        assertThat("No terminal received.", subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void publishOnOriginalIsPreservedOnError() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        subscriber.request(Long.MAX_VALUE);
        single.onError(DELIBERATE_EXCEPTION);
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        assertThat("Unexpected items received: " + subscriber.items(), subscriber.items(), hasSize(0));
        assertThat("Subscriber is not marked as failed", subscriber.isErrored(), is(true));
        Throwable err = subscriber.takeError();
        assertThat("No error received.", err, is(notNullValue()));
        assertThat("Wrong error received.", err, is(sameInstance(DELIBERATE_EXCEPTION)));
    }

    @Test
    public void publishOnOriginalIsPreservedOnInvalidRequestN() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        subscriber.request(-1);
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        Throwable err = subscriber.takeError();
        assertThat("No error received.", err, is(notNullValue()));
        assertThat("Wrong error received.", err, is(instanceOf(IllegalArgumentException.class)));
    }

    private CountDownLatch publishOnOriginalIsPreserved0(final ConcurrentLinkedQueue<AssertionError> errors,
                                                         final TestPublisherSubscriber<String> subscriber,
                                                         final TestSingle<String> single,
                                                         @Nullable final CountDownLatch receivedOnSuccessFromSingle)
            throws Exception {
        final Thread testThread = currentThread();
        CountDownLatch analyzed = new CountDownLatch(1);
        CountDownLatch receivedOnSubscribe = new CountDownLatch(1);
        toSource(single.publishOn(executorRule.executor())
                .beforeOnSuccess(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onSuccess " +
                                "(from Completable). Thread: " + currentThread()));
                    }
                })
                .afterOnSuccess(__ -> {
                    if (receivedOnSuccessFromSingle != null) {
                        receivedOnSuccessFromSingle.countDown();
                    }
                })
                .beforeOnError(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onError" +
                                "(from Completable). Thread: " + currentThread()));
                    }
                })
                .toPublisher()
                .beforeOnNext(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onNext " +
                                "(from Publisher). Thread: " + currentThread()));
                    }
                })
                .beforeOnError(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onError " +
                                "(from Publisher). Thread: " + currentThread()));
                    }
                })
                .afterOnSubscribe(__ -> receivedOnSubscribe.countDown())
                .afterOnComplete(analyzed::countDown)
                .afterOnError(__ -> analyzed.countDown())
        ).subscribe(subscriber);
        single.onSubscribe(new TestCancellable()); // await subscribe
        receivedOnSubscribe.await();
        assertThat("Single not subscribed.", single.isSubscribed(), is(true));
        return analyzed;
    }
}
