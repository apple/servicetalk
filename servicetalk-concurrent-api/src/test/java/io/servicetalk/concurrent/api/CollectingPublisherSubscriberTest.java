/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectingPublisherSubscriberTest {

    private final CollectingPublisherSubscriber<String> subscriber = new CollectingPublisherSubscriber<>();
    private final TestPublisher<String> source = new TestPublisher.Builder<String>()
            .disableDemandCheck()
            .build();

    @Test
    void testAssertItems() {
        source.subscribe(subscriber);
        assertThat(subscriber.items(), hasSize(0));

        source.onNext("a");
        assertThat(subscriber.items(), contains("a"));

        source.onNext("b");
        assertThat(subscriber.items(), contains("a", "b"));
    }

    @Test
    void testSubscriptionReceived() {
        assertFalse(subscriber.subscriptionReceived());

        source.subscribe(subscriber);

        assertTrue(subscriber.subscriptionReceived());
    }

    @Test
    void testComplete() {
        source.subscribe(subscriber);

        assertNull(subscriber.terminal());
        assertFalse(subscriber.isCompleted());
        assertThat(subscriber.terminal(), nullValue());

        source.onComplete();

        assertNull(subscriber.error());
        assertThat(subscriber.terminal(), is(complete()));
    }

    @Test
    void testError() {
        source.subscribe(subscriber);

        assertThat(subscriber.terminal(), nullValue());
        assertFalse(subscriber.isErrored());
        assertNull(subscriber.error());

        final RuntimeException error = new RuntimeException("Outer", new IllegalStateException("Inner"));
        source.onError(error);

        assertNotNull(subscriber.terminal());
        assertThat(subscriber.terminal().cause(), sameInstance(error));
        assertThat(subscriber.error(), sameInstance(error));
        assertThat(subscriber.terminal(), notNullValue());
        assertTrue(subscriber.isErrored());
        assertFalse(subscriber.isCompleted());
    }
}
