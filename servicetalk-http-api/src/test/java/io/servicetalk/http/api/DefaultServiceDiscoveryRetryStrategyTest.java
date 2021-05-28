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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy.Builder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.ExecutorRule.withTestExecutor;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Long.MAX_VALUE;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class DefaultServiceDiscoveryRetryStrategyTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<TestExecutor> executorRule = withTestExecutor();

    @Test
    public void errorWithNoAddresses() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);
        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    public void newAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);
        final DefaultServiceDiscovererEvent<String> evt2 = new DefaultServiceDiscovererEvent<>("addr2", true);
        sdEvents.onNext(singletonList(evt2));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), containsInAnyOrder(flipAvailable(evt1), evt2));
    }

    @Test
    public void overlapAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt3 = sendUpAndVerifyReceive(state, "addr3", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);

        final DefaultServiceDiscovererEvent<String> evt4 = new DefaultServiceDiscovererEvent<>("addr4", true);
        sdEvents.onNext(asList(evt2, evt4));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(evt4, flipAvailable(evt1), flipAvailable(evt3)));
    }

    @Test
    public void sameAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final String addr = "addr1";
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, addr, sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        final DefaultServiceDiscovererEvent<String> evt1Un = new DefaultServiceDiscovererEvent<>(addr, false);
        sdEvents.onNext(asList(evt1, evt1Un, evt1));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(evt1Un, evt1));
    }

    @Test
    public void addRemoveBeforeRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        sdEvents.onNext(singletonList(flipAvailable(evt1)));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(flipAvailable(evt1)));

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    public void removeAfterRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(asList(evt1, flipAvailable(evt1)));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(flipAvailable(evt1)));
    }

    @Test
    public void addAndRemovePostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(asList(evt1, flipAvailable(evt1)));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2)));
    }

    @Test
    public void noRetainActiveAddresses() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        triggerRetry(state, sdEvents);
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2)));
    }

    @Test
    public void noRetainErrorWithNoAddresses() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    public void noRetainActiveAddressesTwoSequentialErrors() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final DefaultServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final DefaultServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2)));

        triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);
    }

    private void verifyNoEventsReceived(final State state) {
        assertThat("Unexpected event received", state.subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
    }

    private TestPublisher<Collection<ServiceDiscovererEvent<String>>> triggerRetry(
            final State state, final TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents)
            throws Exception {
        sdEvents.onError(DELIBERATE_EXCEPTION);
        executorRule.executor().advanceTimeBy(1, MINUTES);
        return state.pubs.take();
    }

    private DefaultServiceDiscovererEvent<String> sendUpAndVerifyReceive(
            final State state, final String addr,
            final TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents) {
        final DefaultServiceDiscovererEvent<String> evt = new DefaultServiceDiscovererEvent<>(addr, true);
        sdEvents.onNext(singletonList(evt));
        final Collection<ServiceDiscovererEvent<String>> received = state.subscriber.takeOnNext(1)
                .stream()
                .flatMap(Collection::stream)
                .collect(toList());
        assertThat("Event not received.", received, hasSize(1));
        assertThat("Unexpected event received.", received.iterator().next().address(), is(addr));
        assertThat("Unexpected event received.", received.iterator().next().isAvailable(), is(true));
        return evt;
    }

    private static ServiceDiscovererEvent<String> flipAvailable(final ServiceDiscovererEvent<String> evt) {
        return new DefaultServiceDiscovererEvent<>(evt.address(), !evt.isAvailable());
    }

    private final class State {
        final LinkedBlockingQueue<TestPublisher<Collection<ServiceDiscovererEvent<String>>>> pubs;
        final TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> subscriber;

        State(final boolean retainAddressesTillSuccess) {
            ServiceDiscoveryRetryStrategy<String, ServiceDiscovererEvent<String>> strategy =
                    Builder.<String>withDefaults(executorRule.executor(), ofSeconds(1), ofMillis(500))
                            .retainAddressesTillSuccess(retainAddressesTillSuccess)
                            .build();
            pubs = new LinkedBlockingQueue<>();
            subscriber = new TestPublisherSubscriber<>();
            toSource(strategy.apply(defer(() -> {
                final TestPublisher<Collection<ServiceDiscovererEvent<String>>> pub = new TestPublisher<>();
                pubs.add(pub);
                return pub;
            }))).subscribe(subscriber);
            subscriber.awaitSubscription().request(MAX_VALUE);
        }
    }
}
