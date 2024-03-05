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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.PublisherCache.MulticastStrategy;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.PublisherCache.MulticastStrategy.wrapMulticast;
import static io.servicetalk.concurrent.api.PublisherCache.MulticastStrategy.wrapReplay;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PublisherCacheTest {
    private TestPublisher<Integer> testPublisher;
    private AtomicInteger upstreamSubscriptionCount;
    private AtomicBoolean isUpstreamUnsubsrcibed;
    private AtomicInteger upstreamCacheRequestCount;

    @BeforeEach
    public void setup() {
        this.testPublisher = new TestPublisher<>();
        this.upstreamSubscriptionCount = new AtomicInteger(0);
        this.isUpstreamUnsubsrcibed = new AtomicBoolean(false);
        this.upstreamCacheRequestCount = new AtomicInteger(0);
    }

    private PublisherCache<String, Integer> publisherCache(final MulticastStrategy<Integer> strategy) {
        return new PublisherCache<>((_ignore) -> {
            upstreamCacheRequestCount.incrementAndGet();
            return testPublisher.afterOnSubscribe(subscription ->
                    upstreamSubscriptionCount.incrementAndGet()
            ).afterFinally(() -> isUpstreamUnsubsrcibed.set(true));
        }, strategy);
    }

    @Test
    public void multipleSubscribersToSameKeyReceiveMulticastEvents() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(wrapMulticast());

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        // the first subscriber receives the initial event
        subscription1.request(1);
        testPublisher.onNext(1);
        assertThat(subscriber1.takeOnNext(), is(1));

        // the second subscriber receives the cached publisher
        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber2);
        final Subscription subscription2 = subscriber2.awaitSubscription();

        // all subscribers receive all subsequent events
        subscription1.request(1);
        subscription2.request(1);
        testPublisher.onNext(2);
        assertThat(subscriber1.takeOnNext(), is(2));
        assertThat(subscriber2.takeOnNext(), is(2));

        // subscribe with the first request
        assertThat(upstreamSubscriptionCount.get(), is(1));

        // make sure we still only have subscribed once
        assertThat(upstreamSubscriptionCount.get(), is(1));
        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    @Test
    public void minSubscribersMulticastPolicySubscribesUpstreamOnce() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(wrapMulticast());

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber2);
        final Subscription subscription2 = subscriber2.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        subscription1.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(false));

        subscription2.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(true));

        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    @Test
    public void unSubscribeUpstreamAndInvalidateCacheWhenEmpty() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(wrapMulticast());

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber2);
        final Subscription subscription2 = subscriber2.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        subscription1.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(false));

        subscription2.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(true));

        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    @Test
    public void cacheSubscriptionAndUnsubscriptionWithMulticastMinSubscribers() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(wrapMulticast(2));

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(0));

        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber2);
        final Subscription subscription2 = subscriber2.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        subscription1.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(false));

        subscription2.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(true));

        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    @Test
    public void cacheSubscriptionAndUnsubscriptionWithReplay() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(
                wrapReplay(ReplayStrategies.<Integer>historyBuilder(1)
                        .cancelUpstream(true)
                        .minSubscribers(1)
                        .build()));

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        subscription1.request(3);
        testPublisher.onNext(1, 2, 3);
        assertThat(subscriber1.takeOnNext(3), is(Arrays.asList(1, 2, 3)));

        final TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(publisherCache.get("foo")).subscribe(subscriber2);
        final Subscription subscription2 = subscriber2.awaitSubscription();

        assertThat(upstreamSubscriptionCount.get(), is(1));

        subscription2.request(1);
        assertThat(subscriber2.takeOnNext(), is(3));

        subscription1.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(false));

        subscription2.cancel();
        assertThat(isUpstreamUnsubsrcibed.get(), is(true));

        assertThat(upstreamCacheRequestCount.get(), is(1));
    }

    @Test
    public void testErrorFromUpstreamInvalidatesCacheEntryAndRequestsANewStream() {
        final PublisherCache<String, Integer> publisherCache = publisherCache(wrapMulticast());

        final TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        // use an "always" retry policy to force the situation
        final Publisher<Integer> discovery = publisherCache.get("foo").retry((i, cause) -> true);
        toSource(discovery).subscribe(subscriber1);
        final Subscription subscription1 = subscriber1.awaitSubscription();

        subscription1.request(2);
        testPublisher.onError(new Exception("bad stuff happened"));

        assertThat(upstreamCacheRequestCount.get(), is(2));
    }
}
