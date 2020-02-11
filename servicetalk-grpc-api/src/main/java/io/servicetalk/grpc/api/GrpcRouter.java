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
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingRequestStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingResponseStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.RequestStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.ResponseStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.Route;
import io.servicetalk.grpc.api.GrpcRoutes.StreamingRoute;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcRouteConversions.toAsyncCloseable;
import static io.servicetalk.grpc.api.GrpcRouteConversions.toRequestStreamingRoute;
import static io.servicetalk.grpc.api.GrpcRouteConversions.toResponseStreamingRoute;
import static io.servicetalk.grpc.api.GrpcRouteConversions.toRoute;
import static io.servicetalk.grpc.api.GrpcRouteConversions.toStreaming;
import static io.servicetalk.grpc.api.GrpcStatus.fromCodeValue;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcUtils.newErrorResponse;
import static io.servicetalk.grpc.api.GrpcUtils.newResponse;
import static io.servicetalk.grpc.api.GrpcUtils.readGrpcMessageEncoding;
import static io.servicetalk.grpc.api.GrpcUtils.setStatus;
import static io.servicetalk.grpc.api.GrpcUtils.setStatusOk;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

/**
 * A router that can route <a href="https://www.grpc.io">gRPC</a> requests to a user provided
 * implementation of a <a href="https://www.grpc.io">gRPC</a> method.
 */
final class GrpcRouter {

    private final Map<String, RouteProvider> routes;
    private final Map<String, RouteProvider> streamingRoutes;
    private final Map<String, RouteProvider> blockingRoutes;
    private final Map<String, RouteProvider> blockingStreamingRoutes;

    private static final GrpcStatus STATUS_UNIMPLEMENTED = fromCodeValue(UNIMPLEMENTED.value());
    private static final StreamingHttpService NOT_FOUND_SERVICE = (ctx, request, responseFactory) -> {
        final StreamingHttpResponse response = newResponse(responseFactory, STATUS_UNIMPLEMENTED,
                ctx.executionContext().bufferAllocator());
        response.version(request.version());
        return succeeded(response);
    };

    private GrpcRouter(final Map<String, RouteProvider> routes,
                       final Map<String, RouteProvider> streamingRoutes,
                       final Map<String, RouteProvider> blockingRoutes,
                       final Map<String, RouteProvider> blockingStreamingRoutes) {
        this.routes = unmodifiableMap(routes);
        this.streamingRoutes = unmodifiableMap(streamingRoutes);
        this.blockingRoutes = unmodifiableMap(blockingRoutes);
        this.blockingStreamingRoutes = unmodifiableMap(blockingStreamingRoutes);
    }

