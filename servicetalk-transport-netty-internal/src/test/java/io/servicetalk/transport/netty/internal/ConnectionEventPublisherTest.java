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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.transport.netty.internal.NettyConnectionContext.ConnectionEvent;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.transport.netty.internal.NettyConnectionContext.ConnectionEvent.ReadComplete;
import static java.lang.Long.MAX_VALUE;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ConnectionEventPublisherTest {

    @Rule
    public final MockedSubscriberRule<ConnectionEvent> subscriberRule = new MockedSubscriberRule<>();
    @Rule
    public final MockedSubscriberRule<ConnectionEvent> subscriber2Rule = new MockedSubscriberRule<>();
    @Rule
    public final MockedSubscriberRule<ConnectionEvent> subscriber3Rule = new MockedSubscriberRule<>();

    private ConnectionEventPublisher publisher;

    @Before
    public void setUp() {
        EmbeddedChannel channel = new EmbeddedChannel();
        publisher = new ConnectionEventPublisher(channel.eventLoop());
    }

    @Test
    public void delayedSubscribeEmitsPreviousEvent() {
        publisher.publishReadComplete();
        subscriberRule.subscribe(publisher).request(1).verifyItems(ReadComplete);
    }

    @Test
    public void highDemandEmitsAllEvents() {
        subscriberRule.subscribe(publisher).request(MAX_VALUE).verifyNoEmissions();
        // Enough demand emits all events
        publisher.publishReadComplete();
        subscriberRule.verifyItems(ReadComplete);
        publisher.publishReadComplete();
        subscriberRule.verifyItems(sub -> verify(sub, times(2)), ReadComplete);
        publisher.publishReadComplete();
        subscriberRule.verifyItems(sub -> verify(sub, times(3)), ReadComplete);

        publisher.close();
        subscriberRule.verifySuccess();
    }

    @Test
    public void lowDemandDropsDuplicateEvents() {
        subscriberRule.subscribe(publisher).request(1).verifyNoEmissions();
        publisher.publishReadComplete();
        subscriberRule.verifyItems(ReadComplete);
        publisher.publishReadComplete();
        publisher.publishReadComplete(); // should be dropped
        subscriberRule.verifyNoEmissions()
                .request(1)
                .verifyItems(sub -> verify(sub, times(2)), ReadComplete);
    }

    @Test
    public void delayedDemandEmitsEvents() {
        publisher.publishReadComplete();
        subscriberRule.subscribe(publisher).verifyNoEmissions().request(2).verifyItems(ReadComplete);
        publisher.publishReadComplete();
        subscriberRule.verifyItems(sub -> verify(sub, times(2)), ReadComplete);
    }

    @Test
    public void delayedDemandEmitsEventsAndThenDrops() {
        publisher.publishReadComplete();
        subscriberRule.subscribe(publisher).verifyNoEmissions().request(1).verifyItems(ReadComplete);
        publisher.publishReadComplete();
        subscriberRule.verifyNoEmissions();
    }

    @Test
    public void multipleOverlappingSubscribers() {
        subscriberRule.subscribe(publisher);
        subscriber2Rule.subscribe(publisher);
        subscriber3Rule.subscribe(publisher);

        publisher.publishReadComplete();

        subscriberRule.request(2).verifyItems(ReadComplete);
        subscriber2Rule.request(2).verifyItems(ReadComplete);
        subscriber3Rule.request(2).verifyItems(ReadComplete);

        publisher.publishReadComplete();
        subscriberRule.verifyItems(sub -> verify(sub, times(2)), ReadComplete);
        subscriber2Rule.verifyItems(sub -> verify(sub, times(2)), ReadComplete);
        subscriber3Rule.verifyItems(sub -> verify(sub, times(2)), ReadComplete);
    }

    @Test
    public void multipleSequentialSubscribers() {
        subscriberRule.subscribe(publisher);
        publisher.publishReadComplete();

        subscriberRule.request(2).verifyItems(ReadComplete);
        subscriberRule.cancel();

        subscriber2Rule.subscribe(publisher).request(1).verifyItems(ReadComplete);
        subscriberRule.verifyNoEmissions();
    }
}
