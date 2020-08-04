/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy.Builder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.ExecutorRule.withTestExecutor;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Long.MAX_VALUE;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class DefaultServiceDiscoveryRetryStrategyTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<TestExecutor> executorRule = withTestExecutor();

    @Test
    public void errorWithNoAddresses() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();
        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);
        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    public void newAddressPostRetry() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();

        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);
        final DefaultServiceDiscovererEvent<String> evt2 = new DefaultServiceDiscovererEvent<>("addr2", true);
        sdEvents.onNext(evt2);

        assertThat("Unexpected event received", state.subscriber.takeItems(),
                containsInAnyOrder(flipAvailable(evt1), evt2));
    }

    @Test
    public void overlapAddressPostRetry() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();

        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);

        sdEvents.onNext(evt1); // previously existing, should not be emitted
        verifyNoEventsReceived(state);

        final DefaultServiceDiscovererEvent<String> evt3 = new DefaultServiceDiscovererEvent<>("addr3", true);
        sdEvents.onNext(evt3); // threshold breach, should evict addr2

        assertThat("Unexpected event received", state.subscriber.takeItems(),
                containsInAnyOrder(flipAvailable(evt2), evt3));
    }

    @Test
    public void errorWhileRetaining() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();

        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);

        sdEvents.onNext(evt1); // previously existing, should not be emitted
        verifyNoEventsReceived(state);

        sdEvents = triggerRetry(state, sdEvents); // error while retaining

        final DefaultServiceDiscovererEvent<String> evt3 = new DefaultServiceDiscovererEvent<>("addr3", true);
        sdEvents.onNext(evt3);

        // T1: addr1, addr2 active
        // T2: error => 75% of 2 addresses => retain till receive 2 addresses
        // T3 addr1 active, addr2 retained
        // T4: error => 75% of 1 active addresses => retain till receive 1 addresses
        // T5: addr3 active, addr1 & addr2 retained => evict addr1 and addr2
        assertThat("Unexpected event received", state.subscriber.takeItems(),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2), evt3));
    }

    @Test
    public void addRemoveBeforeRetry() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        sdEvents.onNext(flipAvailable(evt1));
        assertThat("Unexpected event received", state.subscriber.takeItems(), contains(flipAvailable(evt1)));

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    public void removeAfterRetry() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(evt1); // pre-existing, no new event
        sdEvents.onNext(flipAvailable(evt1));

        assertThat("Unexpected event received", state.subscriber.takeItems(),
                contains(flipAvailable(evt1)));
    }

    @Test
    public void removeAfterRetryWithRetain() throws Exception {
        State state = new State(75);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(evt1); // pre-existing, no new event
        sdEvents.onNext(flipAvailable(evt1));

        assertThat("Unexpected event received", state.subscriber.takeItems(),
                contains(flipAvailable(evt1)));

        sdEvents.onNext(evt2);
        verifyNoEventsReceived(state);
    }

    @Test
    public void noRetainActiveAddresses() throws Exception {
        State state = new State(0);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(evt1);
        assertThat("Unexpected event received", state.subscriber.takeItems(),
                contains(flipAvailable(evt2)));

        sdEvents.onNext(evt2);
        assertThat("Unexpected event received", state.subscriber.takeItems(),
                contains(evt2));
    }

    @Test
    public void noRetainErrorWithNoAddresses() throws Exception {
        State state = new State(0);
        TestPublisher<ServiceDiscovererEvent<String>> sdEvents = state.pubs.take();

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    private void verifyNoEventsReceived(final State state) {
        assertThat("Unexpected event received", state.subscriber.takeItems(), hasSize(0));
    }

    private TestPublisher<ServiceDiscovererEvent<String>> triggerRetry(final State state,
            final TestPublisher<ServiceDiscovererEvent<String>> sdEvents) throws Exception {
        sdEvents.onError(DELIBERATE_EXCEPTION);
        executorRule.executor().advanceTimeBy(1, MINUTES);
        return state.pubs.take();
    }

    private DefaultServiceDiscovererEvent<String> sendUpAndVerifyReceive(final State state, final String addr,
            final TestPublisher<ServiceDiscovererEvent<String>> sdEvents) {
        final DefaultServiceDiscovererEvent<String> evt = new DefaultServiceDiscovererEvent<>(addr, true);
        sdEvents.onNext(evt);
        final List<ServiceDiscovererEvent<String>> received = state.subscriber.takeItems();
        assertThat("Event not received.", received, hasSize(1));
        assertThat("Unexpected event received.", received.get(0).address(), is(addr));
        assertThat("Unexpected event received.", received.get(0).isAvailable(), is(true));
        return evt;
    }

    private static ServiceDiscovererEvent<String> flipAvailable(final ServiceDiscovererEvent<String> evt) {
        return new DefaultServiceDiscovererEvent<>(evt.address(), !evt.isAvailable());
    }

    private final class State {
        final LinkedBlockingQueue<TestPublisher<ServiceDiscovererEvent<String>>> pubs;
        final TestPublisherSubscriber<ServiceDiscovererEvent<String>> subscriber;

        State(final int retainAddressesTillSuccessPercentage) {
            ServiceDiscoveryRetryStrategy<String, ServiceDiscovererEvent<String>> strategy =
                    Builder.<String>withDefaults(executorRule.executor(), ofSeconds(1))
                            .retainAddressesTillSuccess(retainAddressesTillSuccessPercentage)
                            .build();
            pubs = new LinkedBlockingQueue<>();
            subscriber = new TestPublisherSubscriber<>();
            toSource(strategy.apply(defer(() -> {
                final TestPublisher<ServiceDiscovererEvent<String>> pub = new TestPublisher<>();
                pubs.add(pub);
                return pub;
            }))).subscribe(subscriber);
            subscriber.request(MAX_VALUE);
        }
    }
}
