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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcRouter.RouteProviders;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import static io.servicetalk.concurrent.api.Completable.completed;

/**
 * A holder of <a href="https://www.grpc.io">gRPC</a> routes that constitutes a service.
 *
 * @param <Service> Type for service that these routes represent.
 */
public abstract class GrpcRoutes<Service extends GrpcService> {

    private final GrpcRouter.Builder routeBuilder;

    /**
     * Create new instance.
     */
    protected GrpcRoutes() {
        routeBuilder = new GrpcRouter.Builder();
    }

    private GrpcRoutes(final GrpcRouter.Builder routeBuilder) {
        this.routeBuilder = routeBuilder;
    }

    /**
     * Use the passed {@link ServerBinder} to bind an appropriate
     * <a href="https://www.grpc.io">gRPC</a> service for the server.
     *
     * @param binder {@link ServerBinder} to bind <a href="https://www.grpc.io">gRPC</a> service
     * to the server.
     * @param executionContext {@link ExecutionContext} to use for the service.
     *
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    final Single<ServerContext> bind(final ServerBinder binder, final ExecutionContext executionContext) {
        return routeBuilder.build().bind(binder, executionContext);
    }

    /**
     * Register all routes contained in the passed {@link Service}.
     *
     * @param service {@link Service} for which routes have to be registered.
     */
    protected abstract void registerRoutes(Service service);

    /**
     * Create a new {@link Service} from the passed {@link AllGrpcRoutes}.
     *
     * @param routes {@link AllGrpcRoutes} for which a {@link Service} has to be created.
     *
     * @return {@link Service} containing all the passed routes.
     */
    protected abstract Service newServiceFromRoutes(AllGrpcRoutes routes);

