/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


class PublisherCacheTest {
    private TestPublisher<Integer> testPublisher;
    private PublisherCache<String, Integer> publisherCache;
    private AtomicInteger upstreamSubscriptionCount;
    private AtomicBoolean isUpstreamUnsubsrcibed;
    private AtomicInteger upstreamCacheRequestCount;

    @BeforeEach
    public void setup() {
        this.testPublisher = new TestPublisher<>();
        this.upstreamSubscriptionCount = new AtomicInteger(0);
        this.isUpstreamUnsubsrcibed = new AtomicBoolean(false);
        this.upstreamCacheRequestCount = new AtomicInteger(0);
        this.publisherCache = PublisherCache.multicast((_ignore) -> {
            upstreamCacheRequestCount.incrementAndGet();
            return testPublisher.afterOnSubscribe(subscription ->
                    upstreamSubscriptionCount.incrementAndGet()
            ).afterFinally(() -> isUpstreamUnsubsrcibed.set(true));
        });
    }

    @Test
    public void testMultipleSubscribersReceiveCachedResults() {
        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        SourceAdapters.toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();

        // the first subscriber receives the initial event
        subscription1.request(1);
        testPublisher.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));

        // the second subscriber receives the cached event
        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        SourceAdapters.toSource(publisherCache.get("bar")).subscribe(subscriber2);
        final PublisherSource.Subscription subscription2 = subscriber2.awaitSubscription();

        subscription2.request(1);
        assertThat(subscriber2.takeOnNext(), is(1));

        // subscribe with the first request
        assertThat(upstreamSubscriptionCount.get(), is(1));

        // all subscribers receive all subsequent events
        subscription1.request(1);
        subscription2.request(1);
        testPublisher.onNext(2);

        assertThat(subscriber1.takeOnNext(), is(2));
        assertThat(subscriber2.takeOnNext(), is(2));

        // make sure we still only have subscribed once
        assertThat(upstreamSubscriptionCount.get(), is(1));
        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    // @Test
    // public void testDiscovererAccumulatesEvents() {
    //     final var subscriber1 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber1);
    //     final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();
    //
    //     // the first subscriber receives the initial event
    //     subscription1.request(4);
    //
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, Map.of())));
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of())));
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT3, Map.of())));
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT4, Map.of())));
    //
    //     // Subscriber 1 receives individual events
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, Map.of())));
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of())));
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT3, Map.of())));
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT4, Map.of())));
    //
    //     final var subscriber2 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber2);
    //     final PublisherSource.Subscription subscription2 = subscriber2.awaitSubscription();
    //
    //     // Subscriber 2 receives a batch of accumulated events
    //     subscription2.request(1);
    //     assertThat(subscriber2.takeOnNext(), containsInAnyOrder(
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, Map.of()),
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of()),
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT3, Map.of()),
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT4, Map.of())
    //     ));
    //
    //     assertThat(upstreamCacheRequestCount.get(), is(1));
    // }
    //
    // @Test
    // public void testDiscovererAccumulatesRemoveEvents() {
    //     final var subscriber1 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber1);
    //     final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();
    //
    //     // the first subscriber receives the initial event
    //     subscription1.request(4);
    //
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, Map.of())));
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of())));
    //     currentEventPublisher.get().onNext(List.of(new RemoveEvent(SERVICE, ENDPOINT1)));
    //
    //     // Subscriber 1 receives individual events
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, Map.of())));
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of())));
    //     assertThat(subscriber1.takeOnNext(), contains(new RemoveEvent(SERVICE, ENDPOINT1)));
    //
    //     final var subscriber2 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber2);
    //     final PublisherSource.Subscription subscription2 = subscriber2.awaitSubscription();
    //
    //     // Subscriber 2 receives a batch of accumulated events
    //     subscription2.request(1);
    //     assertThat(subscriber2.takeOnNext(), containsInAnyOrder(
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT2, Map.of())
    //     ));
    //
    //     assertThat(upstreamCacheRequestCount.get(), is(1));
    // }
    //
    // @Test
    // public void testDiscovererConvertsModifyEventsToAddEvents() {
    //     final var subscriber1 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber1);
    //     final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();
    //
    //     // the first subscriber receives the initial event
    //     subscription1.request(4);
    //
    //     final Map<String, Attribute> initialAttributes = Map.of();
    //     final Map<String, Attribute> updateAttributes = Map.of("attr1", new LongAttribute(1));
    //
    //     currentEventPublisher.get().onNext(List.of(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, initialAttributes)));
    //     currentEventPublisher.get().onNext(List.of(new UpdateEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, updateAttributes)));
    //
    //     // Subscriber 1 receives individual events
    //     assertThat(subscriber1.takeOnNext(), contains(new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, initialAttributes)));
    //     assertThat(subscriber1.takeOnNext(), contains(new UpdateEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, updateAttributes)));
    //
    //     final var subscriber2 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber2);
    //     final PublisherSource.Subscription subscription2 = subscriber2.awaitSubscription();
    //
    //     // Subscriber 2 receives a batch of accumulated events
    //     subscription2.request(1);
    //     assertThat(subscriber2.takeOnNext(), containsInAnyOrder(
    //             new AddEvent(SERVICE, new KubernetesEndpointEvent.Condition(true), ENDPOINT1, updateAttributes)
    //     ));
    //
    //     assertThat(upstreamCacheRequestCount.get(), is(1));
    // }
    //
    // @Test
    // public void testUnSubscribeUpstreamAndInvalidateCacheWhenEmpty() {
    //     final var subscriber1 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber1);
    //     final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();
    //
    //     assertThat(upstreamSubscriptionCount.get(), is(1));
    //
    //     final var subscriber2 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     SourceAdapters.toSource(discoveryService.discover(SERVICE)).subscribe(subscriber2);
    //     final PublisherSource.Subscription subscription2 = subscriber2.awaitSubscription();
    //
    //     assertThat(upstreamSubscriptionCount.get(), is(1));
    //
    //     subscription1.cancel();
    //     assertThat(isUpstreamUnsubsrcibed.get(), is(false));
    //
    //     subscription2.cancel();
    //     assertThat(isUpstreamUnsubsrcibed.get(), is(true));
    //
    //     assertThat(upstreamCacheRequestCount.get(), is(1));
    // }
    //
    // @Test
    // public void testErrorFromUpstreamInvalidatesCacheEntryAndRequestsANewStream() {
    //     final var subscriber1 = new TestPublisherSubscriber<Collection<KubernetesEndpointEvent>>();
    //     // use a stupid "always" retry policy to force the situation
    //     final var discovery = discoveryService.discover(SERVICE).retry((i, cause) -> true);
    //     SourceAdapters.toSource(discovery).subscribe(subscriber1);
    //     final PublisherSource.Subscription subscription1 = subscriber1.awaitSubscription();
    //
    //     subscription1.request(2);
    //     currentEventPublisher.get().onError(new Exception("bad stuff happened"));
    //
    //     assertThat(upstreamCacheRequestCount.get(), is(2));
    // }
}