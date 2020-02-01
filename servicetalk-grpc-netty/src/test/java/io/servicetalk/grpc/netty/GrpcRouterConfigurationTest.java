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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.DEFAULT_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.DEFAULT_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;

public class GrpcRouterConfigurationTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Nullable
    private ServerContext serverContext;

    @After
    public void tearDown() throws Exception {
        if (serverContext != null) {
            serverContext.close();
        }
    }

    private ServerContext startGrpcServer(ServiceFactory... serviceFactories) throws Exception {
        return serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(serviceFactories);
    }

    @Test
    public void testCanNotAppendFilterWithoutImplementingAllRoutes() {
        Throwable t = assertThrows(IllegalArgumentException.class, () -> startGrpcServer(new ServiceFactory.Builder()
                .test(DEFAULT_STRATEGY_ASYNC_SERVICE)
                .build()
                .appendServiceFilter(delegate -> new TesterProto.Tester.TesterServiceFilter(delegate) {
                    @Override
                    public Single<TesterProto.TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
                        if (request.getName().isEmpty()) {
                            throw new IllegalArgumentException("Received name can not be empty");
                        }
                        return delegate().test(ctx, request);
                    }
                })));
        assertThat(t.getMessage(), startsWith("No routes registered for path"));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPath() {
        final TesterService asyncService = DEFAULT_STRATEGY_ASYNC_SERVICE;
        final TesterService alternativeAsyncService = CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE;
        testCanNotOverrideAlreadyRegisteredPath(TestRpc.PATH, builder -> builder
                .test(asyncService)
                .test(alternativeAsyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStream(asyncService)
                .testBiDiStream(alternativeAsyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestResponseStreamRpc.PATH, builder -> builder
                .testResponseStream(asyncService)
                .testResponseStream(alternativeAsyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestRequestStreamRpc.PATH, builder -> builder
                .testRequestStream(asyncService)
                .testRequestStream(alternativeAsyncService));

        final BlockingTesterService blockingService = DEFAULT_STRATEGY_BLOCKING_SERVICE;
        final BlockingTesterService alternativeBlockingService = CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE;
        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRpc.PATH, builder -> builder
                .testBlocking(blockingService)
                .testBlocking(alternativeBlockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStreamBlocking(blockingService)
                .testBiDiStreamBlocking(alternativeBlockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestResponseStreamRpc.PATH, builder -> builder
                .testResponseStreamBlocking(blockingService)
                .testResponseStreamBlocking(alternativeBlockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRequestStreamRpc.PATH, builder -> builder
                .testRequestStreamBlocking(blockingService)
                .testRequestStreamBlocking(alternativeBlockingService));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherStrategy() {
        final TesterService asyncService = DEFAULT_STRATEGY_ASYNC_SERVICE;
        testCanNotOverrideAlreadyRegisteredPath(TestRpc.PATH, builder -> builder
                .test(asyncService)
                .test(noOffloadsStrategy(), asyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStream(asyncService)
                .testBiDiStream(noOffloadsStrategy(), asyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestResponseStreamRpc.PATH, builder -> builder
                .testResponseStream(asyncService)
                .testResponseStream(noOffloadsStrategy(), asyncService));

        testCanNotOverrideAlreadyRegisteredPath(TestRequestStreamRpc.PATH, builder -> builder
                .testRequestStream(asyncService)
                .testRequestStream(noOffloadsStrategy(), asyncService));

        final BlockingTesterService blockingService = DEFAULT_STRATEGY_BLOCKING_SERVICE;
        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRpc.PATH, builder -> builder
                .testBlocking(blockingService)
                .testBlocking(noOffloadsStrategy(), blockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStreamBlocking(blockingService)
                .testBiDiStreamBlocking(noOffloadsStrategy(), blockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestResponseStreamRpc.PATH, builder -> builder
                .testResponseStreamBlocking(blockingService)
                .testResponseStreamBlocking(noOffloadsStrategy(), blockingService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRequestStreamRpc.PATH, builder -> builder
                .testRequestStreamBlocking(blockingService)
                .testRequestStreamBlocking(noOffloadsStrategy(), blockingService));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherApi() {
        final TesterService asyncService = DEFAULT_STRATEGY_ASYNC_SERVICE;
        final BlockingTesterService blockingService = DEFAULT_STRATEGY_BLOCKING_SERVICE;

        // Test registering of async RPC then blocking RPC for the same path:
        testCanNotOverrideAlreadyRegisteredPath(TestRpc.PATH, builder -> builder
                .test(asyncService)
                .testBlocking(blockingService));

        testCanNotOverrideAlreadyRegisteredPath(TestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStream(asyncService)
                .testBiDiStreamBlocking(blockingService));

        testCanNotOverrideAlreadyRegisteredPath(TestResponseStreamRpc.PATH, builder -> builder
                .testResponseStream(asyncService)
                .testResponseStreamBlocking(blockingService));

        testCanNotOverrideAlreadyRegisteredPath(TestRequestStreamRpc.PATH, builder -> builder
                .testRequestStream(asyncService)
                .testRequestStreamBlocking(blockingService));

        // Test registering of blocking RPC then async RPC for the same path:
        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRpc.PATH, builder -> builder
                .testBlocking(blockingService)
                .test(asyncService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestBiDiStreamRpc.PATH, builder -> builder
                .testBiDiStreamBlocking(blockingService)
                .testBiDiStream(asyncService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestResponseStreamRpc.PATH, builder -> builder
                .testResponseStreamBlocking(blockingService)
                .testResponseStream(asyncService));

        testCanNotOverrideAlreadyRegisteredPath(BlockingTestRequestStreamRpc.PATH, builder -> builder
                .testRequestStreamBlocking(blockingService)
                .testRequestStream(asyncService));
    }

    private void testCanNotOverrideAlreadyRegisteredPath(String path,
                                                         UnaryOperator<ServiceFactory.Builder> builderFunction) {
        Throwable t = assertThrows(IllegalStateException.class, () -> startGrpcServer(
                builderFunction.apply(new ServiceFactory.Builder()).build()));
        assertThat(t.getMessage(), equalTo("Can not override already registered route for path: " + path));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherServiceFactoryAsyncAsync() {
        testCanNotOverrideAlreadyRegisteredPath(new ServiceFactory(DEFAULT_STRATEGY_ASYNC_SERVICE),
                new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherServiceFactoryAsyncBlocking() {
        testCanNotOverrideAlreadyRegisteredPath(new ServiceFactory(DEFAULT_STRATEGY_ASYNC_SERVICE),
                new ServiceFactory(DEFAULT_STRATEGY_BLOCKING_SERVICE));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherServiceFactoryBlockingBlocking() {
        testCanNotOverrideAlreadyRegisteredPath(new ServiceFactory(DEFAULT_STRATEGY_BLOCKING_SERVICE),
                new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE));
    }

    @Test
    public void testCanNotOverrideAlreadyRegisteredPathWithAnotherServiceFactoryBlockingAsync() {
        testCanNotOverrideAlreadyRegisteredPath(new ServiceFactory(DEFAULT_STRATEGY_BLOCKING_SERVICE),
                new ServiceFactory(DEFAULT_STRATEGY_ASYNC_SERVICE));
    }

    private void testCanNotOverrideAlreadyRegisteredPath(ServiceFactory... serviceFactories) {
        Throwable t = assertThrows(IllegalStateException.class, () -> startGrpcServer(serviceFactories));
        assertThat(t.getMessage(), startsWith("Can not override already registered route for path"));
    }
}
