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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

/**
 * A factory for binding a <a href="https://www.grpc.io">gRPC</a> service to a server using a {@link ServerBinder}.
 *
 * @param <Service> Type for service
 */
public abstract class GrpcServiceFactory<Service extends GrpcService> {

    private final GrpcRoutes<Service> routes;

    /**
     * Creates new instance.
     *
     * @param routes {@link GrpcRoutes} that will hold the routes for the constructed service.
     */
    protected GrpcServiceFactory(final GrpcRoutes<Service> routes) {
        this.routes = routes;
    }

    /**
     * Merges multiple {@link GrpcServiceFactory factories} into a single instance.
     * @param factories instanes of {@link GrpcServiceFactory} to merge.
     * @return An aggregate {@link GrpcServiceFactory}.
     */
    @SuppressWarnings("unchecked")
    public static GrpcServiceFactory<?> merge(final GrpcServiceFactory<?>... factories) {
        if (factories.length == 1) {
            return factories[0];
        }
        final GrpcRoutes<?>[] routes = new GrpcRoutes[factories.length];
        for (int i = 0; i < factories.length; i++) {
            final GrpcServiceFactory<?> factory = factories[i];
            routes[i] = factory.routes;
        }
        return new MergedServiceFactory(routes);
    }

    /**
     * Use the passed {@link ServerBinder} to bind an appropriate
     * <a href="https://www.grpc.io">gRPC</a> service for the server.
     *
     * @param binder {@link ServerBinder} to bind <a href="https://www.grpc.io">gRPC</a> service to the server.
     * @param executionContext {@link ExecutionContext} to use for the service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> bind(final ServerBinder binder, final ExecutionContext executionContext) {
        GrpcExecutionContext useContext = executionContext instanceof GrpcExecutionContext ?
                (GrpcExecutionContext) executionContext :
                new DefaultGrpcExecutionContext(executionContext instanceof HttpExecutionContext ?
                        (HttpExecutionContext) executionContext :
                        new DefaultHttpExecutionContext(executionContext.bufferAllocator(),
                                executionContext.ioExecutor(),
                                executionContext.executor(),
                                executionContext.executionStrategy() instanceof HttpExecutionStrategy ?
                                        GrpcExecutionStrategy.from(
                                                (HttpExecutionStrategy) executionContext.executionStrategy()) :
                                        HttpExecutionStrategies.defaultStrategy()
                        )
                );

        return routes.bind(binder, useContext);
    }

    /**
     * A utility to bind an HTTP service for <a href="https://www.grpc.io">gRPC</a> with an
     * appropriate programming model.
     */
    public interface ServerBinder {

        /**
         * Binds an {@link HttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link HttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bind(HttpService service);

        /**
         * Binds a {@link StreamingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link StreamingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindStreaming(StreamingHttpService service);

        /**
         * Binds a {@link BlockingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link BlockingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindBlocking(BlockingHttpService service);

        /**
         * Binds a {@link BlockingStreamingHttpService} to the associated server.
         * <p>
         * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
         *
         * @param service {@link BlockingStreamingHttpService} to bind.
         * @return A {@link Single} that completes when the server is successfully started or terminates with an error
         * if the server could not be started.
         */
        Single<ServerContext> bindBlockingStreaming(BlockingStreamingHttpService service);
    }

    private static final class MergedServiceFactory extends GrpcServiceFactory {

        @SuppressWarnings("unchecked")
        MergedServiceFactory(final GrpcRoutes... routes) {
            super(GrpcRoutes.merge(routes));
        }
    }
}
