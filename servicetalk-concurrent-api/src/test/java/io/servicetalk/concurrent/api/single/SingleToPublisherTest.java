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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class SingleToPublisherTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> EXEC = ExecutorExtension.withCachedExecutor().setClassLevel(true);

    private final TestPublisherSubscriber<String> verifier = new TestPublisherSubscriber<>();

    @Test
    void testSuccessBeforeRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(1);
        assertThat(verifier.takeOnNext(), is("Hello"));
        verifier.awaitOnComplete();
    }

    @Test
    void testFailureBeforeRequest() {
        toSource(Single.<String>failed(DELIBERATE_EXCEPTION).toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(1);
        assertThat(verifier.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testSuccessAfterRequest() {
        TestSingle<String> single = new TestSingle<>();
        toSource(single.toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(1);
        single.onSuccess("Hello");
        assertThat(verifier.takeOnNext(), is("Hello"));
        verifier.awaitOnComplete();
    }

    @Test
    void testFailedFuture() {
        toSource(Single.<String>failed(DELIBERATE_EXCEPTION).toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(1);
        assertThat(verifier.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCancelBeforeRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.awaitSubscription();
        assertThat(verifier.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(verifier.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void testCancelAfterRequest() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(1);
        assertThat(verifier.takeOnNext(), is("Hello"));
        verifier.awaitOnComplete();
        verifier.awaitSubscription().cancel();
    }

    @Test
    void testInvalidRequestN() {
        toSource(Single.succeeded("Hello").toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(-1);
        assertThat(verifier.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void testSuccessAfterInvalidRequestN() {
        TestSingle<String> single = new TestSingle<>();
        toSource(single.toPublisher()).subscribe(verifier);
        verifier.awaitSubscription().request(-1);
        assertThat(verifier.awaitOnError(), instanceOf(IllegalArgumentException.class));
        single.onSuccess("Hello");
        assertThat(verifier.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void exceptionInTerminalCallsOnError() {
        toSource(Single.succeeded("Hello").toPublisher().afterOnNext(n -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(verifier);
        // The mock behavior must be applied after subscribe, because a new mock is created as part of this process.
        verifier.awaitSubscription().request(1);
        assertThat(verifier.takeOnNext(), is("Hello"));
        assertThat(verifier.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void subscribeOnOriginalIsPreserved() throws Exception {
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
        }).afterCancel(analyzed::countDown).subscribeOn(EXEC.executor()).toPublisher())
                .subscribe(subscriber);
        TestCancellable cancellable = new TestCancellable();
        single.onSubscribe(cancellable); // waits till subscribed.
        assertThat("Single not subscribed.", single.isSubscribed(), is(true));
        subscriber.awaitSubscription().cancel();
        analyzed.await();
        assertThat("Single did not get a cancel.", cancellable.isCancelled(), is(true));
        assertNoAsyncErrors(errors);
    }

    @Test
    @Disabled("The Publisher Subscriber is now not offloaded")
    void publishOnOriginalIsPreservedOnCompleteFromRequest() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<String> subscriber =
                new io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch receivedOnSuccess = new CountDownLatch(1);
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, receivedOnSuccess);
        single.onSuccess("Hello");
        receivedOnSuccess.await();
        subscriber.awaitSubscription().request(1);
        analyzed.await();
        assertNoAsyncErrors(errors);
        assertThat("No terminal received.", subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitOnComplete();
    }

    @Test
    void publishOnOriginalIsPreservedOnCompleteFromOnSuccess() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<String> subscriber =
                new io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        subscriber.awaitSubscription().request(1);
        single.onSuccess("Hello");
        analyzed.await();
        assertNoAsyncErrors(errors);
        assertThat("No terminal received.", subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitOnComplete();
    }

    @Test
    void publishOnOriginalIsPreservedOnError() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<String> subscriber = new
                io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        single.onError(DELIBERATE_EXCEPTION);
        analyzed.await();
        assertNoAsyncErrors(errors);
        Throwable err = subscriber.awaitOnError();
        assertThat("No error received.", err, is(notNullValue()));
        assertThat("Wrong error received.", err, is(sameInstance(DELIBERATE_EXCEPTION)));
    }

    @Test
    @Disabled("The Publisher Subscriber is now not offloaded")
    void publishOnOriginalIsPreservedOnInvalidRequestN() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<String> subscriber =
                new io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<>();
        TestSingle<String> single = new TestSingle.Builder<String>().disableAutoOnSubscribe().build();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, single, null);
        subscriber.awaitSubscription().request(-1);
        analyzed.await();
        assertNoAsyncErrors(errors);
        Throwable err = subscriber.awaitOnError();
        assertThat("No error received.", err, is(notNullValue()));
        assertThat("Wrong error received.", err, is(instanceOf(IllegalArgumentException.class)));
    }

    private CountDownLatch publishOnOriginalIsPreserved0(
            final ConcurrentLinkedQueue<AssertionError> errors,
            final io.servicetalk.concurrent.test.internal.TestPublisherSubscriber<String> subscriber,
            final TestSingle<String> single,
            @Nullable final CountDownLatch receivedOnSuccessFromSingle) throws Exception {
        final Thread testThread = currentThread();
        CountDownLatch analyzed = new CountDownLatch(1);
        CountDownLatch receivedOnSubscribe = new CountDownLatch(1);
        toSource(single.publishOn(EXEC.executor())
                .beforeOnSuccess(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onSuccess " +
                                "(from Single). Thread: " + currentThread()));
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
                                "(from Single). Thread: " + currentThread()));
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
