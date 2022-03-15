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

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.grpc.api.GrpcStatusCode.FAILED_PRECONDITION;
import static io.servicetalk.grpc.api.GrpcStatusCode.NOT_FOUND;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.health.DefaultHealthService.OVERALL_SERVICE_NAME;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.NOT_SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVING;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
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
