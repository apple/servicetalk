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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CancellationException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class SpliceFlatStreamToMetaSingleTest {

    private final MetaData metaData = new MetaData("foo");
    private final Payload one = new Payload();
    private final Payload two = new Payload();
    private final LastPayload last = new LastPayload();
    private final Data data = new Data(metaData, from(one, two, last));

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final PublisherRule<Object> upstream = new PublisherRule<>();

    @Rule
    public final MockedSubscriberRule<Payload> payloadSubscriber = new MockedSubscriberRule<>();

    @Rule
    public final MockedSubscriberRule<Payload> dupePayloadSubscriber = new MockedSubscriberRule<>();

    @Rule
    public final MockedSingleListenerRule<Data> dataSubscriber = new MockedSingleListenerRule<>();

    @Test
    public void streamWithHeaderAndPayloadShouldProduceDataWithEmbeddedPayload() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(2);
        upstream.sendItems(one, last);
        upstream.complete();
        payloadSubscriber.verifySuccess(one, last);
    }

    @Test
    public void streamWithHeaderAndEmptyPayloadShouldCompleteOnPublisherOnSubscribe()
            throws Exception {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        upstream.complete();
        assertThat(data.getPayload().toFuture().get(), empty());
    }

    @Test
    public void emptyStreamShouldCompleteDataWithError() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.complete();
        dataSubscriber.verifyFailure(IllegalStateException.class);
    }

    @Test
    public void cancelDataRacingWithDataShouldCompleteAndFailPublisherOnSubscribe() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        dataSubscriber.cancel();
        upstream.sendItemsNoVerify(metaData); // noverify -> send regardless of cancel to simulate race
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.verifyFailure(CancellationException.class);
    }

    @Test
    public void cancelDataAfterDataCompleteShouldIgnoreCancelAndDeliverPublisherOnComplete() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        dataSubscriber.cancel();
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(3);
        upstream.sendItems(one, two, last);
        upstream.complete();
        payloadSubscriber.verifySuccess(one, two, last);
    }

    @Test
    public void cancelDataBeforeDataCompleteShouldDeliverError() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        dataSubscriber.cancel();
        upstream.fail(true, DELIBERATE_EXCEPTION);
        dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void streamErrorAfterPublisherSubscribeShouldDeliverError() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(1);
        upstream.sendItems(one);
        upstream.fail(false, DELIBERATE_EXCEPTION);
        payloadSubscriber.verifyItems(one);
        payloadSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void streamCompleteAfterPublisherSubscribeShouldDeliverComplete() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(3);
        upstream.sendItems(one, two, last);
        upstream.complete();
        payloadSubscriber.verifySuccess(one, two, last);
    }

    @Test
    public void streamCompleteBeforePublisherSubscribeShouldDeliverCompleteOnSubscribe() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        upstream.complete();
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.verifySuccess();
    }

    @Test
    public void streamErrorBeforePublisherSubscribeShouldDeliverErrorOnSubscribe() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        upstream.fail(false, DELIBERATE_EXCEPTION);
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void publisherSubscribeTwiceShouldFailSecondSubscriber() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(3);
        upstream.sendItems(one, two, last);
        dupePayloadSubscriber.subscribe(data.getPayload());
        dupePayloadSubscriber.verifyFailure(DuplicateSubscribeException.class);
        upstream.complete();
        payloadSubscriber.verifySuccess(one, two, last);
    }

    @Test
    public void publisherSubscribeAgainAfterCompletingInitialSubscriberShouldFailSecondSubscriber() {
        Publisher<Object> stream = upstream.getPublisher();
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, Data::new);
        dataSubscriber.listen(op);
        upstream.sendItems(metaData);
        Data data = dataSubscriber.verifySuccessAndReturn(Data.class);
        assertThat(data.getMeta(), equalTo(data.getMeta()));
        payloadSubscriber.subscribe(data.getPayload());
        payloadSubscriber.request(3);
        upstream.sendItems(one, two, last);
        upstream.complete();
        payloadSubscriber.verifySuccess(one, two, last);
        dupePayloadSubscriber.subscribe(data.getPayload());
        dupePayloadSubscriber.verifyFailure(DuplicateSubscribeException.class);
    }

    @Test
    public void packerThrowsShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Object> stream = just(metaData);
        SpliceFlatStreamToMetaSingle<Data, MetaData, Payload> op = new SpliceFlatStreamToMetaSingle<>(
                stream, (metaData, payload) -> {
                    throw DELIBERATE_EXCEPTION;
                });
        dataSubscriber.listen(op);
        dataSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    private static class MetaData {
        private final String meta;

        MetaData(String meta) {
            this.meta = meta;
        }

        String getMeta() {
            return meta;
        }
    }

    private static final class Data extends MetaData {
        private final Publisher<Payload> payload;

        Data(MetaData metaData, Publisher<Payload> payload) {
            super(metaData.getMeta());
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
