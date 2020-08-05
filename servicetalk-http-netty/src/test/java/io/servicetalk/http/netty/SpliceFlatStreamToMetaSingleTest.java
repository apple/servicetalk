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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CancellationException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class SpliceFlatStreamToMetaSingleTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final LegacyMockedSingleListenerRule<Data> dataSubscriber = new LegacyMockedSingleListenerRule<>();

    private final TestPublisher<Object> upstream = new TestPublisher<>();
    private final MetaData metaData = new MetaData("foo");
    private final Payload one = new Payload();
    private final Payload two = new Payload();
    private final LastPayload last = new LastPayload();

    private final TestPublisherSubscriber<Payload> payloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Payload> dupePayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void streamWithHeaderAndPayloadShouldProduceDataWithEmbeddedPayload() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(2);
        upstream.onNext(one, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeItems(), contains(one, last));
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void streamWithHeaderAndEmptyPayloadShouldCompleteOnPublisherOnSubscribe()
            throws Exception {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        upstream.onComplete();
        assertThat(data.getPayload().toFuture().get(), empty());
    }

    @Test
    public void emptyStreamShouldCompleteDataWithError() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onComplete();
        dataSubscriber.verifyFailure(IllegalStateException.class);
    }

    @Test
    public void cancelDataRacingWithDataShouldCompleteAndFailPublisherOnSubscribe() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        dataSubscriber.cancel();
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.takeError(), instanceOf(CancellationException.class));
    }

    @Test
    public void cancelDataAfterDataCompleteShouldIgnoreCancelAndDeliverPublisherOnComplete() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        dataSubscriber.cancel();
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeItems(), contains(one, two, last));
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void cancelDataBeforeDataCompleteShouldDeliverError() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onSubscribe(subscription);
        dataSubscriber.cancel();
        assertTrue(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void streamErrorAfterPublisherSubscribeShouldDeliverError() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onSubscribe(subscription);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(1);
        upstream.onNext(one);
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(payloadSubscriber.takeItems(), contains(one));
        assertThat(payloadSubscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void streamCompleteAfterPublisherSubscribeShouldDeliverComplete() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeItems(), contains(one, two, last));
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void streamCompleteBeforePublisherSubscribeShouldDeliverCompleteOnSubscribe() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        upstream.onComplete();
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void streamErrorBeforePublisherSubscribeShouldDeliverErrorOnSubscribe() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onSubscribe(subscription);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        assertThat(payloadSubscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void publisherSubscribeTwiceShouldFailSecondSubscriber() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(3);
        upstream.onNext(one, two, last);
        toSource(data.getPayload()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.takeError(), instanceOf(DuplicateSubscribeException.class));
        upstream.onComplete();
        assertThat(payloadSubscriber.takeItems(), contains(one, two, last));
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void publisherSubscribeAgainAfterCompletingInitialSubscriberShouldFailSecondSubscriber() {
        Publisher<Object> stream = upstream;
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Data::new));
        dataSubscriber.listen(op);
        upstream.onNext(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.meta(), equalTo(data.meta()));
        toSource(data.getPayload()).subscribe(payloadSubscriber);
        payloadSubscriber.request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(payloadSubscriber.takeItems(), contains(one, two, last));
        assertThat(payloadSubscriber.takeTerminal(), is(complete()));
        toSource(data.getPayload()).subscribe(dupePayloadSubscriber);
        assertThat(dupePayloadSubscriber.takeError(), instanceOf(DuplicateSubscribeException.class));
    }

    @Test
    public void packerThrowsShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Object> stream = from(metaData);
        Single<Data> op = stream.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>((metaData, payload) -> {
                    throw DELIBERATE_EXCEPTION;
                }));
        dataSubscriber.listen(op);
        dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
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
