/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkipWhileTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void skipWhile() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        assertThat(subscriber.takeOnNext(2), contains("Hello2", "Hello3"));
        assertFalse(subscription.isCancelled());
    }

    @Test
    void skipWhileComplete() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2");
        publisher.onComplete();
        assertEquals("Hello2", subscriber.takeOnNext());
    }

    @Test
    void skipWhileCancelled() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("Hello1", "Hello2");
        assertEquals("Hello2", subscriber.takeOnNext());
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }
}
