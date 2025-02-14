/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstAndTailToPackedSingleTest {
    private static final int METADATA = 0;
    private static final int FIRST_ELEMENT = 1;
    private static final int SECOND_ELEMENT = 2;
    private static final int LAST_ELEMENT = 3;

    private final TestSingleSubscriber<Data<Integer>> dataSubscriber = new TestSingleSubscriber<>();
    private final TestPublisher<Integer> upstream = new TestPublisher<>();

    private final TestPublisherSubscriber<Integer> payloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Integer> dupePayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void streamWithHeaderAndPayloadShouldProduceDataWithEmbeddedPayload() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(2);
        upstream.onNext(FIRST_ELEMENT, LAST_ELEMENT);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(2), contains(FIRST_ELEMENT, LAST_ELEMENT));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamWithHeaderAndEmptyPayloadShouldCompleteOnPublisherOnSubscribe()
            throws Exception {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        upstream.onComplete();
        assertThat(data.tail().toFuture().get(), empty());
    }

    @Test
    void emptyStreamShouldCompleteDataWithError() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onComplete();
        assertThat(dataSubscriber.awaitOnError(), instanceOf(IllegalStateException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: terminateUpstreamWithError={0}")
    @ValueSource(booleans = {false, true})
    void cancelDataRacingWithDataShouldCompleteAndFailPublisherOnSubscribe(boolean terminateUpstreamWithError) {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        dataSubscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        assertPayloadSubscriberReceivesCancellationException(terminateUpstreamWithError);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: terminateUpstreamWithError={0}")
    @ValueSource(booleans = {false, true})
    void cancelDataAfterDataCompleteShouldCancelUpstreamAndFailPublisherOnSubscribe(
            boolean terminateUpstreamWithError) {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        assertFalse(subscription.isCancelled());
        dataSubscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        toSource(data.tail()).subscribe(payloadSubscriber);
        assertPayloadSubscriberReceivesCancellationException(terminateUpstreamWithError);
    }

    private void assertPayloadSubscriberReceivesCancellationException(boolean terminateUpstreamWithError) {
        assertThat(payloadSubscriber.awaitOnError(), instanceOf(CancellationException.class));
        // Verify payloadSubscriber does not receive a terminal signal two times. If received, TestPublisherSubscriber
        // will throw IllegalStateException: Subscriber has already terminated.
        if (terminateUpstreamWithError) {
            upstream.onError(DELIBERATE_EXCEPTION);
        } else {
            upstream.onComplete();
        }
    }

    @Test
    void cancelDataBeforeDataCompleteShouldDeliverError() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        dataSubscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(dataSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void cancelUpstreamIfPayloadSubscriberThrowsFromOnSubscribe() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        assertFalse(subscription.isCancelled());

        Publisher<Integer> payload = data.tail();
        AtomicReference<Throwable> onError = new AtomicReference<>();
        toSource(payload).subscribe(new PublisherSource.Subscriber<Integer>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onNext(@Nullable final Integer payload) {
            }

            @Override
            public void onError(final Throwable t) {
                onError.set(t);
            }

            @Override
            public void onComplete() {
            }
        });
        assertTrue(subscription.isCancelled(), "Upstream subscription not cancelled");
        assertThat(onError.get(), is(DELIBERATE_EXCEPTION));
        toSource(payload).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: withPayload={0}")
    @ValueSource(booleans = {false, true})
    void streamErrorAfterPublisherSubscribeShouldDeliverError(boolean withPayload) {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(1);
        if (withPayload) {
            upstream.onNext(FIRST_ELEMENT);
        }
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        if (withPayload) {
            assertThat(payloadSubscriber.takeOnNext(), is(FIRST_ELEMENT));
        } else {
            assertThat(payloadSubscriber.pollAllOnNext(), is(empty()));
        }
        assertThat(payloadSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: withPayload={0}")
    @ValueSource(booleans = {false, true})
    void streamCompleteAfterPublisherSubscribeShouldDeliverComplete(boolean withPayload) {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        if (withPayload) {
            upstream.onNext(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT);
        }
        upstream.onComplete();
        if (withPayload) {
            assertThat(payloadSubscriber.takeOnNext(3), contains(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT));
        } else {
            assertThat(payloadSubscriber.pollAllOnNext(), is(empty()));
        }
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamCompleteBeforePublisherSubscribeShouldDeliverCompleteOnSubscribe() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        upstream.onComplete();
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamErrorBeforePublisherSubscribeShouldDeliverErrorOnSubscribe() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        toSource(data.tail()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void publisherSubscribeTwiceShouldFailSecondSubscriber() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT);
        toSource(data.tail()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void publisherSubscribeAgainAfterCompletingInitialSubscriberShouldFailSecondSubscriber() {
        Single<Data<Integer>> op = upstream.firstAndTail(Data::new);
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(METADATA);
        Data<Integer> data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.head(), equalTo(METADATA));
        toSource(data.tail()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(FIRST_ELEMENT, SECOND_ELEMENT, LAST_ELEMENT));
        payloadSubscriber.awaitOnComplete();
        toSource(data.tail()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
    }

    @Test
    void packerThrowsShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Integer> stream = from(METADATA);
        Single<Data<Integer>> op = stream.firstAndTail((head, tail) -> {
                    throw DELIBERATE_EXCEPTION;
                });
        toSource(op).subscribe(dataSubscriber);
        assertThat(dataSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private static final class Data<T> {
        private final T head;
        private final Publisher<T> tail;

        Data(T head, Publisher<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        T head() {
            return head;
        }

        Publisher<T> tail() {
            return tail;
        }
    }
}
