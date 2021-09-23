/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.transport.api.ServerContext;

import io.grpc.examples.strategies.Greeter;
import io.grpc.examples.strategies.HelloReply;
import io.grpc.examples.strategies.HelloRequest;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;

/**
 * Extends the async "Hello World" example to demonstrate support for alternative execution strategies and executors.
 * <p>
 * Start this server first and then run the {@link ExecutionStrategyClient}.
 */
public class ExecutionStrategyServer {

    public static void main(String... args) throws Exception {
        Executor executor = Executors.newCachedThreadExecutor();
        Completable allServicesOnClose = completed();

        int port = 8080;

        // No offloads server
        ServerContext vanillaServer = GrpcServers.forPort(port)
                .listenAndAwait((Greeter.GreeterService) (ctx, request) -> getReply(request, "vanilla"));
        allServicesOnClose.merge(vanillaServer.onClose());

        // No offloads server
        ServerContext noOffloadsServer = GrpcServers.forPort(++port)
                .executionStrategy(noOffloadsStrategy())
                .listenAndAwait((Greeter.GreeterService) (ctx, request) -> getReply(request, "server"));
        allServicesOnClose.merge(noOffloadsServer.onClose());

        // Custom executor
        ServerContext customExecutorServer = GrpcServers.forPort(++port)
                .executor(executor)
                .listenAndAwait((Greeter.GreeterService) (ctx, request) ->
                        getReply(request, "executor"));
        allServicesOnClose.merge(customExecutorServer.onClose());

        // No offloads route
        Greeter.ServiceFactory.Builder builder = new Greeter.ServiceFactory.Builder()
                .sayHello(noOffloadsStrategy(),
                        (ctx, request) -> getReply(request, "route"));
        ServerContext noOffloadsRoute = GrpcServers.forPort(++port)
                .listenAndAwait(builder.build());
        allServicesOnClose.merge(noOffloadsRoute.onClose());

        // No offloads annotation
        ServerContext noOffloadsAnnotation = GrpcServers.forPort(++port)
                .listenAndAwait(new NoOffloadsGreeterService());
        allServicesOnClose.merge(noOffloadsAnnotation.onClose());

        // No offloads server and route
        builder = new Greeter.ServiceFactory.Builder()
                .sayHello(noOffloadsStrategy(),
                        (ctx, request) -> getReply(request, "contextAndRoute"));
        ServerContext noOffloadsServerRoute = GrpcServers.forPort(++port)
                .executionStrategy(noOffloadsStrategy())
                .listenAndAwait(builder.build());
        allServicesOnClose.merge(noOffloadsServerRoute.onClose());

        noOffloadsServerRoute.awaitShutdown();

        allServicesOnClose.toFuture().get();
        executor.closeAsync().toFuture().get();
    }

    private static class NoOffloadsGreeterService implements Greeter.GreeterService {

        @Override
        @NoOffloadsRouteExecutionStrategy
        public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return getReply(request, "routeAnnotation" );
        }
    }

    private static Single<HelloReply> getReply(HelloRequest request, final String greeter) {
        String reply =  String.format("Hello %s from %s on %s",
                request.getName(), greeter, Thread.currentThread().getName());
        return succeeded(HelloReply.newBuilder().setMessage(reply).build());
    }
}
