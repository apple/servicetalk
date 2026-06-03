/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.health;

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.health.v1.Health;
import io.servicetalk.health.v1.HealthCheckRequest;
import io.servicetalk.health.v1.HealthCheckResponse;
import io.servicetalk.health.v1.HealthCheckResponse.ServingStatus;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.grpc.api.GrpcStatusCode.FAILED_PRECONDITION;
import static io.servicetalk.grpc.api.GrpcStatusCode.NOT_FOUND;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.health.DefaultHealthService.OVERALL_SERVICE_NAME;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.NOT_SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVING;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DefaultHealthServiceTest {
    private static final String UNKNOWN_SERVICE_NAME = "unknown";

    @Test
    void defaultCheck() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                assertThat(client.check(newRequest(OVERALL_SERVICE_NAME)).getStatus(), equalTo(SERVING));

                assertThat(service.setStatus(OVERALL_SERVICE_NAME, NOT_SERVING), equalTo(true));
                assertThat(client.check(newRequest(OVERALL_SERVICE_NAME)).getStatus(), equalTo(NOT_SERVING));
            }
        }
    }

    @Test
    void statusChangeCheck() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        String serviceName = "service";
        ServingStatus serviceStatus = NOT_SERVING;
        service.setStatus(serviceName, serviceStatus);
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                assertThat(client.check(newRequest(serviceName)).getStatus(), equalTo(serviceStatus));
            }
        }
    }

    @Test
    void notFoundCheck() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                assertThat(assertThrows(GrpcStatusException.class,
                        () -> client.check(newRequest(UNKNOWN_SERVICE_NAME))).status().code(),
                        equalTo(NOT_FOUND));
            }
        }
    }

    @Test
    void defaultWatch() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                BlockingIterator<HealthCheckResponse> itr = client.watch(newRequest(OVERALL_SERVICE_NAME)).iterator();
                assertThat(itr.next().getStatus(), equalTo(SERVING));

                assertThat(service.setStatus(OVERALL_SERVICE_NAME, NOT_SERVING), equalTo(true));
                assertThat(itr.next().getStatus(), equalTo(NOT_SERVING));
            }
        }
    }

    @Test
    void clearWatch() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                assertThat(service.clearStatus(OVERALL_SERVICE_NAME), equalTo(true));
                BlockingIterator<HealthCheckResponse> itr = client.watch(newRequest(OVERALL_SERVICE_NAME)).iterator();
                assertThat(itr.next().getStatus(), equalTo(SERVICE_UNKNOWN));

                assertThat(service.setStatus(OVERALL_SERVICE_NAME, SERVING), equalTo(true));
                assertThat(itr.next().getStatus(), equalTo(SERVING));
            }
        }
    }

    @Test
    void terminateWatchCheck() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                BlockingIterator<HealthCheckResponse> itr = client.watch(newRequest(OVERALL_SERVICE_NAME)).iterator();
                assertThat(itr.next().getStatus(), equalTo(SERVING));
                assertThat(client.check(newRequest(OVERALL_SERVICE_NAME)).getStatus(), equalTo(SERVING));

                assertThat(service.terminate(), equalTo(true));

                assertThat(itr.next().getStatus(), equalTo(NOT_SERVING));
                assertThat(itr.hasNext(), equalTo(false));
                assertThat(client.check(newRequest(OVERALL_SERVICE_NAME)).getStatus(), equalTo(NOT_SERVING));

                assertThat(service.setStatus(OVERALL_SERVICE_NAME, SERVING), equalTo(false));

                // Clear after terminate verifies that multiple termination doesn't cause issues.
                assertThat(service.clearStatus(OVERALL_SERVICE_NAME), equalTo(true));
                assertThat(assertThrows(GrpcStatusException.class,
                                () -> client.check(newRequest(OVERALL_SERVICE_NAME))).status().code(),
                        equalTo(NOT_FOUND));
            }
        }
    }

    @Test
    void watchPredicateFalse() throws Exception {
        watchFailure(new DefaultHealthService(name -> false), FAILED_PRECONDITION);
    }

    @Test
    void watchPredicateThrows() throws Exception {
        watchFailure(new DefaultHealthService(name -> {
            throw DELIBERATE_EXCEPTION;
        }), UNKNOWN);
    }

    @Test
    void watchBoundedByMaxServices() throws Exception {
        // OVERALL_SERVICE_NAME already occupies the single allowed slot, so any new watch is rejected.
        DefaultHealthService service = new DefaultHealthService(name -> true, 1);
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                // Watching the already-known overall service stays within the bound.
                assertThat(client.watch(newRequest(OVERALL_SERVICE_NAME)).iterator().next().getStatus(),
                        equalTo(SERVING));

                assertThat(assertThrows(GrpcStatusException.class,
                                () -> client.watch(newRequest(UNKNOWN_SERVICE_NAME)).iterator().next()).status().code(),
                        equalTo(RESOURCE_EXHAUSTED));
            }
        }
    }

    @Test
    void watchOnlyEntryEvictedWhenWatcherCancels() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        assertThat(service.trackedServices(), equalTo(1)); // OVERALL_SERVICE_NAME

        BlockingQueue<HealthCheckResponse> received = new LinkedBlockingQueue<>();
        Subscription subscription = subscribeToWatch(service, UNKNOWN_SERVICE_NAME, received);
        // The watch created a placeholder entry and delivered the initial SERVICE_UNKNOWN.
        assertThat(received.take().getStatus(), equalTo(SERVICE_UNKNOWN));
        assertThat(service.trackedServices(), equalTo(2));

        // Cancellation (e.g. client disconnect) must evict the watch-only entry.
        subscription.cancel();
        assertThat(service.trackedServices(), equalTo(1));
    }

    @Test
    void watchOnlyEntryRetainedWhileAnotherWatcherRemains() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        BlockingQueue<HealthCheckResponse> received1 = new LinkedBlockingQueue<>();
        BlockingQueue<HealthCheckResponse> received2 = new LinkedBlockingQueue<>();
        Subscription sub1 = subscribeToWatch(service, UNKNOWN_SERVICE_NAME, received1);
        Subscription sub2 = subscribeToWatch(service, UNKNOWN_SERVICE_NAME, received2);
        assertThat(received1.take().getStatus(), equalTo(SERVICE_UNKNOWN));
        assertThat(received2.take().getStatus(), equalTo(SERVICE_UNKNOWN));
        assertThat(service.trackedServices(), equalTo(2));

        sub1.cancel();
        assertThat(service.trackedServices(), equalTo(2)); // still watched by sub2
        sub2.cancel();
        assertThat(service.trackedServices(), equalTo(1)); // last watcher gone
    }

    @Test
    void registeredServiceRetainedWhenWatcherCancels() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        service.setStatus("svc", SERVING);
        assertThat(service.trackedServices(), equalTo(2));

        BlockingQueue<HealthCheckResponse> received = new LinkedBlockingQueue<>();
        Subscription subscription = subscribeToWatch(service, "svc", received);
        assertThat(received.take().getStatus(), equalTo(SERVING));

        subscription.cancel();
        // A server-registered service is retained so check() and future watchers still see it.
        assertThat(service.trackedServices(), equalTo(2));
        assertThat(service.check(null, newRequest("svc")).toFuture().get().getStatus(), equalTo(SERVING));
    }

    @Test
    void watchBeforeRegisterRetainedAfterPromotion() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        BlockingQueue<HealthCheckResponse> received = new LinkedBlockingQueue<>();
        Subscription subscription = subscribeToWatch(service, "later", received);
        assertThat(received.take().getStatus(), equalTo(SERVICE_UNKNOWN));

        // Server registers the watched-but-unknown service; the watcher observes the update.
        service.setStatus("later", SERVING);
        assertThat(received.take().getStatus(), equalTo(SERVING));

        // Now that it is registered, it survives the watcher leaving.
        subscription.cancel();
        assertThat(service.trackedServices(), equalTo(2));
    }

    @Test
    void terminateCompletesAndEvictsWatchOnlyWatcher() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        BlockingQueue<HealthCheckResponse> received = new LinkedBlockingQueue<>();
        CountDownLatch complete = new CountDownLatch(1);
        subscribeToWatch(service, UNKNOWN_SERVICE_NAME, received, complete);
        assertThat(received.take().getStatus(), equalTo(SERVICE_UNKNOWN));
        assertThat(service.trackedServices(), equalTo(2));

        assertThat(service.terminate(), equalTo(true));
        assertThat(received.take().getStatus(), equalTo(NOT_SERVING));
        assertThat(complete.await(10, SECONDS), equalTo(true));
        // Completion drove watcherTerminated, which evicted the watch-only entry; OVERALL_SERVICE_NAME remains.
        assertThat(service.trackedServices(), equalTo(1));
    }

    @Test
    void clearStatusCompletesActiveWatcher() throws Exception {
        DefaultHealthService service = new DefaultHealthService();
        service.setStatus("svc", SERVING);
        assertThat(service.trackedServices(), equalTo(2));
        BlockingQueue<HealthCheckResponse> received = new LinkedBlockingQueue<>();
        CountDownLatch complete = new CountDownLatch(1);
        subscribeToWatch(service, "svc", received, complete);
        assertThat(received.take().getStatus(), equalTo(SERVING));

        assertThat(service.clearStatus("svc"), equalTo(true));
        assertThat(received.take().getStatus(), equalTo(SERVICE_UNKNOWN));
        assertThat(complete.await(10, SECONDS), equalTo(true));
        // clearStatus removed the entry; the watcher's subsequent watcherTerminated is a harmless no-op.
        assertThat(service.trackedServices(), equalTo(1));
    }

    private static Subscription subscribeToWatch(DefaultHealthService service, String name,
                                                 BlockingQueue<HealthCheckResponse> received) {
        return subscribeToWatch(service, name, received, new CountDownLatch(1));
    }

    private static Subscription subscribeToWatch(DefaultHealthService service, String name,
                                                 BlockingQueue<HealthCheckResponse> received,
                                                 CountDownLatch complete) {
        AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
        toSource(service.watch(null, newRequest(name))).subscribe(new Subscriber<HealthCheckResponse>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                subscriptionRef.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final HealthCheckResponse response) {
                received.add(response);
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                complete.countDown();
            }
        });
        return subscriptionRef.get();
    }

    private static void watchFailure(DefaultHealthService service, GrpcStatusCode expectedCode) throws Exception {
        try (ServerContext serverCtx = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service)) {
            try (Health.BlockingHealthClient client = GrpcClients.forResolvedAddress(
                    (InetSocketAddress) serverCtx.listenAddress()).buildBlocking(new Health.ClientFactory())) {
                assertThat(assertThrows(GrpcStatusException.class,
                                () -> client.watch(newRequest(UNKNOWN_SERVICE_NAME)).iterator().next()).status().code(),
                        equalTo(expectedCode));
            }
        }
    }

    private static HealthCheckRequest newRequest(String service) {
        return HealthCheckRequest.newBuilder().setService(service).build();
    }
}
