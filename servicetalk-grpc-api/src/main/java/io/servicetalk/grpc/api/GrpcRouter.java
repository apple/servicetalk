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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceToOffloadedStreamingHttpService;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_CONTENT_TYPE;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

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
        final StreamingHttpResponse response = newErrorResponse(responseFactory, GRPC_CONTENT_TYPE,
                STATUS_UNIMPLEMENTED.asException(), ctx.executionContext().bufferAllocator());
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
            verifyNoOverrides(allRoutes.put(path,
                            StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(
                                    adapterHolder.serviceInvocationStrategy(),
                                    executionContext.executor(),
                                    IoThreadFactory.IoThread::currentThreadIsIoThread,
                                    route)),
                    path, emptyMap());
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
                    new RouteProvider(executionContext -> toStreamingHttpService(
                    new HttpService() {
                        @Override
                        public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request,
                                                           final HttpResponseFactory responseFactory) {
                            try {
                                validateContentType(request.headers(), requestContentType);
                                GrpcDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                        request.headers(), deserializerIdentity, deserializers,
                                        GrpcDeserializer::messageEncoding);
                                return route.handle(new DefaultGrpcServiceContext(methodDescriptor.httpPath(), ctx),
                                        deserializer.deserialize(request.payloadBody(),
                                                ctx.executionContext().bufferAllocator()))
                                        .map(rawResp -> {
                                            GrpcSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                                    request.headers(), serializerIdentity, serializers,
                                                    GrpcSerializer::messageEncoding);
                                            return newResponse(responseFactory, responseContentType,
                                                    serializer.messageEncoding(), acceptedEncoding)
                                                    .payloadBody(serializer.serialize(rawResp,
                                                            ctx.executionContext().bufferAllocator()));
                                        })
                                        .onErrorReturn(cause -> newErrorResponse(responseFactory,
                                            responseContentType, cause, ctx.executionContext().bufferAllocator()));
                            } catch (Throwable t) {
                                return succeeded(newErrorResponse(responseFactory, responseContentType, t,
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
                    methodDescriptor.httpPath(), blockingRoutes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

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
            verifyNoOverrides(streamingRoutes.put(methodDescriptor.httpPath(), new RouteProvider(executionContext -> {
                        final StreamingHttpService service = new StreamingHttpService() {
                            @Override
                            public Single<StreamingHttpResponse> handle(
                                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                                    final StreamingHttpResponseFactory responseFactory) {
                                try {
                                    validateContentType(request.headers(), requestContentType);
                                    GrpcStreamingSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                            request.headers(), serializerIdentity, serializers,
                                            GrpcStreamingSerializer::messageEncoding);
                                    GrpcStreamingDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                            request.headers(), deserializerIdentity, deserializers,
                                            GrpcStreamingDeserializer::messageEncoding);
                                    final Publisher<Resp> response = route.handle(
                                            new DefaultGrpcServiceContext(methodDescriptor.httpPath(), ctx),
                                            deserializer.deserialize(request.payloadBody(),
                                                    ctx.executionContext().bufferAllocator()));
                                    return succeeded(newResponse(responseFactory, responseContentType,
                                            serializer.messageEncoding(), acceptedEncoding, response, serializer,
                                            ctx.executionContext().bufferAllocator()));
                                } catch (Throwable t) {
                                    return succeeded(newErrorResponse(responseFactory, responseContentType, t,
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
                    methodDescriptor.httpPath(), blockingStreamingRoutes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

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
            verifyNoOverrides(blockingRoutes.put(methodDescriptor.httpPath(), new RouteProvider(executionContext ->
                    toStreamingHttpService(new BlockingHttpService() {
                        @Override
                        public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                                   final HttpResponseFactory responseFactory) {
                            try {
                                validateContentType(request.headers(), requestContentType);
                                GrpcDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                        request.headers(), deserializerIdentity, deserializers,
                                        GrpcDeserializer::messageEncoding);
                                final Resp rawResp = route.handle(
                                        new DefaultGrpcServiceContext(methodDescriptor.httpPath(), ctx),
                                        deserializer.deserialize(request.payloadBody(),
                                                ctx.executionContext().bufferAllocator()));
                                GrpcSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                        request.headers(), serializerIdentity, serializers,
                                        GrpcSerializer::messageEncoding);
                                return newResponse(responseFactory, responseContentType,
                                        serializer.messageEncoding(), acceptedEncoding)
                                        .payloadBody(serializer.serialize(rawResp,
                                                ctx.executionContext().bufferAllocator()));
                            } catch (Throwable t) {
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
                    }, strategy -> executionStrategy == null ? strategy : executionStrategy),
                    () -> toStreaming(route), () -> toRequestStreamingRoute(route),
                    () -> toResponseStreamingRoute(route), () -> toRoute(route), route)),
                    // We only assume duplication across blocking and async variant of the same API and not between
                    // aggregated and streaming. Therefore, verify that there is no async-aggregated route registered
                    // for the same path:
                    methodDescriptor.httpPath(), routes);
            executionStrategies.put(methodDescriptor.httpPath(), executionStrategy);
        }

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
                    new RouteProvider(executionContext -> toStreamingHttpService(new BlockingStreamingHttpService() {
                        @Override
                        public void handle(final HttpServiceContext ctx, final BlockingStreamingHttpRequest request,
                                           final BlockingStreamingHttpServerResponse response) throws Exception {
                            validateContentType(request.headers(), requestContentType);
                            GrpcStreamingSerializer<Resp> serializer = negotiateAcceptedEncodingRaw(
                                    request.headers(), serializerIdentity, serializers,
                                    GrpcStreamingSerializer::messageEncoding);
                            GrpcStreamingDeserializer<Req> deserializer = readGrpcMessageEncodingRaw(
                                    request.headers(), deserializerIdentity, deserializers,
                                    GrpcStreamingDeserializer::messageEncoding);
                            final GrpcServiceContext serviceContext =
                                    new DefaultGrpcServiceContext(request.path(), ctx);
                            initResponse(response, responseContentType, serializer.messageEncoding(), acceptedEncoding);
                            final HttpPayloadWriter<Buffer> bufferWriter = response.sendMetaData();
                            final DefaultGrpcPayloadWriter<Resp> grpcPayloadWriter =
                                    new DefaultGrpcPayloadWriter<>(serializer.serialize(bufferWriter,
                                            ctx.executionContext().bufferAllocator()));
                            try {
                                // Set status OK before invoking handle methods because users can close PayloadWriter
                                setStatusOk(bufferWriter.trailers());
                                route.handle(serviceContext, deserializer.deserialize(request.payloadBody(),
                                        ctx.executionContext().bufferAllocator()), grpcPayloadWriter);
                            } catch (Throwable t) {
                                try {
                                    setStatus(bufferWriter.trailers(), t, ctx.executionContext().bufferAllocator());
                                } finally {
                                    // Error is propagated in trailers, payload should close normally.
                                    grpcPayloadWriter.close();
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
                    }, strategy -> executionStrategy == null ? strategy : executionStrategy),
                    () -> toStreaming(route), () -> toRequestStreamingRoute(route),
                    () -> toResponseStreamingRoute(route), () -> toRoute(route), route)),
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
                        @Override
                        public void handle(final GrpcServiceContext ctx, final BlockingIterable<Req> request,
                                           final GrpcPayloadWriter<Resp> responseWriter) throws Exception {
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
                            route.handle(ctx, firstItem, responseWriter);
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
            return new GrpcRouter(routes, streamingRoutes, blockingRoutes, blockingStreamingRoutes);
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
