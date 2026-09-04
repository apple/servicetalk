/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.http.utils.InterruptBlockingServiceOnCancelHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A blocking gRPC route is adapted through the same {@code BlockingHttpService} conversion as a plain HTTP blocking
 * service, so {@code INTERRUPT_BLOCKING_SERVICE_ON_CANCEL} applies to it when the filter is appended on the
 * underlying HTTP server builder via {@code GrpcServerBuilder#initializeHttp}. No gRPC-specific plumbing is involved.
 */
class GrpcServiceInterruptOnCancelTest {

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("test").build();

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void blockingRouteRespectsRequestContext(boolean interruptOnCancel) throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        BlockingTesterService service = new BlockingTesterService() {
            @Override
            public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) {
                try {
                    cancelLatch.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                if (Thread.interrupted()) {
                    interrupted.set(true);
                }
                doneLatch.countDown();
                return TestResponse.newBuilder().setMessage(request.getName()).build();
            }
        };

        ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> builder.appendServiceFilter(
                        new InterruptBlockingServiceOnCancelHttpServiceFilter(interruptOnCancel)))
                .listenAndAwait(service);
        try {
            try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory())) {
                GrpcClientMetadata metadata = new DefaultGrpcClientMetadata(ofMillis(200));
                GrpcStatusException e = assertThrows(GrpcStatusException.class,
                        () -> client.test(metadata, REQUEST));
                assertThat(e.status().code(), is(DEADLINE_EXCEEDED));
            }
            cancelLatch.countDown();

            assertThat("the service must terminate rather than hang", doneLatch.await(30, SECONDS), is(true));
            assertThat("a blocking gRPC route must follow the request context",
                    interrupted.get(), is(interruptOnCancel));
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }
}
