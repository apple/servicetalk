/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy.Builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.ExecutorExtension.withTestExecutor;
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

class DefaultServiceDiscoveryRetryStrategyTest {
    @RegisterExtension
    final ExecutorExtension<TestExecutor> executorExtension = withTestExecutor();

    private static Stream<Arguments> booleanMatrix() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true));
    }

    @Test
    void errorWithNoAddresses() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);
        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    void newAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);
        final DefaultServiceDiscovererEvent<String> evt2 = new DefaultServiceDiscovererEvent<>("addr2", true);
        sdEvents.onNext(singletonList(evt2));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), containsInAnyOrder(flipAvailable(evt1), evt2));
    }

    @Test
    void overlapAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);
        final ServiceDiscovererEvent<String> evt3 = sendUpAndVerifyReceive(state, "addr3", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);

        verifyNoEventsReceived(state);

        final ServiceDiscovererEvent<String> evt4 = new DefaultServiceDiscovererEvent<>("addr4", true);
        sdEvents.onNext(asList(evt2, evt4));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(evt4, flipAvailable(evt1), flipAvailable(evt3)));
    }

    @Test
    void sameAddressPostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        final ServiceDiscovererEvent<String> evt1Un = new DefaultServiceDiscovererEvent<>("addr1", false);
        sdEvents.onNext(asList(evt1, evt1Un, evt1));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(evt1Un, evt1));
    }

    @Test
    void addRemoveBeforeRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        sdEvents.onNext(singletonList(flipAvailable(evt1)));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(flipAvailable(evt1)));

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    void removeAfterRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(asList(evt1, flipAvailable(evt1)));

        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0), contains(flipAvailable(evt1)));
    }

    @Test
    void addAndRemovePostRetry() throws Exception {
        State state = new State(true);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sdEvents.onNext(asList(evt1, flipAvailable(evt1)));
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2)));
    }

    @Test
    void noRetainActiveAddresses() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

        triggerRetry(state, sdEvents);
        final List<Collection<ServiceDiscovererEvent<String>>> items = state.subscriber.takeOnNext(1);
        assertThat("Unexpected items received.", items, hasSize(1));
        assertThat("Unexpected event received", items.get(0),
                containsInAnyOrder(flipAvailable(evt1), flipAvailable(evt2)));
    }

    @Test
    void noRetainErrorWithNoAddresses() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();

        sdEvents = triggerRetry(state, sdEvents);
        verifyNoEventsReceived(state);

        sendUpAndVerifyReceive(state, "addr1", sdEvents);
    }

    @Test
    void noRetainActiveAddressesTwoSequentialErrors() throws Exception {
        State state = new State(false);
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = state.pubs.take();
        final ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceive(state, "addr1", sdEvents);
        final ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceive(state, "addr2", sdEvents);

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
        executorExtension.executor().advanceTimeBy(1, MINUTES);
        return state.pubs.take();
    }

    private ServiceDiscovererEvent<String> sendUpAndVerifyReceive(
            final State state, final String addr,
            final TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents) {
        final DefaultServiceDiscovererEvent<String> evt = new DefaultServiceDiscovererEvent<>(addr, true);
        sdEvents.onNext(singletonList(evt));
        return verifyReceive(state, addr, true);
    }

    private ServiceDiscovererEvent<String> verifyReceive(final State state, final String addr,
                                                         final boolean available) {
        final Collection<ServiceDiscovererEvent<String>> received = state.subscriber.takeOnNext(1)
                .stream()
                .flatMap(Collection::stream)
                .collect(toList());
        assertThat("Event not received.", received, hasSize(1));
        final ServiceDiscovererEvent<String> receivedEvent = received.iterator().next();
        assertThat("Unexpected event received.", receivedEvent.address(), is(addr));
        assertThat("Unexpected event received.", receivedEvent.isAvailable(), is(available));
        return receivedEvent;
    }

    private static ServiceDiscovererEvent<String> flipAvailable(final ServiceDiscovererEvent<String> evt) {
        return new DefaultServiceDiscovererEvent<>(evt.address(), !evt.isAvailable());
    }

    private final class State {
        final LinkedBlockingQueue<TestPublisher<Collection<ServiceDiscovererEvent<String>>>> pubs;
        final TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> subscriber;

        State(final boolean retainAddressesTillSuccess) {
            ServiceDiscoveryRetryStrategy<String, ServiceDiscovererEvent<String>> strategy =
                    Builder.<String>withDefaults(executorExtension.executor(), ofSeconds(1), ofMillis(500))
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
