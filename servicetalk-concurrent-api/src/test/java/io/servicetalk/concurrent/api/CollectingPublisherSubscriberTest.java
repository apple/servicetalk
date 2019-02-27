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

import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertNull;

public class CollectingPublisherSubscriberTest {

    private final CollectingPublisherSubscriber<String> subscriber = new CollectingPublisherSubscriber<>();
    private final TestPublisher<String> source = new TestPublisher.Builder<String>()
            .disableDemandCheck()
            .build();

    @Test
    public void testAssertItems() {
        source.subscribe(subscriber);
        assertThat(subscriber.items(), hasSize(0));

        source.onNext("a");
        assertThat(subscriber.items(), contains("a"));

        source.onNext("b");
        assertThat(subscriber.items(), contains("a", "b"));
    }

    @Test
    public void testSubscriptionReceived() {
        assertFalse(subscriber.subscriptionReceived());

        source.subscribe(subscriber);

        assertTrue(subscriber.subscriptionReceived());
    }

    @Test
    public void testComplete() {
        source.subscribe(subscriber);

        assertNull(subscriber.terminal());
        assertFalse(subscriber.isCompleted());
        assertFalse(subscriber.isTerminated());

        source.onComplete();

        assertThat(subscriber.terminal(), sameInstance(TerminalNotification.complete()));
        assertNull(subscriber.error());
        assertTrue(subscriber.isCompleted());
        assertTrue(subscriber.isTerminated());
    }

    @Test
    public void testError() {
        source.subscribe(subscriber);

        assertFalse(subscriber.isTerminated());
        assertFalse(subscriber.isErrored());
        assertNull(subscriber.error());

        final RuntimeException error = new RuntimeException("Outer", new IllegalStateException("Inner"));
        source.onError(error);

        assertNotNull(subscriber.terminal());
        assertThat(subscriber.terminal().cause(), sameInstance(error));
        assertThat(subscriber.error(), sameInstance(error));
        assertTrue(subscriber.isTerminated());
        assertTrue(subscriber.isErrored());
        assertFalse(subscriber.isCompleted());
    }
}
