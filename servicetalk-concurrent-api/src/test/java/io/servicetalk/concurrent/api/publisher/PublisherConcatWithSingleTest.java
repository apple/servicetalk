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

import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PublisherConcatWithSingleTest {
    private TestSubscription subscription;
    private TestCancellable cancellable;
    private TestPublisher<Long> source;
    private TestPublisherSubscriber<Long> subscriber;
    private TestSingle<Long> single;

    PublisherConcatWithSingleTest() {
        initState();
        toSource(source.concat(single)).subscribe(subscriber);
        source.onSubscribe(subscription);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat("Next source subscribed before termination.", single.isSubscribed(), is(false));
    }

    private void initState() {
        subscription = new TestSubscription();
        cancellable = new TestCancellable();
        source = new TestPublisher<>();
        subscriber = new TestPublisherSubscriber<>();
        single = new TestSingle<>();
    }

    private void initStateOnNextThrows() {
        initState();
        toSource(source.concat(single)
                .beforeOnNext(n -> {
                    throw DELIBERATE_EXCEPTION;
                })
        ).subscribe(subscriber);
    }

    @Test
    void subscriberDemandThenOnNextThrowsSendsOnError() {
        initStateOnNextThrows();
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        source.onSubscribe(subscription);
        source.onComplete();
        single.onSuccess(1L);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void subscriberOnNextThenDemandThrowsSendsOnError() {
        initStateOnNextThrows();
        source.onSubscribe(subscription);
        source.onComplete();
        single.onSuccess(1L);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void bothComplete() {
        testBothComplete(2L);
    }

    @Test
    void publisherEmpty() {
        completeSource();
        subscriber.awaitSubscription().request(1);
        single.onSuccess(1L);
        verifySingleSuccessTerminatesSubscriber(1L);
    }

    @Test
    void nextEmitsNull() {
        testBothComplete(null);
    }

    @Test
    void sourceError() {
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat("Next source subscribed on error.", single.isSubscribed(), is(false));
        verifySubscriberErrored();
    }

    @Test
    void nextError() {
        subscriber.awaitSubscription().request(1);
        source.onNext(1L);
        source.onComplete();
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1L));
        assertThat("Next source not subscribed.", single.isSubscribed(), is(true));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        single.onError(DELIBERATE_EXCEPTION);
        verifySubscriberErrored();
    }

    @Test
    void sourceCancel() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Source subscription not cancelled.", subscription.isCancelled(), is(true));
        assertThat("Next source subscribed on cancellation.", single.isSubscribed(), is(false));
        source.onComplete();
        assertThat("Next source not subscribed.", single.isSubscribed(), is(true));
        single.onSubscribe(cancellable);
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @Test
    void nextCancel() {
        source.onComplete();
        assertThat("Next source not subscribed.", single.isSubscribed(), is(true));
        subscriber.awaitSubscription().cancel();
        single.onSubscribe(cancellable);
        assertThat("Next cancellable not cancelled.", cancellable.isCancelled(), is(true));
    }

    @Test
    void onSuccessBeforeRequest() {
        testOnSuccessBeforeRequest(1L);
    }

    @Test
    void onSuccessWithNullBeforeRequest() {
        testOnSuccessBeforeRequest(null);
    }

    @Test
    void invalidRequestNNegative1BeforeProcessingSingle() {
        invalidRequestNWhenProcessingSingle(-1, true);
    }

    @Test
    void invalidRequestNNegative1AfterProcessingSingle() {
        invalidRequestNWhenProcessingSingle(-1, false);
    }

    @Test
    void invalidRequestNZeroBeforeProcessingSingle() {
        invalidRequestNWhenProcessingSingle(0, true);
    }

    @Test
    void invalidRequestNZeroAfterProcessingSingle() {
        invalidRequestNWhenProcessingSingle(0, false);
    }

    @Test
    void invalidRequestNLongMinBeforeProcessingSingle() {
        invalidRequestNWhenProcessingSingle(Long.MIN_VALUE, true);
    }

    @Test
    void invalidRequestNLongMinAfterProcessingSingle() {
        invalidRequestNWhenProcessingSingle(Long.MIN_VALUE, false);
    }

    @Test
    void validRequestNAfterInvalidRequestNNegative1AfterProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(-1, false);
    }

    @Test
    void validRequestNAfterInvalidRequestNNegative1BeforeProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(-1, true);
    }

    @Test
    void validRequestNAfterInvalidRequestNZeroBeforeProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(0, true);
    }

    @Test
    void validRequestNAfterInvalidRequestNZeroAfterProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(0, false);
    }

    @Test
    void validRequestNAfterInvalidRequestNLongMinBeforeProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(Long.MIN_VALUE, true);
    }

    @Test
    void validRequestNAfterInvalidRequestNLongMinAfterProcessingSingle() {
        validRequestNAfterInvalidRequestNWhenProcessingSingle(Long.MIN_VALUE, false);
    }

    private void validRequestNAfterInvalidRequestNWhenProcessingSingle(long n, boolean requestNBeforeSuccess) {
        completeSource();
        if (requestNBeforeSuccess) {
            subscriber.awaitSubscription().request(n);
            subscriber.awaitSubscription().request(Long.MAX_VALUE);
        }
        single.onSuccess(10L);
        if (!requestNBeforeSuccess) {
            subscriber.awaitSubscription().request(n);
            subscriber.awaitSubscription().request(Long.MAX_VALUE);
        }
        assertThat("Unexpected termination (expected error).", subscriber.awaitOnError(),
                is(instanceOf(IllegalArgumentException.class)));
    }

    private void invalidRequestNWhenProcessingSingle(long n, boolean requestNBeforeSuccess) {
        completeSource();
        if (requestNBeforeSuccess) {
            subscriber.awaitSubscription().request(n);
        }
        single.onSuccess(10L);
        if (!requestNBeforeSuccess) {
            subscriber.awaitSubscription().request(n);
        }
        assertThat("Unexpected termination (expected error).", subscriber.awaitOnError(),
                is(instanceOf(IllegalArgumentException.class)));
    }

    private void testOnSuccessBeforeRequest(@Nullable Long nextResult) {
        emitOneItemFromSource();
        completeSource();
        single.onSuccess(nextResult);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        verifySingleSuccessTerminatesSubscriber(nextResult);
    }

    private void testBothComplete(@Nullable final Long nextResult) {
        emitOneItemFromSource();
        completeSource();
        subscriber.awaitSubscription().request(1);
        single.onSuccess(nextResult);
        verifySingleSuccessTerminatesSubscriber(nextResult);
    }

    private void verifySingleSuccessTerminatesSubscriber(@Nullable Long result) {
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(result));
        subscriber.awaitOnComplete();
    }

    private void completeSource() {
        source.onComplete();
        assertThat("Next source not subscribed.", single.isSubscribed(), is(true));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    private void emitOneItemFromSource() {
        subscriber.awaitSubscription().request(1);
        source.onNext(1L);
        assertThat("Unexpected items emitted.", subscriber.takeOnNext(), is(1L));
    }

    private void verifySubscriberErrored() {
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }
}
