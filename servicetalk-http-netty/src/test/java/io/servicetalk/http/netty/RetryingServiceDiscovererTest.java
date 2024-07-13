/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererEvent.Status;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.netty.internal.GlobalExecutionContext;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryingServiceDiscovererTest {

    private static final int DEFAULT_POLL_MILLIS = CI ? 30 : 10;

    private final BlockingQueue<TestPublisher<Collection<ServiceDiscovererEvent<String>>>> pubs =
            new LinkedBlockingQueue<>();
    private final TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> subscriber =
            new TestPublisherSubscriber<>();
    private final ServiceDiscoverer<String, String, ServiceDiscovererEvent<String>> sd;

    RetryingServiceDiscovererTest() {
        @SuppressWarnings("unchecked")
        ServiceDiscoverer<String, String, ServiceDiscovererEvent<String>> delegate = mock(ServiceDiscoverer.class);
        when(delegate.discover(any())).thenReturn(Publisher.defer(() -> {
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> pub = new TestPublisher<>();
            pubs.add(pub);
            return pub;
        }));
        sd = new RetryingServiceDiscoverer<>(RetryingServiceDiscovererTest.class.getSimpleName(), delegate,
                (count, t) -> Completable.completed(), GlobalExecutionContext.globalExecutionContext(),
                e -> new DefaultServiceDiscovererEvent<>(e.address(), UNAVAILABLE));
    }

    @BeforeEach
    void setUp() {
        toSource(sd.discover("any")).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() throws Exception {
        verifyNoEventsReceived();
        assertThat("Unexpected publisher created", pubs, is(empty()));
    }

    @Test
    void errorWithNoAddresses() throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();
        sendUpAndVerifyReceived("addr1", sdEvents);
    }

    @ParameterizedTest(name = "{displayName} [{index}] withDuplicateEvents={0}")
    @ValueSource(booleans = {false, true})
    void sameAddressPostRetry(boolean withDuplicateEvents) throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr1", sdEvents);

        for (int i = 0; i < 3; i++) {
            sdEvents = triggerRetry(sdEvents);
            verifyNoEventsReceived();

            if (withDuplicateEvents) {
                ServiceDiscovererEvent<String> evt1Un = flip(evt1);
                sdEvents.onNext(asList(evt1, evt1Un, evt1));
                verifyReceivedEvents(contains(evt1, evt1Un, evt1));
            } else {
                sendUpAndVerifyReceived(evt1.address(), sdEvents);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] withDuplicateEvents={0}")
    @ValueSource(booleans = {false, true})
    void newAddressPostRetry(boolean withDuplicateEvents) throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr9", sdEvents);

        for (int i = 0; i < 3; i++) {
            sdEvents = triggerRetry(sdEvents);
            verifyNoEventsReceived();

            ServiceDiscovererEvent<String> evt2 = new DefaultServiceDiscovererEvent<>("addr" + i, AVAILABLE);
            if (withDuplicateEvents) {
                sdEvents.onNext(asList(evt2, flip(evt2), evt2));
                verifyReceivedEvents(contains(evt2, flip(evt2), evt2, flip(evt1)));
            } else {
                sdEvents.onNext(singletonList(evt2));
                verifyReceivedEvents(contains(evt2, flip(evt1)));
            }
            evt1 = evt2;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] withDuplicateEvents={0}")
    @ValueSource(booleans = {false, true})
    void overlapAddressPostRetry(boolean withDuplicateEvents) throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();

        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr1", sdEvents);
        ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceived("addr2", sdEvents);
        ServiceDiscovererEvent<String> evt3 = sendUpAndVerifyReceived("addr3", sdEvents);

        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();

        ServiceDiscovererEvent<String> evt4 = new DefaultServiceDiscovererEvent<>("addr4", AVAILABLE);
        List<ServiceDiscovererEvent<String>> newState = withDuplicateEvents ?
                asList(evt2, evt4, evt2, evt4) : asList(evt2, evt4);
        sdEvents.onNext(newState);

        Collection<ServiceDiscovererEvent<String>> events = receivedEvents(subscriber);
        assertThat("Unexpected event received", events, hasSize(newState.size() + 2));
        assertThat("Unexpected event received",
                events.stream().limit(newState.size()).collect(toList()), contains(newState.toArray()));
        assertThat("Unexpected event received",
                events.stream().skip(newState.size()).collect(toList()), containsInAnyOrder(flip(evt1), flip(evt3)));

        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();

        ServiceDiscovererEvent<String> evt5 = new DefaultServiceDiscovererEvent<>("addr5", AVAILABLE);
        newState = withDuplicateEvents ? asList(evt2, evt3, evt5, evt5) : asList(evt2, evt3, evt5);
        sdEvents.onNext(newState);

        events = receivedEvents(subscriber);
        assertThat("Unexpected event received", events, hasSize(newState.size() + 1));
        assertThat("Unexpected event received",
                events.stream().limit(newState.size()).collect(toList()), contains(newState.toArray()));
        assertThat("Unexpected event received",
                events.stream().skip(newState.size()).collect(toList()), containsInAnyOrder(flip(evt4)));
    }

    @Test
    void addRemoveBeforeRetry() throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr1", sdEvents);
        sendUpAndVerifyReceived(evt1.address(), UNAVAILABLE, sdEvents);

        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();

        sendUpAndVerifyReceived("addr1", sdEvents);
    }

    @Test
    void removeAfterRetry() throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr1", sdEvents);

        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();

        sdEvents.onNext(asList(evt1, flip(evt1)));
        verifyReceivedEvents(contains(evt1, flip(evt1)));
    }

    @Test
    void addAndRemovePostRetry() throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();
        ServiceDiscovererEvent<String> evt1 = sendUpAndVerifyReceived("addr1", sdEvents);
        ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceived("addr2", sdEvents);

        sdEvents = triggerRetry(sdEvents);
        verifyNoEventsReceived();

        sdEvents.onNext(asList(evt1, flip(evt1)));
        verifyReceivedEvents(contains(evt1, flip(evt1), flip(evt2)));
    }

    @Test
    void noUnavailableEventsAfterCancel() throws Exception {
        TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents = pubs.take();

        sendUpAndVerifyReceived("addr1", sdEvents);
        ServiceDiscovererEvent<String> evt2 = sendUpAndVerifyReceived("addr2", sdEvents);
        sendUpAndVerifyReceived("addr3", sdEvents);

        triggerRetry(sdEvents);
        verifyNoEventsReceived();

        // Cancel and re-subscribe should not produce UNAVAILABLE events
        subscriber.awaitSubscription().cancel();
        TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> newSubscriber =
                new TestPublisherSubscriber<>();
        toSource(sd.discover("any")).subscribe(newSubscriber);
        newSubscriber.awaitSubscription().request(Long.MAX_VALUE);
        sdEvents = pubs.take();

        ServiceDiscovererEvent<String> evt4 = new DefaultServiceDiscovererEvent<>("addr4", AVAILABLE);
        sdEvents.onNext(asList(evt2, evt4));
        Collection<ServiceDiscovererEvent<String>> events = receivedEvents(newSubscriber);
        assertThat("Unexpected event received", events, contains(evt2, evt4));
        verifyNoEventsReceived(subscriber);
        verifyNoEventsReceived(newSubscriber);
    }

    private TestPublisher<Collection<ServiceDiscovererEvent<String>>> triggerRetry(
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents)
            throws Exception {
        sdEvents.onError(DELIBERATE_EXCEPTION);
        return pubs.take();
    }

    private void verifyNoEventsReceived() {
        verifyNoEventsReceived(subscriber);
    }

    private void verifyNoEventsReceived(
            TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> subscriber) {
        assertThat("Unexpected event received",
                subscriber.pollOnNext(DEFAULT_POLL_MILLIS, MILLISECONDS), is(nullValue()));
    }

    private Collection<ServiceDiscovererEvent<String>> receivedEvents(
            TestPublisherSubscriber<Collection<ServiceDiscovererEvent<String>>> subscriber) {
        List<Collection<ServiceDiscovererEvent<String>>> items = subscriber.takeOnNext(1);
        assertThat("Unexpected items received", items, hasSize(1));
        return items.get(0);
    }

    private void verifyReceivedEvents(Matcher<? super Collection<ServiceDiscovererEvent<String>>> matcher) {
        Collection<ServiceDiscovererEvent<String>> events = receivedEvents(subscriber);
        assertThat("Unexpected event received", events, matcher);
    }

    private ServiceDiscovererEvent<String> sendUpAndVerifyReceived(String addr,
                TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents) {
        return sendUpAndVerifyReceived(addr, AVAILABLE, sdEvents);
    }

    private ServiceDiscovererEvent<String> sendUpAndVerifyReceived(String addr, Status status,
                TestPublisher<Collection<ServiceDiscovererEvent<String>>> sdEvents) {
        ServiceDiscovererEvent<String> evt = new DefaultServiceDiscovererEvent<>(addr, status);
        sdEvents.onNext(singletonList(evt));
        Collection<ServiceDiscovererEvent<String>> received = subscriber.takeOnNext(1)
                .stream()
                .flatMap(Collection::stream)
                .collect(toList());
        assertThat("Unexpected number of events received", received, hasSize(1));
        Iterator<ServiceDiscovererEvent<String>> iterator = received.iterator();
        ServiceDiscovererEvent<String> receivedEvt = iterator.next();
        assertThat("Unexpected event received", receivedEvt.address(), is(addr));
        assertThat("Unexpected event received", receivedEvt.status(), is(status));
        assertThat("Unexpected iterator.hasNext()", iterator.hasNext(), is(false));
        return evt;
    }

    private static ServiceDiscovererEvent<String> flip(ServiceDiscovererEvent<String> evt) {
        Status flipped = AVAILABLE.equals(evt.status()) ? UNAVAILABLE : AVAILABLE;
        return new DefaultServiceDiscovererEvent<>(evt.address(), flipped);
    }
}
