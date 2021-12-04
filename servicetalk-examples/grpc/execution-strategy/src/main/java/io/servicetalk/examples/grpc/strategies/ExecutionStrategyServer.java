/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.strategies;

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcExecutionStrategies;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.transport.api.ServerContext;

import io.grpc.examples.strategies.Greeter;
import io.grpc.examples.strategies.Greeter.GreeterService;
import io.grpc.examples.strategies.HelloReply;
import io.grpc.examples.strategies.HelloRequest;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.offloadNever;

/**
 * Extends the async "Hello World" example to demonstrate support for alternative execution strategies and executors.
 * <p>
 * Start this server first and then run the {@link ExecutionStrategyClient}.
 */
public final class ExecutionStrategyServer {

    private static final GrpcExecutionStrategy CUSTOM_STRATEGY =
            GrpcExecutionStrategies.customStrategyBuilder().offloadSend().build();

    public static void main(String... args) throws Exception {
        int port = 8080;

        try (CompositeCloseable closeEverything = AsyncCloseables.newCompositeCloseable()) {
            Executor executor = Executors.newCachedThreadExecutor(new DefaultThreadFactory("custom"));
            // executor will be closed last, servers are prepended before executor.
            closeEverything.append(executor);

            // Default server configuration.
            // -> route offloaded to global executor
            System.out.printf("\n%d : default server\n", port);
            ServerContext defaultServer = GrpcServers.forPort(port++)
                    .listenAndAwait((GreeterService)
                            (ctx, request) -> getReplySingle(request, "default server"));
            closeEverything.prepend(defaultServer);

            // No offloads strategy specified on the server, async route does not override its strategy.
            // -> no offloading, route executed on IoExecutor
            System.out.printf("\n%d : no offloading server, async route\n", port);
            ServerContext asyncServer = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executionStrategy(offloadNever()))
                    .listenAndAwait((GreeterService)
                            (ctx, request) -> getReplySingle(request, "no offloading server, async route"));
            closeEverything.prepend(asyncServer);

            // No offloads strategy specified on the server, blocking route does not override its strategy.
            // -> no offloading, route executed on IoExecutor
            System.out.printf("\n%d : no offloading server, blocking route\n", port);
            ServerContext blockingServer = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executionStrategy(offloadNever()))
                    .listenAndAwait((Greeter.BlockingGreeterService)
                            (ctx, request) -> getReply(request, "no offloading server, blocking route"));
            closeEverything.prepend(blockingServer);

            // No offloads strategy specified on the server, route overrides it to use the default strategy.
            // -> route offloaded to global executor
            System.out.printf("\n%d : no offloading server, default offloading for the route\n", port);
            ServerContext noOffloadsServerRouteOffloads = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executionStrategy(offloadNever()))
                    .listenAndAwait(new Greeter.ServiceFactory.Builder()
                            .sayHello(defaultStrategy(),
                                    (ctx, request) -> getReplySingle(request,
                                            "no offloading server, default offloading for the route"))
                            .build());
            closeEverything.prepend(noOffloadsServerRouteOffloads);

            // No offloads strategy specified on the server, route overrides it to use a custom strategy.
            // -> route offloaded to global executor
            System.out.printf("\n%d: no offloading server, custom offloading for the route\n", port);
            ServerContext noOffloadsServerRouteOffloadCustom = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executionStrategy(offloadNever()))
                    .listenAndAwait(new Greeter.ServiceFactory.Builder().sayHello(CUSTOM_STRATEGY,
                                    (ctx, request) -> getReplySingle(request,
                                            "no offloading server, custom offloading for the route"))
                            .build());
            closeEverything.prepend(noOffloadsServerRouteOffloadCustom);

            // Server with a default strategy but a custom executor, route does not override its strategy.
            // -> route offloaded to custom executor
            System.out.printf("\n%d : server with a default offloading and a custom executor\n", port);
            ServerContext customExecutorServer = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executor(executor))
                    .listenAndAwait((GreeterService) (ctx, request) ->
                                    getReplySingle(request, "server with a default offloading and a custom executor"));
            closeEverything.prepend(customExecutorServer);

            // Server has default configuration, route attempts to use no offloads strategy, which is ignored.
            // (Too late, already offloaded at the server level)
            // -> route offloaded to global executor
            System.out.printf("\n%d : default server, no offloading route\n", port);
            ServerContext noOffloadsRoute = GrpcServers.forPort(port++)
                    .listenAndAwait(new Greeter.ServiceFactory.Builder().sayHello(offloadNever(),
                                    (ctx, request) -> getReplySingle(request, "default server, no offloading route"))
                            .build());
            closeEverything.prepend(noOffloadsRoute);

            // Server has default configuration, route attempts to use no offloads strategy via annotation, which is
            // ignored. (Too late, already offloaded at the server level)
            // -> route offloaded to global executor
            System.out.printf("\n%d : default server, no offloading (annotation) route\n", port);
            ServerContext noOffloadsAnnotation = GrpcServers.forPort(port++)
                    .listenAndAwait(new NoOffloadsGreeterService());
            closeEverything.prepend(noOffloadsAnnotation);

            // No offloads strategy specified on the server, route overrides it to also use no offloads strategy,
            // which is redundant.
            // -> no offloading, route executed on IoExecutor
            System.out.printf("\n%d : no offloading server, no offloading route\n", port);
            ServerContext noOffloadsServerRoute = GrpcServers.forPort(port++)
                    .initializeHttp(init -> init.executionStrategy(offloadNever()))
                    .listenAndAwait(new Greeter.ServiceFactory.Builder()
                            .sayHello(offloadNever(),
                                    (ctx, request) ->
                                            getReplySingle(request, "no offloading server, no offloading route"))
                            .build());
            closeEverything.prepend(noOffloadsServerRoute);

            noOffloadsServerRoute.awaitShutdown();
        }
    }

    private static Single<HelloReply> getReplySingle(final HelloRequest request, final String greeter) {
        return succeeded(getReply(request, greeter));
    }

    private static HelloReply getReply(final HelloRequest request, final String greeter) {
        String reply = String.format("Hello %s from %s on %s",
                request.getName(), greeter, Thread.currentThread().getName());
        return HelloReply.newBuilder().setMessage(reply).build();
    }

    private static class NoOffloadsGreeterService implements GreeterService {

        @Override
        @NoOffloadsRouteExecutionStrategy
        public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return getReplySingle(request, "default server, no offloading (annotation) route");
        }
    }
}