    Single<ServerContext> bind(final ServerBinder binder, final ExecutionContext executionContext) {
        final CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        final Map<String, StreamingHttpService> allRoutes = new HashMap<>();
        populateRoutes(executionContext, allRoutes, routes, closeable);
        populateRoutes(executionContext, allRoutes, streamingRoutes, closeable);
        populateRoutes(executionContext, allRoutes, blockingRoutes, closeable);
        populateRoutes(executionContext, allRoutes, blockingStreamingRoutes, closeable);

        // TODO: Optimize to bind a specific programming model service based on routes
        return binder.bindStreaming(new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                final StreamingHttpService service;
                if (!POST.equals(request.method()) || (service = allRoutes.get(request.path())) == null) {
                    return NOT_FOUND_SERVICE.handle(ctx, request, responseFactory);
                } else {
                    return service.handle(ctx, request, responseFactory);
                }
            }

            @Override
            public Completable closeAsync() {
                return closeable.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return closeable.closeAsyncGracefully();
            }
        });
    }

    private static void populateRoutes(final ExecutionContext executionContext,
                                       final Map<String, StreamingHttpService> allRoutes,
                                       final Map<String, RouteProvider> routes,
                                       final CompositeCloseable closeable) {
        for (Map.Entry<String, RouteProvider> entry : routes.entrySet()) {
            final String path = entry.getKey();
            final ServiceAdapterHolder adapterHolder = entry.getValue().buildRoute(executionContext);
            final StreamingHttpService route = closeable.append(adapterHolder.adaptor());
            verifyNoOverrides(allRoutes.put(path, adapterHolder.serviceInvocationStrategy()
                    .offloadService(executionContext.executor(), route)), path, emptyMap());
        }
    }

    /**
     * A builder for building a {@link GrpcRouter}.
     */
    static final class Builder {

        private final Map<String, RouteProvider> routes;
        private final Map<String, RouteProvider> streamingRoutes;
        private final Map<String, RouteProvider> blockingRoutes;
        private final Map<String, RouteProvider> blockingStreamingRoutes;
        private final Map<String, GrpcExecutionStrategy> executionStrategies;

        Builder() {
            routes = new HashMap<>();
            streamingRoutes = new HashMap<>();
            blockingRoutes = new HashMap<>();
            blockingStreamingRoutes = new HashMap<>();
            executionStrategies = new HashMap<>();
        }

        Builder(final Map<String, RouteProvider> routes,
                final Map<String, RouteProvider> streamingRoutes,
                final Map<String, RouteProvider> blockingRoutes,
                final Map<String, RouteProvider> blockingStreamingRoutes,
                final Map<String, GrpcExecutionStrategy> executionStrategies) {
            this.routes = routes;
            this.streamingRoutes = streamingRoutes;
            this.blockingRoutes = blockingRoutes;
            this.blockingStreamingRoutes = blockingStreamingRoutes;
            this.executionStrategies = executionStrategies;
        }

        RouteProviders drainRoutes() {
            final Map<String, RouteProvider> allRoutes = new HashMap<>();
            allRoutes.putAll(routes);
            allRoutes.putAll(streamingRoutes);
            allRoutes.putAll(blockingRoutes);
            allRoutes.putAll(blockingStreamingRoutes);
            routes.clear();
            streamingRoutes.clear();
            blockingRoutes.clear();
            blockingStreamingRoutes.clear();
            return new RouteProviders(allRoutes);
        }

        GrpcExecutionStrategy executionStrategyFor(final String path, final GrpcExecutionStrategy defaultValue) {
            return executionStrategies.getOrDefault(path, defaultValue);
        }

        static GrpcRouter.Builder merge(final GrpcRouter.Builder... builders) {
            final Map<String, RouteProvider> routes = new HashMap<>();
            final Map<String, RouteProvider> streamingRoutes = new HashMap<>();
            final Map<String, RouteProvider> blockingRoutes = new HashMap<>();
            final Map<String, RouteProvider> blockingStreamingRoutes = new HashMap<>();
            final Map<String, GrpcExecutionStrategy> executionStrategies = new HashMap<>();
            for (Builder builder : builders) {
                mergeRoutes(routes, builder.routes);
                mergeRoutes(streamingRoutes, builder.streamingRoutes);
                mergeRoutes(blockingRoutes, builder.blockingRoutes);
                mergeRoutes(blockingStreamingRoutes, builder.blockingStreamingRoutes);
                executionStrategies.putAll(builder.executionStrategies);
            }
            return new Builder(routes, streamingRoutes, blockingRoutes, blockingStreamingRoutes, executionStrategies);
        }

        private static void mergeRoutes(final Map<String, RouteProvider> first,
                                        final Map<String, RouteProvider> second) {
            for (Map.Entry<String, RouteProvider> entry : second.entrySet()) {
                final String path = entry.getKey();
                verifyNoOverrides(first.put(path, entry.getValue()), path, emptyMap());
            }
        }

        <Req, Resp> Builder addRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final Route<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            verifyNoOverrides(routes.put(path, new RouteProvider(executionContext -> toStreamingHttpService(
                    new HttpService() {
                        @Override
                        public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                                           final HttpResponseFactory responseFactory) {
                            try {
                                final GrpcServiceContext serviceContext =
                                        new DefaultGrpcServiceContext(request.path(), ctx);
                                final HttpDeserializer<Req> deserializer =
                                        serializationProvider.deserializerFor(readGrpcMessageEncoding(request),
                                                requestClass);
                                return route.handle(serviceContext, request.payloadBody(deserializer))
                                        .map(rawResp -> newResponse(responseFactory,
                                                ctx.executionContext().bufferAllocator())
                                                .payloadBody(rawResp,
                                                        serializationProvider.serializerFor(serviceContext,
                                                                responseClass)))
                                        .recoverWith(cause -> succeeded(newErrorResponse(responseFactory, cause,
                                                ctx.executionContext().bufferAllocator())));
                            } catch (Throwable t) {
                                return succeeded(newErrorResponse(responseFactory, t,
                                        ctx.executionContext().bufferAllocator()));
                            }
                        }

                        @Override
                        public Completable closeAsync() {
                            return route.closeAsync();
                        }

                        @Override
                        public Completable closeAsyncGracefully() {
                            return route.closeAsyncGracefully();
                        }
                    }, strategy -> executionStrategy == null ? strategy : executionStrategy),
                    () -> toStreaming(route), () -> toRequestStreamingRoute(route),
                    () -> toResponseStreamingRoute(route), () -> route, route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no blocking-aggregated route registered
                    // for the same path:
                    path, blockingRoutes);
            executionStrategies.put(path, executionStrategy);
            return this;
        }

        <Req, Resp> Builder addStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final StreamingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            verifyNoOverrides(streamingRoutes.put(path, new RouteProvider(executionContext -> {
                final StreamingHttpService service = new StreamingHttpService() {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        try {
                            final GrpcServiceContext serviceContext =
                                    new DefaultGrpcServiceContext(request.path(), ctx);
                            final HttpDeserializer<Req> deserializer =
                                    serializationProvider.deserializerFor(readGrpcMessageEncoding(request),
                                            requestClass);
                            final Publisher<Resp> response = route.handle(serviceContext,
                                    request.payloadBody(deserializer));
                            return succeeded(newResponse(responseFactory, response,
                                    serializationProvider.serializerFor(serviceContext, responseClass),
                                    ctx.executionContext().bufferAllocator()));
                        } catch (Throwable t) {
                            return succeeded(newErrorResponse(responseFactory, t,
                                    ctx.executionContext().bufferAllocator()));
                        }
                    }

                    @Override
                    public Completable closeAsync() {
                        return route.closeAsync();
                    }

                    @Override
                    public Completable closeAsyncGracefully() {
                        return route.closeAsyncGracefully();
                    }
                };
                return new ServiceAdapterHolder() {
                    @Override
                    public StreamingHttpService adaptor() {
                        return service;
                    }

                    @Override
                    public HttpExecutionStrategy serviceInvocationStrategy() {
                        return executionStrategy == null ? defaultStrategy() : executionStrategy;
                    }
                };
            }, () -> route, () -> toRequestStreamingRoute(route), () -> toResponseStreamingRoute(route),
                    () -> toRoute(route), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no blocking-streaming route registered
                    // for the same path:
                    path, blockingStreamingRoutes);
            executionStrategies.put(path, executionStrategy);
            return this;
        }

        <Req, Resp> Builder addRequestStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final RequestStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            return addStreamingRoute(path, executionStrategy,
                    new StreamingRoute<Req, Resp>() {
                        @Override
                        public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                            return route.handle(ctx, request).toPublisher();
                        }

                        @Override
                        public Completable closeAsync() {
                            return route.closeAsync();
                        }

                        @Override
                        public Completable closeAsyncGracefully() {
                            return route.closeAsyncGracefully();
                        }
                    }, requestClass, responseClass, serializationProvider);
        }

        /**
         * Adds a {@link ResponseStreamingRoute} to this builder.
         *
         * @param path for this route.
         * @param route {@link ResponseStreamingRoute} to add.
         * @param requestClass {@link Class} for the request object.
         * @param responseClass {@link Class} for the response object.
         * @param serializationProvider {@link GrpcSerializationProvider} for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return {@code this}.
         */
        <Req, Resp> Builder addResponseStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final ResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            return addStreamingRoute(path, executionStrategy, new StreamingRoute<Req, Resp>() {
                        @Override
                        public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                            return request.firstOrError()
                                    .flatMapPublisher(rawReq -> route.handle(ctx, rawReq));
                        }

                        @Override
                        public Completable closeAsync() {
                            return route.closeAsync();
                        }

                        @Override
                        public Completable closeAsyncGracefully() {
                            return route.closeAsyncGracefully();
                        }
                    },
                    requestClass, responseClass, serializationProvider);
        }

        /**
         * Adds a {@link BlockingRoute} to this builder.
         *
         * @param path for this route.
         * @param route {@link BlockingRoute} to add.
         * @param requestClass {@link Class} for the request object.
         * @param responseClass {@link Class} for the response object.
         * @param serializationProvider {@link GrpcSerializationProvider} for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return {@code this}.
         */
        <Req, Resp> Builder addBlockingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final BlockingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            verifyNoOverrides(blockingRoutes.put(path, new RouteProvider(executionContext ->
                    toStreamingHttpService(new BlockingHttpService() {
                        @Override
                        public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                                   final HttpResponseFactory responseFactory) {
                            try {
                                final GrpcServiceContext serviceContext =
                                        new DefaultGrpcServiceContext(request.path(), ctx);
                                final HttpDeserializer<Req> deserializer =
                                        serializationProvider.deserializerFor(readGrpcMessageEncoding(request),
                                                requestClass);
                                final Resp response = route.handle(serviceContext, request.payloadBody(deserializer));
                                return newResponse(responseFactory, ctx.executionContext().bufferAllocator())
                                        .payloadBody(response,
                                                serializationProvider.serializerFor(serviceContext, responseClass));
                            } catch (Throwable t) {
                                return newErrorResponse(responseFactory, t, ctx.executionContext().bufferAllocator());
                            }
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    }, strategy -> executionStrategy == null ? strategy : executionStrategy),
                    () -> toStreaming(route), () -> toRequestStreamingRoute(route),
                    () -> toResponseStreamingRoute(route), () -> toRoute(route), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no async-aggregated route registered
                    // for the same path:
                    path, routes);
            executionStrategies.put(path, executionStrategy);
            return this;
        }

        /**
         * Adds a {@link BlockingStreamingRoute} to this builder.
         *
         * @param path for this route.
         * @param route {@link BlockingStreamingRoute} to add.
         * @param requestClass {@link Class} for the request object.
         * @param responseClass {@link Class} for the response object.
         * @param serializationProvider {@link GrpcSerializationProvider} for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return {@code this}.
         */
        <Req, Resp> Builder addBlockingStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final BlockingStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            verifyNoOverrides(blockingStreamingRoutes.put(path, new RouteProvider(executionContext ->
                    toStreamingHttpService(new BlockingStreamingHttpService() {
                        @Override
                        public void handle(final HttpServiceContext ctx, final BlockingStreamingHttpRequest request,
                                           final BlockingStreamingHttpServerResponse response) throws Exception {
                            final GrpcServiceContext serviceContext =
                                    new DefaultGrpcServiceContext(request.path(), ctx);
                            final HttpDeserializer<Req> deserializer =
                                    serializationProvider.deserializerFor(readGrpcMessageEncoding(request),
                                            requestClass);
                            final HttpSerializer<Resp> serializer =
                                    serializationProvider.serializerFor(serviceContext, responseClass);
                            final DefaultGrpcPayloadWriter<Resp> grpcPayloadWriter =
                                    new DefaultGrpcPayloadWriter<>(response.sendMetaData(serializer));
                            try {
                                // Set status OK before invoking handle methods because users can close PayloadWriter
                                final HttpPayloadWriter<Resp> payloadWriter = grpcPayloadWriter.payloadWriter();
                                setStatusOk(payloadWriter.trailers(), ctx.executionContext().bufferAllocator());
                                route.handle(serviceContext, request.payloadBody(deserializer), grpcPayloadWriter);
                            } catch (Throwable t) {
                                // Override OK status with error details in case of failure
                                final HttpPayloadWriter<Resp> payloadWriter = grpcPayloadWriter.payloadWriter();
                                setStatus(payloadWriter.trailers(), t, ctx.executionContext().bufferAllocator());
                            } finally {
                                grpcPayloadWriter.close();
                            }
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    }, strategy -> executionStrategy == null ? strategy : executionStrategy),
                    () -> toStreaming(route), () -> toRequestStreamingRoute(route),
                    () -> toResponseStreamingRoute(route), () -> toRoute(route), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no async-streaming route registered
                    // for the same path:
                    path, streamingRoutes);
            executionStrategies.put(path, executionStrategy);
            return this;
        }

        /**
         * Adds a {@link RequestStreamingRoute} to this builder.
         *
         * @param path for this route.
         * @param route {@link RequestStreamingRoute} to add.
         * @param requestClass {@link Class} for the request object.
         * @param responseClass {@link Class} for the response object.
         * @param serializationProvider {@link GrpcSerializationProvider} for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return {@code this}.
         */
        <Req, Resp> Builder addBlockingRequestStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final BlockingRequestStreamingRoute<Req, Resp> route,
                final Class<Req> requestClass, final Class<Resp> responseClass,
                final GrpcSerializationProvider serializationProvider) {
            return addBlockingStreamingRoute(path, executionStrategy, new BlockingStreamingRoute<Req, Resp>() {
                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                            final Resp resp = route.handle(ctx, request);
                            responseWriter.write(resp);
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    },
                    requestClass, responseClass, serializationProvider);
        }

        /**
         * Adds a {@link ResponseStreamingRoute} to this builder.
         *
         * @param path for this route.
         * @param route {@link ResponseStreamingRoute} to add.
         * @param requestClass {@link Class} for the request object.
         * @param responseClass {@link Class} for the response object.
         * @param serializationProvider {@link GrpcSerializationProvider} for the route.
         * @param <Req> Type of request.
         * @param <Resp> Type of response.
         * @return {@code this}.
         */
        <Req, Resp> Builder addBlockingResponseStreamingRoute(
                final String path, @Nullable final GrpcExecutionStrategy executionStrategy,
                final BlockingResponseStreamingRoute<Req, Resp> route, final Class<Req> requestClass,
                final Class<Resp> responseClass, final GrpcSerializationProvider serializationProvider) {
            return addBlockingStreamingRoute(path, executionStrategy, new BlockingStreamingRoute<Req, Resp>() {
                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                            route.handle(ctx, requireNonNull(request.iterator().next()), responseWriter);
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    },
                    requestClass, responseClass, serializationProvider);
        }

        /**
         * Builds a {@link GrpcRouter}.
         *
         * @return {@link GrpcRouter}.
         */
        public GrpcRouter build() {
            return new GrpcRouter(routes, streamingRoutes, blockingRoutes, blockingStreamingRoutes);
        }
    }

    private static void verifyNoOverrides(@Nullable final Object oldValue, final String path,
                                          final Map<String, ?> alternativeMap) {
        if (oldValue != null || alternativeMap.containsKey(path)) {
            throw new IllegalStateException("Can not override already registered route for path: " + path);
        }
    }

    private static final class DefaultGrpcPayloadWriter<Resp> implements GrpcPayloadWriter<Resp> {
        private final HttpPayloadWriter<Resp> payloadWriter;

        DefaultGrpcPayloadWriter(final HttpPayloadWriter<Resp> payloadWriter) {
            this.payloadWriter = payloadWriter;
        }

        @Override
        public void write(final Resp resp) throws IOException {
            payloadWriter.write(resp);
        }

        @Override
        public void close() throws IOException {
            payloadWriter.close();
        }

        @Override
        public void flush() throws IOException {
            payloadWriter.flush();
        }

        HttpPayloadWriter<Resp> payloadWriter() {
            return payloadWriter;
        }
    }

    static final class RouteProviders implements AsyncCloseable {

        private final Map<String, RouteProvider> routes;
        private final CompositeCloseable closeable;

        RouteProviders(final Map<String, RouteProvider> routes) {
            this.routes = routes;
            closeable = AsyncCloseables.newCompositeCloseable();
            for (RouteProvider provider : routes.values()) {
                closeable.append(provider);
            }
        }

        RouteProvider routeProvider(final String path) {
            final RouteProvider routeProvider = routes.get(path);
            if (routeProvider == null) {
                throw new IllegalArgumentException("No routes registered for path: " + path);
            }
            return routeProvider;
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }
    }

    static final class RouteProvider implements AsyncCloseable {

        private final Function<ExecutionContext, ServiceAdapterHolder> routeProvider;
        private final Supplier<StreamingRoute<?, ?>> toStreamingConverter;
        private final Supplier<RequestStreamingRoute<?, ?>> toRequestStreamingRouteConverter;
        private final Supplier<ResponseStreamingRoute<?, ?>> toResponseStreamingRouteConverter;
        private final Supplier<Route<?, ?>> toRouteConverter;
        private final AsyncCloseable closeable;

        RouteProvider(final Function<ExecutionContext, ServiceAdapterHolder> routeProvider,
                      final Supplier<StreamingRoute<?, ?>> toStreamingConverter,
                      final Supplier<RequestStreamingRoute<?, ?>> toRequestStreamingRouteConverter,
                      final Supplier<ResponseStreamingRoute<?, ?>> toResponseStreamingRouteConverter,
                      final Supplier<Route<?, ?>> toRouteConverter,
                      final AsyncCloseable closeable) {
            this.routeProvider = routeProvider;
            this.toStreamingConverter = toStreamingConverter;
            this.toRequestStreamingRouteConverter = toRequestStreamingRouteConverter;
            this.toResponseStreamingRouteConverter = toResponseStreamingRouteConverter;
            this.toRouteConverter = toRouteConverter;
            this.closeable = closeable;
        }

        RouteProvider(final Function<ExecutionContext, ServiceAdapterHolder> routeProvider,
                      final Supplier<StreamingRoute<?, ?>> toStreamingConverter,
                      final Supplier<RequestStreamingRoute<?, ?>> toRequestStreamingRouteConverter,
                      final Supplier<ResponseStreamingRoute<?, ?>> toResponseStreamingRouteConverter,
                      final Supplier<Route<?, ?>> toRouteConverter,
                      final GracefulAutoCloseable closeable) {
            this(routeProvider, toStreamingConverter, toRequestStreamingRouteConverter,
                    toResponseStreamingRouteConverter, toRouteConverter, toAsyncCloseable(closeable));
        }

        ServiceAdapterHolder buildRoute(ExecutionContext executionContext) {
            return routeProvider.apply(executionContext);
        }

        <Req, Resp> RequestStreamingRoute<Req, Resp> asRequestStreamingRoute() {
            // We assume that generated code passes the correct types here.
            @SuppressWarnings("unchecked")
            RequestStreamingRoute<Req, Resp> toReturn =
                    (RequestStreamingRoute<Req, Resp>) toRequestStreamingRouteConverter.get();
            return toReturn;
        }

        <Req, Resp> ResponseStreamingRoute<Req, Resp>
        asResponseStreamingRoute() {
            // We assume that generated code passes the correct types here.
            @SuppressWarnings("unchecked")
            ResponseStreamingRoute<Req, Resp> toReturn =
                    (ResponseStreamingRoute<Req, Resp>) toResponseStreamingRouteConverter.get();
            return toReturn;
        }

        <Req, Resp> StreamingRoute<Req, Resp> asStreamingRoute() {
            // We assume that generated code passes the correct types here.
            @SuppressWarnings("unchecked")
            StreamingRoute<Req, Resp> toReturn = (StreamingRoute<Req, Resp>) toStreamingConverter.get();
            return toReturn;
        }

        <Req, Resp> Route<Req, Resp> asRoute() {
            // We assume that generated code passes the correct types here.
            @SuppressWarnings("unchecked")
            Route<Req, Resp> toReturn = (Route<Req, Resp>) toRouteConverter.get();
            return toReturn;
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }
    }
}