    /**
     * Returns a {@link AllGrpcRoutes} representing this {@link GrpcRoutes}. Any route registered that is not a
     * {@link StreamingRoute} will be converted to a {@link StreamingRoute}.
     *
     * @return {@link AllGrpcRoutes} representing this {@link GrpcRoutes}.
     */
    AllGrpcRoutes drainToStreamingRoutes() {
        RouteProviders routeProviders = routeBuilder.drainRoutes();
        return new AllGrpcRoutes() {
            @Override
            public <Req, Resp> StreamingRoute<Req, Resp> streamingRouteFor(
                    final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asStreamingRoute();
            }

            @Override
            public <Req, Resp> Route<Req, Resp> routeFor(final String path)
                    throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asRoute();
            }

            @Override
            public <Req, Resp> RequestStreamingRoute<Req, Resp>
            requestStreamingRouteFor(final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asRequestStreamingRoute();
            }

            @Override
            public <Req, Resp> ResponseStreamingRoute<Req, Resp>
            responseStreamingRouteFor(final String path) throws IllegalArgumentException {
                return routeProviders.routeProvider(path).asResponseStreamingRoute();
            }

            @Override
            public Completable closeAsync() {
                return routeProviders.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return routeProviders.closeAsyncGracefully();
            }
        };
    }

    static GrpcRoutes<?> merge(GrpcRoutes<?>... allRoutes) {
        GrpcRouter.Builder[] builders = new GrpcRouter.Builder[allRoutes.length];
        for (int i = 0; i < allRoutes.length; i++) {
            builders[i] = allRoutes[i].routeBuilder;
        }
        return new GrpcRoutes<GrpcService>(GrpcRouter.Builder.merge(builders)) {
            @Override
            protected void registerRoutes(final GrpcService service) {
                throw new UnsupportedOperationException("Merged service factory can not register routes.");
            }

            @Override
            protected GrpcService newServiceFromRoutes(final AllGrpcRoutes routes) {
                throw new UnsupportedOperationException("Merged service factory can not create new service.");
            }
        };
    }

    /**
     * Adds a {@link Route} to this factory.
     *
     * @param path for this route.
     * @param route {@link Route} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRoute(
            final String path, final Route<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addRoute(path, null, route, requestClass, responseClass, serializationProvider);
    }

    /**
     * Adds a {@link Route} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link Route} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRoute(
            final String path, final GrpcExecutionStrategy executionStrategy, final Route<Req, Resp> route,
            final Class<Req> requestClass, final Class<Resp> responseClass,
            final HttpSerializationProvider serializationProvider) {
        routeBuilder.addRoute(path, executionStrategy, route, requestClass, responseClass, serializationProvider);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link StreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addStreamingRoute(
            final String path, final StreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link StreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link StreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final StreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link RequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRequestStreamingRoute(
            final String path, final RequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addRequestStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link RequestStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link RequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addRequestStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final RequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addRequestStreamingRoute(path, executionStrategy, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link ResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addResponseStreamingRoute(
            final String path, final ResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addResponseStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link ResponseStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link ResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addResponseStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final ResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addResponseStreamingRoute(path, executionStrategy, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link BlockingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRoute(
            final String path, final BlockingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy, final BlockingRoute<Req, Resp> route,
            final Class<Req> requestClass, final Class<Resp> responseClass,
            final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingRoute(path, executionStrategy, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link BlockingStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingStreamingRoute(
            final String path, final BlockingStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingStreamingRoute(path, executionStrategy,
                route, requestClass, responseClass, serializationProvider);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link BlockingRequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            final String path, final BlockingRequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingRequestStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingRequestStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingRequestStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingRequestStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingRequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingRequestStreamingRoute(path, executionStrategy, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param route {@link BlockingResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            final String path, final BlockingResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingResponseStreamingRoute(path, null, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * Adds a {@link BlockingResponseStreamingRoute} to this factory.
     *
     * @param path for this route.
     * @param executionStrategy {@link GrpcExecutionStrategy} to use.
     * @param route {@link BlockingResponseStreamingRoute} to add.
     * @param requestClass {@link Class} for the request object.
     * @param responseClass {@link Class} for the response object.
     * @param serializationProvider {@link HttpSerializationProvider} for the route.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected final <Req, Resp> void addBlockingResponseStreamingRoute(
            final String path, final GrpcExecutionStrategy executionStrategy,
            final BlockingResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
            final Class<Resp> responseClass, final HttpSerializationProvider serializationProvider) {
        routeBuilder.addBlockingResponseStreamingRoute(path, executionStrategy, route, requestClass, responseClass,
                serializationProvider);
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface Route<Req, Resp> extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Single<Resp> handle(GrpcServiceContext ctx, Req request);

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with bi-directional streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface StreamingRoute<Req, Resp> extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Publisher} of {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Publisher<Resp> handle(GrpcServiceContext ctx, Publisher<Req> request);

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with request streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface RequestStreamingRoute<Req, Resp>
            extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Publisher} of {@link Req} to handle.
         * @return {@link Single} containing the response.
         */
        Single<Resp> handle(GrpcServiceContext ctx, Publisher<Req> request);

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }

    /**
     * An asynchronous <a href="https://www.grpc.io">gRPC</a> route with response streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface ResponseStreamingRoute<Req, Resp>
            extends AsyncCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return {@link Publisher} containing the response.
         */
        Publisher<Resp> handle(GrpcServiceContext ctx, Req request);

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface BlockingRoute<Req, Resp>
            extends GracefulAutoCloseable {
        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @return The response.
         * @throws Exception If an exception occurs during request processing.
         */
        Resp handle(GrpcServiceContext ctx, Req request) throws Exception;

        @Override
        default void close() {
            // No op
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with bi-directional streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface BlockingStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link BlockingIterable} of {@link Req} to handle.
         * @param responseWriter {@link GrpcPayloadWriter} to write the response.
         * @throws Exception If an exception occurs during request processing.
         */
        void handle(GrpcServiceContext ctx, BlockingIterable<Req> request,
                    GrpcPayloadWriter<Resp> responseWriter) throws Exception;

        @Override
        default void close() {
            // No op
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with request streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface BlockingRequestStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link BlockingIterable} of {@link Req} to handle.
         * @return The response.
         * @throws Exception If an exception occurs during request processing.
         */
        Resp handle(GrpcServiceContext ctx, BlockingIterable<Req> request) throws Exception;

        @Override
        default void close() {
            // No op
        }
    }

    /**
     * A blocking <a href="https://www.grpc.io">gRPC</a> route with response streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    protected interface BlockingResponseStreamingRoute<Req, Resp>
            extends GracefulAutoCloseable {

        /**
         * Handles the passed {@link Req}.
         *
         * @param ctx {@link GrpcServiceContext} for this call.
         * @param request {@link Req} to handle.
         * @param responseWriter {@link GrpcPayloadWriter} to write the response.
         * @throws Exception If an exception occurs during request processing.
         */
        void handle(GrpcServiceContext ctx, Req request, GrpcPayloadWriter<Resp> responseWriter) throws Exception;

        @Override
        default void close() {
            // No op
        }
    }

    /**
     * A collection of route corresponding to the enclosing {@link GrpcRoutes}.
     */
    protected interface AllGrpcRoutes extends AsyncCloseable {

        /**
         * Returns the registered {@link StreamingRoute} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link StreamingRoute}.
         *
         * @param path for the route.
         *
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         *
         * @return Registered {@link StreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> StreamingRoute<Req, Resp> streamingRouteFor(String path)
                throws IllegalArgumentException;

        /**
         * Returns the registered {@link Route} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link Route}.
         *
         * @param path for the route.
         *
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         *
         * @return Registered {@link Route} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> Route<Req, Resp> routeFor(String path)
                throws IllegalArgumentException;

        /**
         * Returns the registered {@link RequestStreamingRoute} for the passed {@code path}. If a route with a different
         * programming model is registered, it will be converted to a {@link RequestStreamingRoute}.
         *
         * @param path for the route.
         *
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         *
         * @return Registered {@link RequestStreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> RequestStreamingRoute<Req, Resp>
        requestStreamingRouteFor(String path) throws IllegalArgumentException;

        /**
         * Returns the registered {@link ResponseStreamingRoute} for the passed {@code path}. If a route with a
         * different programming model is registered, it will be converted to a {@link ResponseStreamingRoute}.
         *
         * @param path for the route.
         *
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         *
         * @return Registered {@link ResponseStreamingRoute} for the passed {@code path}.
         * @throws IllegalArgumentException If the route does not exist.
         */
        <Req, Resp> ResponseStreamingRoute<Req, Resp>
        responseStreamingRouteFor(String path) throws IllegalArgumentException;
    }
}
