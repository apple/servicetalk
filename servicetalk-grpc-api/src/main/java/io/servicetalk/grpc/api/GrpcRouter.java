/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpliceFlatStreamToSingleResult;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.grpc.api.DefaultGrpcMetadata.LazyContextMapSupplier;
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
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceToOffloadedStreamingHttpService;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.IoThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.grpc.api.GrpcHeaderValues.APPLICATION_GRPC;
import static io.servicetalk.grpc.api.GrpcStatus.fromCodeValue;
import static io.servicetalk.grpc.api.GrpcStatusCode.INVALID_ARGUMENT;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcUtils.grpcContentType;
import static io.servicetalk.grpc.api.GrpcUtils.initResponse;
import static io.servicetalk.grpc.api.GrpcUtils.negotiateAcceptedEncodingRaw;
import static io.servicetalk.grpc.api.GrpcUtils.newErrorResponse;
import static io.servicetalk.grpc.api.GrpcUtils.newResponse;
import static io.servicetalk.grpc.api.GrpcUtils.readGrpcMessageEncodingRaw;
import static io.servicetalk.grpc.api.GrpcUtils.setStatus;
import static io.servicetalk.grpc.api.GrpcUtils.setStatusOk;
import static io.servicetalk.grpc.api.GrpcUtils.validateContentType;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A router that can route <a href="https://www.grpc.io">gRPC</a> requests to a user provided
 * implementation of a <a href="https://www.grpc.io">gRPC</a> method.
 */
