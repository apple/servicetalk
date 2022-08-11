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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

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

class SpliceFlatStreamToMetaSingleTest {
    private final TestSingleSubscriber<Data> dataSubscriber = new TestSingleSubscriber<>();
    private final TestPublisher<Object> upstream = new TestPublisher<>();
    private final MetaData metaData = new MetaData("foo");
    private final Payload one = new Payload();
    private final Payload two = new Payload();
    private final LastPayload last = new LastPayload();

    private final TestPublisherSubscriber<Payload> payloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Payload> dupePayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void streamWithHeaderAndPayloadShouldProduceDataWithEmbeddedPayload() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(2);
        upstream.onNext(one, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(2), contains(one, last));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamWithHeaderAndEmptyPayloadShouldCompleteOnPublisherOnSubscribe()
            throws Exception {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        upstream.onComplete();
        assertThat(data.getPayload().toFuture().get(), empty());
    }

    @Test
    void emptyStreamShouldCompleteDataWithError() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onComplete();
        assertThat(dataSubscriber.awaitOnError(), instanceOf(IllegalStateException.class));
    }

    @Test
    void cancelDataRacingWithDataShouldCompleteAndFailPublisherOnSubscribe() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        dataSubscriber.awaitSubscription().cancel();
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.awaitOnError(), instanceOf(CancellationException.class));
    }

    @Test
    void cancelDataAfterDataCompleteShouldIgnoreCancelAndDeliverPublisherOnComplete() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        dataSubscriber.awaitSubscription().cancel();
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(one, two, last));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void cancelDataBeforeDataCompleteShouldDeliverError() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        dataSubscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(dataSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void streamErrorAfterPublisherSubscribeShouldDeliverError() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(1);
        upstream.onNext(one);
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(payloadSubscriber.takeOnNext(), is(one));
        assertThat(payloadSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void streamCompleteAfterPublisherSubscribeShouldDeliverComplete() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(one, two, last));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamCompleteBeforePublisherSubscribeShouldDeliverCompleteOnSubscribe() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        upstream.onComplete();
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void streamErrorBeforePublisherSubscribeShouldDeliverErrorOnSubscribe() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void publisherSubscribeTwiceShouldFailSecondSubscriber() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        toSource(data.getPayload()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(one, two, last));
        payloadSubscriber.awaitOnComplete();
    }

    @Test
    void publisherSubscribeAgainAfterCompletingInitialSubscriberShouldFailSecondSubscriber() {
        Single<Data> op = upstream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        toSource(op).subscribe(dataSubscriber);
        upstream.onNext(metaData);
        Data data = dataSubscriber.awaitOnSuccess();
        assertThat(data, is(notNullValue()));
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeOnNext(3), contains(one, two, last));
        payloadSubscriber.awaitOnComplete();
        toSource(data.getPayload()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
    }

    @Test
    void packerThrowsShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Object> stream = from(metaData);
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>((md, payload) -> {
                    throw DELIBERATE_EXCEPTION;
                }));
        toSource(op).subscribe(dataSubscriber);
        assertThat(dataSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private static class MetaData {
        private final String meta;

        MetaData(String meta) {
            this.meta = meta;
        }

        String meta() {
            return meta;
        }
    }

    private static final class Data extends MetaData {
        private final Publisher<Payload> payload;

        Data(MetaData metaData, Publisher<Payload> payload) {
            super(metaData.meta());
            this.payload = payload;
        }

        Publisher<Payload> getPayload() {
            return payload;
        }
    }

    private static class Payload {
    }

    private static final class LastPayload extends Payload {
    }
}
