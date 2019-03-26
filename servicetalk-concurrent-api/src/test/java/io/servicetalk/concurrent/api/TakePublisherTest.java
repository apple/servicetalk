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
package io.servicetalk.concurrent.api;

import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TakePublisherTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void testEnoughRequests() {
        Publisher<String> p = publisher.takeAtMost(2);
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(3);
        publisher.onNext("Hello1", "Hello2");
        assertThat(subscriber.takeItems(), contains("Hello1", "Hello2"));
        assertThat(subscriber.takeTerminal(), is(complete()));
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testTakeError() {
        Publisher<String> p = publisher.takeAtMost(2);
        toSource(p).subscribe(subscriber);
        subscriber.request(2);
        publisher.onNext("Hello1");
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains("Hello1"));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testTakeComplete() {
        Publisher<String> p = publisher.takeAtMost(2);
        toSource(p).subscribe(subscriber);
        subscriber.request(2);
        publisher.onNext("Hello1");
        publisher.onComplete();
        assertThat(subscriber.takeItems(), contains("Hello1"));
    }

    @Test
    public void testSubCancelled() {
        Publisher<String> p = publisher.takeAtMost(3);
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(3);
        publisher.onNext("Hello1", "Hello2");
        assertThat(subscriber.takeItems(), contains("Hello1", "Hello2"));
        subscriber.cancel();
        assertTrue(subscription.isCancelled());
    }
}
