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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestPublisherTest {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private final TestPublisherSubscriber<String> subscriber1 = TestPublisherSubscriber.newTestPublisherSubscriber();
    private final TestPublisherSubscriber<String> subscriber2 = TestPublisherSubscriber.newTestPublisherSubscriber();

    @Test
    public void testNonResubscribeablePublisher() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .singleSubscriber()
                .build();

        source.subscribe(subscriber1);
        assertTrue(subscriber1.isSubscribed());

        source.onComplete();
        assertTrue(subscriber1.isCompleted());

        source.subscribe(subscriber2);
        expected.expect(RuntimeException.class);
        expected.expectMessage("Unexpected exception(s) encountered");
        expected.expectCause(allOf(instanceOf(IllegalStateException.class), hasProperty("message",
                startsWith("Duplicate subscriber"))));
        source.onComplete();
    }

    @Test
    public void testSequentialSubscribePublisher() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .build();

        source.subscribe(subscriber1);
        source.onComplete();
        assertTrue(subscriber1.isCompleted());

        source.subscribe(subscriber2);
        source.onComplete();
        assertTrue(subscriber2.isCompleted());
    }

    @Test
    public void testConcurrentSubscribePublisher() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .concurrentSubscribers()
                .build();

        source.subscribe(subscriber1);

        source.subscribe(subscriber2);

        source.onComplete();
        assertTrue(subscriber1.isCompleted());
        assertTrue(subscriber2.isCompleted());
    }

    @Test
    public void testFanOut() {
        final AutoOnSubscribeSubscriberFunction<Integer> autoOnSubscribe = new AutoOnSubscribeSubscriberFunction<>();
        TestPublisher<Integer> source = new TestPublisher.Builder<Integer>()
                .autoOnSubscribe(autoOnSubscribe)
                .concurrentSubscribers()
                .build();

        FanOut fanOut = new FanOut(2);
        fanOut.consume(source);

        List<TestSubscription> subscriptions = autoOnSubscribe.subscriptions();

        assertEquals(1, subscriptions.get(0).requested());
        assertEquals(1, subscriptions.get(1).requested());

        source.onNext(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(10, subscriptions.get(0).requested());
        assertEquals(10, subscriptions.get(1).requested());
        source.onComplete();

        Map<Integer, Integer> counts = fanOut.getCounts();
        assertEquals(4, (int) counts.get(0));
        assertEquals(5, (int) counts.get(1));
    }

    @Test
    public void testFanOut2() {
        ConcurrentPublisherSubscriberFunction<Integer> concurrentPublisherSubscriberFunction =
                new ConcurrentPublisherSubscriberFunction<>();
        final AutoOnSubscribeSubscriberFunction<Integer> autoOnSubscribe = new AutoOnSubscribeSubscriberFunction<>();
        TestPublisher<Integer> source = new TestPublisher.Builder<Integer>()
                .autoOnSubscribe(autoOnSubscribe)
                .concurrentSubscribers(concurrentPublisherSubscriberFunction)
                .build();

        FanOut fanOut = new FanOut(2);
        fanOut.consume(source);
        assertEquals(2, concurrentPublisherSubscriberFunction.subscribers().size());

        List<TestSubscription> subscriptions = autoOnSubscribe.subscriptions();

        assertEquals(1, subscriptions.get(0).requested());
        assertEquals(1, subscriptions.get(1).requested());

        source.onNext(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(10, subscriptions.get(0).requested());
        assertEquals(10, subscriptions.get(1).requested());
        source.onComplete();

        Map<Integer, Integer> counts = fanOut.getCounts();
        assertEquals(4, (int) counts.get(0));
        assertEquals(5, (int) counts.get(1));
    }

    @Test
    public void testFanOut3() {
        ConcurrentPublisherSubscriberFunction<Integer> concurrentPublisherSubscriberFunction =
                new ConcurrentPublisherSubscriberFunction<>();
        final AutoOnSubscribeSubscriberFunction<Integer> autoOnSubscribe = new AutoOnSubscribeSubscriberFunction<>();
        TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().build(
                new DemandCheckingSubscriberFunction<Integer>()
                        .andThen(autoOnSubscribe)
                        .andThen(concurrentPublisherSubscriberFunction)
        );

        FanOut fanOut = new FanOut(2);
        fanOut.consume(source);
        assertEquals(2, concurrentPublisherSubscriberFunction.subscribers().size());

        List<TestSubscription> subscriptions = autoOnSubscribe.subscriptions();

        assertEquals(1, subscriptions.get(0).requested());
        assertEquals(1, subscriptions.get(1).requested());

        source.onNext(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(10, subscriptions.get(0).requested());
        assertEquals(10, subscriptions.get(1).requested());
        source.onComplete();

        Map<Integer, Integer> counts = fanOut.getCounts();
        assertEquals(4, (int) counts.get(0));
        assertEquals(5, (int) counts.get(1));
    }

    @Test
    public void testDemandNoRequest() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .build();
        source.subscribe(subscriber1);

        expected.expect(IllegalStateException.class);
        expected.expectMessage("Demand check failure: not enough demand to send a");
        source.onNext("a");
    }

    @Test
    public void testInsufficientDemand() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .build();
        source.subscribe(subscriber1);

        subscriber1.request(2);
        source.onNext("a", "b");

        assertThat(subscriber1.items(), contains("a", "b"));

        expected.expect(IllegalStateException.class);
        expected.expectMessage("Demand check failure: not enough demand to send c");
        source.onNext("c");
    }

    @Test
    public void testRequestMaxMultiple() {
        TestPublisher<String> source = new TestPublisher.Builder<String>()
                .build();
        source.subscribe(subscriber1);

        subscriber1.request(MAX_VALUE);
        subscriber1.request(MAX_VALUE);
        source.onNext("a");

        assertThat(subscriber1.items(), contains("a"));
    }

    private static class FanOut {

        private final int fanOut;
        private final Map<Integer, Integer> counts = new ConcurrentHashMap<>();

        FanOut(final int fanOut) {
            this.fanOut = fanOut;
        }

        public void consume(final Publisher<Integer> publisher) {
            final PublisherSource<Integer> source = toSource(publisher);
            for (int i = 0; i < fanOut; ++i) {
                source.subscribe(new MySubscriber(fanOut, i));
            }
        }

        public Map<Integer, Integer> getCounts() {
            return new HashMap<>(counts);
        }

        private final class MySubscriber implements Subscriber<Integer> {

            private final int modulo;
            private final int offset;
            private int count;
            private Subscription subscription;

            private MySubscriber(final int modulo, final int offset) {
                this.modulo = modulo;
                this.offset = offset;
            }

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer i) {
                assert i != null;
                if (i % modulo == offset) {
                    count++;
                }
                subscription.request(1);
            }

            @Override
            public void onError(final Throwable t) {
                counts.put(offset, count);
            }

            @Override
            public void onComplete() {
                counts.put(offset, count);
            }

            @Override
            public String toString() {
                return this.getClass().getSimpleName() + offset;
            }
        }
    }
}
