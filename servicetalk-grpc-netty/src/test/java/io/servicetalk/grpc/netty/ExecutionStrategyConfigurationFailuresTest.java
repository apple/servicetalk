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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.router.utils.internal.DefaultRouteExecutionStrategyFactory.getUsingDefaultStrategyFactory;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

public class ExecutionStrategyConfigurationFailuresTest {

    @NoOffloadsRouteExecutionStrategy
    private static final class MisconfiguredService implements TesterService {

        @Override
        @NoOffloadsRouteExecutionStrategy
        @RouteExecutionStrategy(id = "test")
        public Single<TesterProto.TestResponse> test(final GrpcServiceContext ctx,
                                                     final TesterProto.TestRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "")
        public Publisher<TesterProto.TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                                  final Publisher<TesterProto.TestRequest> request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "unknown")
        public Publisher<TesterProto.TestResponse> testResponseStream(final GrpcServiceContext ctx,
                                                                      final TesterProto.TestRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "test")
        public Single<TesterProto.TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                                  final Publisher<TesterProto.TestRequest> request) {
            throw new UnsupportedOperationException();
        }
    }

    @NoOffloadsRouteExecutionStrategy
    private static final class MisconfiguredBlockingService implements BlockingTesterService {

        @Override
        @NoOffloadsRouteExecutionStrategy
        @RouteExecutionStrategy(id = "test")
        public TesterProto.TestResponse test(final GrpcServiceContext ctx, final TesterProto.TestRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "")
        public void testBiDiStream(final GrpcServiceContext ctx,
                                   final BlockingIterable<TesterProto.TestRequest> request,
                                   final GrpcPayloadWriter<TesterProto.TestResponse> responseWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "unknown")
        public void testResponseStream(final GrpcServiceContext ctx, final TesterProto.TestRequest request,
                                       final GrpcPayloadWriter<TesterProto.TestResponse> responseWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        @RouteExecutionStrategy(id = "test")
        public TesterProto.TestResponse testRequestStream(final GrpcServiceContext ctx,
                                                          final BlockingIterable<TesterProto.TestRequest> request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final TesterService MISCONFIGURED_SERVICE = new MisconfiguredService();
    private static final BlockingTesterService MISCONFIGURED_BLOCKING_SERVICE = new MisconfiguredBlockingService();
    private static final RouteExecutionStrategyFactory<GrpcExecutionStrategy> STRATEGY_FACTORY =
            id -> "test".equals(id) ? noOffloadsStrategy() : getUsingDefaultStrategyFactory(id);

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Test
    public void usingServiceFactoryAsyncService() throws Exception {
        usingServiceFactory(new ServiceFactory(MISCONFIGURED_SERVICE));
    }

    @Test
    public void usingServiceFactoryBlockingService() throws Exception {
        usingServiceFactory(new ServiceFactory(MISCONFIGURED_BLOCKING_SERVICE));
    }

    private void usingServiceFactory(final ServiceFactory serviceFactory) throws Exception {
        expected.expect(IllegalStateException.class);
        expected.expectMessage(allOf(
                containsString("test("),
                containsString("testBiDiStream("),
                containsString("testResponseStream("),
                containsString("testRequestStream(")));

        GrpcServers.forAddress(localAddress(0)).listenAndAwait(serviceFactory);
    }

    @Test
    public void usingServiceFactoryWithStrategyFactoryAsyncService() throws Exception {
        usingServiceFactoryWithStrategyFactory(new ServiceFactory(MISCONFIGURED_SERVICE, STRATEGY_FACTORY));
    }

    @Test
    public void usingServiceFactoryWithStrategyFactoryBlockingService() throws Exception {
        usingServiceFactoryWithStrategyFactory(new ServiceFactory(MISCONFIGURED_BLOCKING_SERVICE, STRATEGY_FACTORY));
    }

    private void usingServiceFactoryWithStrategyFactory(final ServiceFactory serviceFactory) throws Exception {
        expected.expect(IllegalStateException.class);
        expected.expectMessage(allOf(
                containsString("test("),
                containsString("testBiDiStream("),
                containsString("testResponseStream(")));

        GrpcServers.forAddress(localAddress(0)).listenAndAwait(serviceFactory);
    }

    @Test
    public void usingServiceFactoryBuilderAsyncService() throws Exception {
        usingServiceFactoryBuilder(new ServiceFactory.Builder()
                .testRequestStream(MISCONFIGURED_SERVICE).build());
    }

    @Test
    public void usingServiceFactoryBuilderBlockingService() throws Exception {
        usingServiceFactoryBuilder(new ServiceFactory.Builder()
                .testRequestStreamBlocking(MISCONFIGURED_BLOCKING_SERVICE).build());
    }

    private void usingServiceFactoryBuilder(final ServiceFactory serviceFactory) throws Exception {
        expected.expect(IllegalStateException.class);
        expected.expectMessage(allOf(
                containsString("Failed to create an execution strategy for ID"),
                containsString("testRequestStream(")));

        GrpcServers.forAddress(localAddress(0)).listenAndAwait(serviceFactory);
    }

    @Test
    public void usingServiceFactoryBuilderWithStrategyFactoryAsyncService() throws Exception {
        usingServiceFactoryBuilderWithStrategyFactory(new ServiceFactory.Builder(STRATEGY_FACTORY)
                .testRequestStream(MISCONFIGURED_SERVICE).build());
    }

    @Test
    public void usingServiceFactoryBuilderWithStrategyFactoryBlockingService() throws Exception {
        usingServiceFactoryBuilderWithStrategyFactory(new ServiceFactory.Builder(STRATEGY_FACTORY)
                .testRequestStreamBlocking(MISCONFIGURED_BLOCKING_SERVICE).build());
    }

    private static void usingServiceFactoryBuilderWithStrategyFactory(final ServiceFactory serviceFactory)
            throws Exception {
        GrpcServers.forAddress(localAddress(0)).listenAndAwait(serviceFactory).close();
    }
}
