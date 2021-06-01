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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeWhilePublisherTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void testWhile() {
        Publisher<String> p = publisher.takeWhile(s -> !s.equals("Hello3"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        subscriber.awaitOnComplete();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testWhileError() {
        Publisher<String> p = publisher.takeWhile(s -> !s.equals("Hello3"));
        toSource(p).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello1");
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is("Hello1"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testWhileComplete() {
        Publisher<String> p = publisher.takeWhile(s -> !s.equals("Hello3"));
        toSource(p).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext("Hello1");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(), is("Hello1"));
    }

    @Test
    void testSubCancelled() {
        Publisher<String> p = publisher.takeWhile(s -> !s.equals("Hello3"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("Hello1", "Hello2");
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }
}