final class GrpcRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcRouter.class);

    private final Map<String, RouteProvider> routes;
    private final Map<String, RouteProvider> streamingRoutes;
    private final Map<String, RouteProvider> blockingRoutes;
    private final Map<String, RouteProvider> blockingStreamingRoutes;
    private final Map<String, GrpcExecutionStrategy> executionStrategies;

    private static final GrpcStatus STATUS_UNIMPLEMENTED = fromCodeValue(UNIMPLEMENTED.value());
    private static final StreamingHttpService NOT_FOUND_SERVICE = (ctx, request, responseFactory) -> {
        final StreamingHttpResponse response = newErrorResponse(responseFactory, APPLICATION_GRPC,
                STATUS_UNIMPLEMENTED.asException(), ctx.executionContext().bufferAllocator());
        response.version(request.version());
        return succeeded(response);
    };

    private GrpcRouter(final Map<String, RouteProvider> routes,
                       final Map<String, RouteProvider> streamingRoutes,
                       final Map<String, RouteProvider> blockingRoutes,
                       final Map<String, RouteProvider> blockingStreamingRoutes,
                       final Map<String, GrpcExecutionStrategy> executionStrategies) {
        this.routes = unmodifiableMap(routes);
        this.streamingRoutes = unmodifiableMap(streamingRoutes);
        this.blockingRoutes = unmodifiableMap(blockingRoutes);
        this.blockingStreamingRoutes = unmodifiableMap(blockingStreamingRoutes);
        this.executionStrategies = unmodifiableMap(executionStrategies);
    }

    Single<GrpcServerContext> bind(final ServerBinder binder, final GrpcExecutionContext executionContext) {
        final CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        final Map<String, StreamingHttpService> allRoutes = new HashMap<>();
        populateRoutes(executionContext, allRoutes, routes, closeable, executionStrategies);
        populateRoutes(executionContext, allRoutes, streamingRoutes, closeable, executionStrategies);
        populateRoutes(executionContext, allRoutes, blockingRoutes, closeable, executionStrategies);
        populateRoutes(executionContext, allRoutes, blockingStreamingRoutes, closeable, executionStrategies);

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

            /**
             * {@inheritDoc}
             * @return {@link HttpExecutionStrategies#offloadAll()} as default safe behavior for predicates and routes.
             * Apps will typically use {@link HttpExecutionStrategies#offloadNone()} with
             * {@link io.servicetalk.http.api.HttpServerBuilder#executionStrategy(HttpExecutionStrategy)} in
             * {@link io.servicetalk.grpc.api.GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)} to
             * override if either no offloading is required or diverse strategies are needed for various routes.
             */
            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return offloadAll();
            }
        }).map(httpServerContext -> new DefaultGrpcServerContext(httpServerContext, executionContext));
    }

    private static void populateRoutes(final GrpcExecutionContext executionContext,
                                       final Map<String, StreamingHttpService> allRoutes,
                                       final Map<String, RouteProvider> routes,
                                       final CompositeCloseable closeable,
                                       final Map<String, GrpcExecutionStrategy> executionStrategies) {
        for (Map.Entry<String, RouteProvider> entry : routes.entrySet()) {
            final String path = entry.getKey();
            final ServiceAdapterHolder adapterHolder = entry.getValue().serviceAdapterHolder();
            final StreamingHttpService route = closeable.append(adapterHolder.adaptor());
            final GrpcExecutionStrategy routeStrategy = executionStrategies.getOrDefault(path, null);
            final HttpExecutionStrategy missing = null == routeStrategy ?
                    HttpExecutionStrategies.offloadNone() :
                    executionContext.executionStrategy().missing(routeStrategy);
            verifyNoOverrides(allRoutes.put(path,
                    null != routeStrategy && missing.isRequestResponseOffloaded() ?
                              StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(
                                  adapterHolder.serviceInvocationStrategy(),
                                  executionContext.executor(),
                                  IoThreadFactory.IoThread::currentThreadIsIoThread,
                                  route) :
                              route),
                    path, emptyMap());
            LOGGER.debug("route strategy for path={} : ctx={} route={} → using={}",
                    path, executionContext.executionStrategy(), routeStrategy, missing);
        }
    }

    private static final class DefaultGrpcServerContext implements GrpcServerContext {

        private final HttpServerContext delegate;
        private final GrpcExecutionContext executionContext;

        DefaultGrpcServerContext(final HttpServerContext delegate, final GrpcExecutionContext executionContext) {
            this.delegate = requireNonNull(delegate);
            this.executionContext = requireNonNull(executionContext);
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public SocketAddress listenAddress() {
            return delegate.listenAddress();
        }

        @Override
        public GrpcExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public void awaitShutdown() {
            delegate.awaitShutdown();
        }

        @Override
        public void close() throws Exception {
            delegate.close();
        }

        @Override
        public void closeGracefully() throws Exception {
            delegate.closeGracefully();
        }

        @Override
        public void acceptConnections(final boolean accept) {
            delegate.acceptConnections(accept);
        }
    }

    /**
     * A builder for building a {@link GrpcRouter}.
     */
    static final class Builder {

        private static final String SINGLE_MESSAGE_EXPECTED_NONE_RECEIVED_MSG =
                "Single request message was expected, but none was received";
        private static final String MORE_THAN_ONE_MESSAGE_RECEIVED_MSG = "More than one request message received";

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

        /**
         * async aggregated
         */
        <Req, Resp> void addRoute(MethodDescriptor<Req, Resp> methodDescriptor,
                                  BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                                  @Nullable GrpcExecutionStrategy executionStrategy, Route<Req, Resp> route) {
            GrpcSerializer<Resp> serializerIdentity = serializer(methodDescriptor);
            List<GrpcSerializer<Resp>> serializers = serializers(methodDescriptor, compressors);
            GrpcDeserializer<Req> deserializerIdentity = deserializer(methodDescriptor);
            List<GrpcDeserializer<Req>> deserializers = deserializers(methodDescriptor, decompressors.decoders());
            CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
            CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                    .serializerDescriptor().contentType());
            CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                    .serializerDescriptor().contentType());
            verifyNoOverrides(routes.put(methodDescriptor.httpPath(),
                    new RouteProvider(toStreamingHttpService(
                    new HttpService() {
                        @Override
                        public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                                           final HttpResponseFactory responseFactory) {
                            final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                            try {
                                validateContentType(request.headers(), requestContentType);
                                final GrpcDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                        request.headers(), deserializerIdentity, deserializers,
                                        GrpcDeserializer::messageEncoding);
                                final LazyContextMapSupplier responseContext = new LazyContextMapSupplier();
                                return route.handle(new DefaultGrpcServiceContext(methodDescriptor.httpPath(),
                                                        request::context, responseContext, ctx),
                                        deserializer.deserialize(request.payloadBody(), allocator))
                                        .map(rawResp -> {
                                            final GrpcSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                                    request.headers(), serializerIdentity, serializers,
                                                    GrpcSerializer::messageEncoding);
                                            final HttpResponse httpResponse = newResponse(responseFactory,
                                                    responseContentType, serializer.messageEncoding(), acceptedEncoding)
                                                    .payloadBody(serializer.serialize(rawResp, allocator));
                                            if (responseContext.isInitialized()) {
                                                httpResponse.context(responseContext.get());
                                            }
                                            return httpResponse;
                                        })
                                        .onErrorReturn(cause -> {
                                            LOGGER.debug("Unexpected exception from aggregated response for path : {}",
                                                    methodDescriptor.httpPath(), cause);
                                            return newErrorResponse(responseFactory, responseContentType, cause,
                                                    allocator);
                                        });
                            } catch (Throwable t) {
                                LOGGER.debug("Unexpected exception from aggregated endpoint for path: {}",
                                        methodDescriptor.httpPath(), t);
                                return succeeded(newErrorResponse(responseFactory, responseContentType, t, allocator));
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
                    }, executionStrategy == null ? defaultStrategy() : executionStrategy), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no blocking-aggregated route registered
                    // for the same path:
                    methodDescriptor.httpPath(), blockingRoutes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

        /**
         * async streaming bi-directional
         */
        <Req, Resp> void addStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, StreamingRoute<Req, Resp> route) {
            GrpcStreamingSerializer<Resp> serializerIdentity = streamingSerializer(methodDescriptor);
            List<GrpcStreamingSerializer<Resp>> serializers = streamingSerializers(methodDescriptor, compressors);
            GrpcStreamingDeserializer<Req> deserializerIdentity = streamingDeserializer(methodDescriptor);
            List<GrpcStreamingDeserializer<Req>> deserializers =
                    streamingDeserializers(methodDescriptor, decompressors.decoders());
            CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
            CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                    .serializerDescriptor().contentType());
            CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                    .serializerDescriptor().contentType());
            verifyNoOverrides(streamingRoutes.put(methodDescriptor.httpPath(), new RouteProvider(
                    new ServiceAdapterHolder() {
                        private final StreamingHttpService service = new StreamingHttpService() {
                            @Override
                            public Single<StreamingHttpResponse> handle(
                                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                                    final StreamingHttpResponseFactory responseFactory) {
                                final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                                try {
                                    validateContentType(request.headers(), requestContentType);
                                    final GrpcStreamingSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                            request.headers(), serializerIdentity, serializers,
                                            GrpcStreamingSerializer::messageEncoding);
                                    final GrpcStreamingDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                            request.headers(), deserializerIdentity, deserializers,
                                            GrpcStreamingDeserializer::messageEncoding);
                                    final LazyContextMapSupplier responseContext = new LazyContextMapSupplier();
                                    return route.handle(
                                            new DefaultGrpcServiceContext(methodDescriptor.httpPath(), request::context,
                                                    responseContext, ctx),
                                            deserializer.deserialize(request.payloadBody(), allocator))
                                            .liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(
                                                    (Resp first, Publisher<Resp> following) -> newResponse(
                                                            responseFactory, responseContentType,
                                                            serializer.messageEncoding(), acceptedEncoding,
                                                            Publisher.from(first).concat(following),
                                                            serializer, allocator)))
                                            // In case `handle` returns an empty publisher, splice operator emits null:
                                            .map(httpResponse -> {
                                                if (httpResponse == null) {
                                                    httpResponse = newResponse(
                                                            responseFactory, responseContentType,
                                                            serializer.messageEncoding(), acceptedEncoding,
                                                            Publisher.empty(),
                                                            serializer, allocator);
                                                }
                                                if (responseContext.isInitialized()) {
                                                    httpResponse.context(responseContext.get());
                                                }
                                                return httpResponse;
                                            })
                                            .onErrorReturn(cause -> {
                                                LOGGER.debug(
                                                        "Unexpected exception from streaming response for path : {}",
                                                        methodDescriptor.httpPath(), cause);
                                                return newErrorResponse(responseFactory, responseContentType, cause,
                                                        allocator);
                                            });
                                } catch (Throwable t) {
                                    LOGGER.debug("Unexpected exception from streaming endpoint for path: {}",
                                            methodDescriptor.httpPath(), t);
                                    return succeeded(newErrorResponse(responseFactory, responseContentType, t,
                                            allocator));
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

                        @Override
                        public StreamingHttpService adaptor() {
                            return service;
                        }

                        @Override
                        public HttpExecutionStrategy serviceInvocationStrategy() {
                            return executionStrategy == null ? defaultStrategy() : executionStrategy;
                        }
                    }, route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no blocking-streaming route registered
                    // for the same path:
                    methodDescriptor.httpPath(), blockingStreamingRoutes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

        /**
         * async streaming request
         */
        <Req, Resp> void addRequestStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, RequestStreamingRoute<Req, Resp> route) {
            addStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
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
                    });
        }

        /**
         * async streaming response
         */
        <Req, Resp> void addResponseStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, ResponseStreamingRoute<Req, Resp> route) {
            addStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
                    new StreamingRoute<Req, Resp>() {
                        @Override
                        public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                            return request.firstOrError()
                                    .onErrorMap(t -> {
                                        if (t instanceof NoSuchElementException) {
                                            return new GrpcStatus(INVALID_ARGUMENT, t,
                                                    SINGLE_MESSAGE_EXPECTED_NONE_RECEIVED_MSG)
                                                    .asException();
                                        } else if (t instanceof IllegalArgumentException) {
                                            return new GrpcStatus(INVALID_ARGUMENT, t,
                                                    MORE_THAN_ONE_MESSAGE_RECEIVED_MSG).asException();
                                        } else {
                                            return t;
                                        }
                                    })
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
                    });
        }

        /**
         * synchronous aggregate
         */
        <Req, Resp> void addBlockingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, BlockingRoute<Req, Resp> route) {
            GrpcSerializer<Resp> serializerIdentity = serializer(methodDescriptor);
            List<GrpcSerializer<Resp>> serializers = serializers(methodDescriptor, compressors);
            GrpcDeserializer<Req> deserializerIdentity = deserializer(methodDescriptor);
            List<GrpcDeserializer<Req>> deserializers = deserializers(methodDescriptor, decompressors.decoders());
            CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
            CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                    .serializerDescriptor().contentType());
            CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                    .serializerDescriptor().contentType());
            verifyNoOverrides(blockingRoutes.put(methodDescriptor.httpPath(), new RouteProvider(
                    toStreamingHttpService(new BlockingHttpService() {
                        @Override
                        public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                                   final HttpResponseFactory responseFactory) {
                            try {
                                validateContentType(request.headers(), requestContentType);
                                final GrpcDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                        request.headers(), deserializerIdentity, deserializers,
                                        GrpcDeserializer::messageEncoding);
                                final LazyContextMapSupplier responseContext = new LazyContextMapSupplier();
                                final Resp rawResp = route.handle(
                                        new DefaultGrpcServiceContext(methodDescriptor.httpPath(), request::context,
                                                responseContext, ctx),
                                        deserializer.deserialize(request.payloadBody(),
                                                ctx.executionContext().bufferAllocator()));
                                final GrpcSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                        request.headers(), serializerIdentity, serializers,
                                        GrpcSerializer::messageEncoding);
                                final HttpResponse httpResponse = newResponse(responseFactory, responseContentType,
                                        serializer.messageEncoding(), acceptedEncoding)
                                        .payloadBody(serializer.serialize(rawResp,
                                                ctx.executionContext().bufferAllocator()));
                                if (responseContext.isInitialized()) {
                                    httpResponse.context(responseContext.get());
                                }
                                return httpResponse;
                            } catch (Throwable t) {
                                LOGGER.debug("Unexpected exception from blocking aggregated endpoint for path: {}",
                                        methodDescriptor.httpPath(), t);
                                return newErrorResponse(responseFactory, responseContentType, t,
                                        ctx.executionContext().bufferAllocator());
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
                    }, executionStrategy == null ? defaultStrategy() : executionStrategy), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no async-aggregated route registered
                    // for the same path:
                    methodDescriptor.httpPath(), routes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

        /**
         * synchronous streaming
         */
        <Req, Resp> void addBlockingStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, BlockingStreamingRoute<Req, Resp> route) {
            GrpcStreamingSerializer<Resp> serializerIdentity = streamingSerializer(methodDescriptor);
            List<GrpcStreamingSerializer<Resp>> serializers = streamingSerializers(methodDescriptor, compressors);
            GrpcStreamingDeserializer<Req> deserializerIdentity = streamingDeserializer(methodDescriptor);
            List<GrpcStreamingDeserializer<Req>> deserializers =
                    streamingDeserializers(methodDescriptor, decompressors.decoders());
            CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
            CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                    .serializerDescriptor().contentType());
            CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                    .serializerDescriptor().contentType());
            verifyNoOverrides(blockingStreamingRoutes.put(methodDescriptor.httpPath(),
                    new RouteProvider(toStreamingHttpService(new BlockingStreamingHttpService() {
                        @Override
                        public void handle(final HttpServiceContext ctx, final BlockingStreamingHttpRequest request,
                                           final BlockingStreamingHttpServerResponse response) throws Exception {
                            validateContentType(request.headers(), requestContentType);
                            final GrpcStreamingSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                    request.headers(), serializerIdentity, serializers,
                                    GrpcStreamingSerializer::messageEncoding);
                            final GrpcStreamingDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                    request.headers(), deserializerIdentity, deserializers,
                                    GrpcStreamingDeserializer::messageEncoding);
                            initResponse(response, responseContentType, serializer.messageEncoding(), acceptedEncoding);
                            final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                            final DefaultBlockingStreamingGrpcServerResponse<Resp> grpcResponse =
                                    new DefaultBlockingStreamingGrpcServerResponse<>(response, serializer, allocator);
                            final GrpcServiceContext serviceContext = new DefaultGrpcServiceContext(request.path(),
                                    // Use grpcResponse to preserve "sendMetaData" check
                                    request::context, grpcResponse::context, ctx);
                            try {
                                route.handle(serviceContext, deserializer.deserialize(request.payloadBody(),
                                        allocator), grpcResponse);
                            } catch (Throwable t) {
                                LOGGER.debug("Unexpected exception from blocking streaming endpoint for path: {}",
                                        methodDescriptor.httpPath(), t);
                                try {
                                    final HttpHeaders trailers = grpcResponse.trailers();
                                    if (trailers != null) {
                                        setStatus(trailers, t, allocator);
                                    } else {
                                        // Response meta-data aren't sent, populate headers with the status and send
                                        setStatus(response.headers(), t, allocator);
                                        grpcResponse.sendMetaData();
                                    }
                                } finally {
                                    // Error is propagated in trailers, payload should close normally.
                                    grpcResponse.close();
                                }
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
                    }, executionStrategy == null ? defaultStrategy() : executionStrategy), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no async-streaming route registered
                    // for the same path:
                    methodDescriptor.httpPath(), streamingRoutes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

        <Req, Resp> void addBlockingRequestStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy, BlockingRequestStreamingRoute<Req, Resp> route) {
            addBlockingStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
                    new BlockingStreamingRoute<Req, Resp>() {
                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                            final Resp resp = route.handle(ctx, request);
                            responseWriter.write(resp);
                            responseWriter.close();
                        }

                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final BlockingStreamingGrpcServerResponse<Resp> response) throws Exception {
                            final Resp resp = route.handle(ctx, request);
                            final GrpcPayloadWriter<Resp> responseWriter = response.sendMetaData();
                            responseWriter.write(resp);
                            responseWriter.close();
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    });
        }

        <Req, Resp> void addBlockingResponseStreamingRoute(
                MethodDescriptor<Req, Resp> methodDescriptor,
                BufferDecoderGroup decompressors, List<BufferEncoder> compressors,
                @Nullable GrpcExecutionStrategy executionStrategy,
                final BlockingResponseStreamingRoute<Req, Resp> route) {
            addBlockingStreamingRoute(methodDescriptor, decompressors, compressors, executionStrategy,
                    new BlockingStreamingRoute<Req, Resp>() {

                        private Req firstItem(final BlockingIterable<Req> request) throws Exception {
                            final Req firstItem;
                            try (BlockingIterator<Req> requestIterator = request.iterator()) {
                                if (!requestIterator.hasNext()) {
                                    throw new GrpcStatus(INVALID_ARGUMENT, null,
                                            SINGLE_MESSAGE_EXPECTED_NONE_RECEIVED_MSG).asException();
                                }
                                firstItem = requestIterator.next();
                                assert firstItem != null;
                                if (requestIterator.hasNext()) {
                                    // Consume the next item to make sure it's not a TerminalNotification with an error
                                    requestIterator.next();
                                    throw new GrpcStatus(INVALID_ARGUMENT, null,
                                            MORE_THAN_ONE_MESSAGE_RECEIVED_MSG).asException();
                                }
                            }
                            return firstItem;
                        }

                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
                            route.handle(ctx, firstItem(request), responseWriter);
                        }

                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final BlockingStreamingGrpcServerResponse<Resp> response) throws Exception {
                            route.handle(ctx, firstItem(request), response);
                        }

                        @Override
                        public void close() throws Exception {
                            route.close();
                        }

                        @Override
                        public void closeGracefully() throws Exception {
                            route.closeGracefully();
                        }
                    });
        }

        /**
         * Builds a {@link GrpcRouter}.
         *
         * @return {@link GrpcRouter}.
         */
        public GrpcRouter build() {
            return new GrpcRouter(
                    routes, streamingRoutes, blockingRoutes, blockingStreamingRoutes, executionStrategies);
        }
    }

    private static void verifyNoOverrides(@Nullable final Object oldValue, final String path,
                                          final Map<String, ?> alternativeMap) {
        if (oldValue != null || alternativeMap.containsKey(path)) {
            throw new IllegalStateException("Can not override already registered route for path: " + path);
        }
    }

    private static <Req> List<GrpcStreamingDeserializer<Req>> streamingDeserializers(
            MethodDescriptor<Req, ?> methodDescriptor, List<BufferDecoder> decompressors) {
        return GrpcUtils.streamingDeserializers(
                methodDescriptor.requestDescriptor().serializerDescriptor().serializer(), decompressors);
    }

    private static <Resp> List<GrpcStreamingSerializer<Resp>> streamingSerializers(
            MethodDescriptor<?, Resp> methodDescriptor, List<BufferEncoder> compressors) {
        return GrpcUtils.streamingSerializers(methodDescriptor.responseDescriptor().serializerDescriptor().serializer(),
                methodDescriptor.responseDescriptor().serializerDescriptor().bytesEstimator(), compressors);
    }

    private static <Resp> GrpcStreamingSerializer<Resp> streamingSerializer(
            MethodDescriptor<?, Resp> methodDescriptor) {
        return new GrpcStreamingSerializer<>(
                methodDescriptor.responseDescriptor().serializerDescriptor().bytesEstimator(),
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer());
    }

    private static <Req> GrpcStreamingDeserializer<Req> streamingDeserializer(
            MethodDescriptor<Req, ?> methodDescriptor) {
        return new GrpcStreamingDeserializer<>(
                methodDescriptor.requestDescriptor().serializerDescriptor().serializer());
    }

    private static <Req> List<GrpcDeserializer<Req>> deserializers(
            MethodDescriptor<Req, ?> methodDescriptor, List<BufferDecoder> decompressors) {
        return GrpcUtils.deserializers(methodDescriptor.requestDescriptor().serializerDescriptor().serializer(),
                decompressors);
    }

    private static <Resp> List<GrpcSerializer<Resp>> serializers(
            MethodDescriptor<?, Resp> methodDescriptor, List<BufferEncoder> compressors) {
        return GrpcUtils.serializers(methodDescriptor.responseDescriptor().serializerDescriptor().serializer(),
                methodDescriptor.responseDescriptor().serializerDescriptor().bytesEstimator(), compressors);
    }

    private static <Resp> GrpcSerializer<Resp> serializer(MethodDescriptor<?, Resp> methodDescriptor) {
        return new GrpcSerializer<>(methodDescriptor.responseDescriptor().serializerDescriptor().bytesEstimator(),
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer());
    }

    private static <Req> GrpcDeserializer<Req> deserializer(MethodDescriptor<Req, ?> methodDescriptor) {
        return new GrpcDeserializer<>(methodDescriptor.requestDescriptor().serializerDescriptor().serializer());
    }

    private static final class DefaultBlockingStreamingGrpcServerResponse<Resp>
            implements BlockingStreamingGrpcServerResponse<Resp> {

        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<DefaultBlockingStreamingGrpcServerResponse, HttpPayloadWriter>
                httpWriterUpdater = newUpdater(DefaultBlockingStreamingGrpcServerResponse.class,
                HttpPayloadWriter.class, "httpWriter");

        private final BlockingStreamingHttpServerResponse httpResponse;
        private final GrpcStreamingSerializer<Resp> serializer;
        private final BufferAllocator allocator;
        @Nullable
        private volatile HttpPayloadWriter<Buffer> httpWriter;

        DefaultBlockingStreamingGrpcServerResponse(final BlockingStreamingHttpServerResponse httpResponse,
                                                   final GrpcStreamingSerializer<Resp> serializer,
                                                   final BufferAllocator allocator) {
            this.httpResponse = httpResponse;
            this.serializer = serializer;
            this.allocator = allocator;
        }

        @Override
        public ContextMap context() {
            checkSent();
            return httpResponse.context();
        }

        @Override
        public GrpcPayloadWriter<Resp> sendMetaData() {
            final HttpPayloadWriter<Buffer> httpWriter = httpResponse.sendMetaData();
            this.httpWriter = httpWriter;
            final GrpcPayloadWriter<Resp> grpcPayloadWriter = new DefaultGrpcPayloadWriter<>(
                    serializer.serialize(httpWriter, allocator));
            // Set status OK before returning PayloadWriter because users can close it right away
            setStatusOk(httpWriter.trailers());
            return grpcPayloadWriter;
        }

        private void checkSent() {
            if (httpWriter != null) {
                throw new IllegalStateException("Response meta-data is already sent");
            }
        }

        @Nullable
        HttpHeaders trailers() {
            final HttpPayloadWriter<Buffer> httpWriter = this.httpWriter;
            return httpWriter != null ? httpWriter.trailers() : null;
        }

        void close() throws IOException {
            final HttpPayloadWriter<Buffer> httpWriter = this.httpWriter;
            if (httpWriter != null) {
                httpWriter.close();
            }
        }
    }

    private static final class DefaultGrpcPayloadWriter<Resp> implements GrpcPayloadWriter<Resp> {
        private final PayloadWriter<Resp> payloadWriter;

        DefaultGrpcPayloadWriter(final PayloadWriter<Resp> payloadWriter) {
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
        public void close(final Throwable cause) throws IOException {
            payloadWriter.close(cause);
        }

        @Override
        public void flush() throws IOException {
            payloadWriter.flush();
        }
    }

    private static final class RouteProvider implements AsyncCloseable {

        private final ServiceAdapterHolder serviceAdapterHolder;
        private final AsyncCloseable closeable;

        RouteProvider(final ServiceAdapterHolder serviceAdapterHolder,
                      final AsyncCloseable closeable) {
            this.serviceAdapterHolder = serviceAdapterHolder;
            this.closeable = closeable;
        }

        RouteProvider(final ServiceAdapterHolder serviceAdapterHolder,
                      final GracefulAutoCloseable closeable) {
            this(serviceAdapterHolder, toAsyncCloseable(closeable));
        }

        ServiceAdapterHolder serviceAdapterHolder() {
            return serviceAdapterHolder;
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }

        private static AsyncCloseable toAsyncCloseable(final GracefulAutoCloseable original) {
            return AsyncCloseables.toAsyncCloseable(graceful -> new Completable() {
                @Override
                protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                    try {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                    } catch (Throwable cause) {
                        handleExceptionFromOnSubscribe(subscriber, cause);
                        return;
                    }

                    try {
                        if (graceful) {
                            original.closeGracefully();
                        } else {
                            original.close();
                        }
                    } catch (Throwable t) {
                        subscriber.onError(t);
                        return;
                    }
                    subscriber.onComplete();
                }
            });
        }
    }
}
