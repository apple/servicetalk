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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class SpScPublisherProcessorTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private SpScPublisherProcessor<Integer> publisher = new SpScPublisherProcessor<>(4);

    @Test
    public void signalsDeliveredSynchronouslyIfSufficientDemand() {
        publisher = new SpScPublisherProcessor<>(4);
        publisher.subscribe(subscriber);
        subscriber.request(4);
        publisher.sendOnNext(1);
        assertEquals(singletonList(1), subscriber.takeItems());
        publisher.sendOnNext(2);
        assertEquals(singletonList(2), subscriber.takeItems());
        publisher.sendOnNext(3);
        assertEquals(singletonList(3), subscriber.takeItems());
        publisher.sendOnNext(4);
        assertEquals(singletonList(4), subscriber.takeItems());
        publisher.sendOnComplete();
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void onNextQueueCanHoldMaxSignals() {
        publisher = new SpScPublisherProcessor<>(4);
        publisher.subscribe(subscriber);
        publisher.sendOnNext(1);
        publisher.sendOnNext(2);
        publisher.sendOnNext(3);
        publisher.sendOnNext(4);
        publisher.sendOnComplete();
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(4);
        assertEquals(asList(1, 2, 3, 4), subscriber.takeItems());
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void completeDeliveredWithExcessDemand() {
        publisher = new SpScPublisherProcessor<>(4);
        publisher.subscribe(subscriber);
        subscriber.request(4);
        publisher.sendOnNext(1);
        publisher.sendOnComplete();
        assertEquals(singletonList(1), subscriber.takeItems());
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void errorDeliveredWithExcessDemand() {
        publisher = new SpScPublisherProcessor<>(4);
        publisher.subscribe(subscriber);
        subscriber.request(4);
        publisher.sendOnNext(1);
        publisher.sendOnError(DELIBERATE_EXCEPTION);
        assertEquals(singletonList(1), subscriber.takeItems());
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void onNextQueueOverflowTerminatesAndThrows() {
        publisher = new SpScPublisherProcessor<>(1);
        publisher.subscribe(subscriber);
        publisher.sendOnNext(1);
        assertThat(subscriber.takeItems(), is(empty()));
        QueueFullException expectedException = null;
        try {
            publisher.sendOnNext(2);
            fail();
        } catch (QueueFullException e) {
            expectedException = e;
        }

        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(1);
        assertEquals(singletonList(1), subscriber.takeItems());
        assertThat(subscriber.takeError(), sameInstance(expectedException));
    }

    @Test
    public void onNextThrows() {
        toSource(publisher.whenOnNext(i -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.request(1);
        publisher.sendOnNext(1);
        publisher.sendOnNext(2);
        publisher.sendOnComplete();

        assertEquals(singletonList(1), subscriber.takeItems());
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void invalidRequestN() {
        publisher.subscribe(subscriber);
        subscriber.request(-1);
        assertThat(subscriber.takeItems(), is(empty()));
        assertThat(subscriber.takeError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void cancelStopsOnNextDelivery() {
        publisher.subscribe(subscriber);
        subscriber.request(4);
        publisher.sendOnNext(1);
        publisher.sendOnNext(2);
        subscriber.cancel();
        publisher.sendOnNext(3);
        publisher.sendOnComplete();
        assertEquals(asList(1, 2), subscriber.takeItems());
        assertFalse(subscriber.isTerminated());
    }
}
