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
package io.servicetalk.grpc.customtransport;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.grpc.api.GrpcBindableService;
import io.servicetalk.grpc.api.GrpcExecutionContext;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.api.MethodDescriptor;
import io.servicetalk.grpc.api.ParameterDescriptor;
import io.servicetalk.grpc.api.SerializerDescriptor;
import io.servicetalk.grpc.customtransport.Utils.ChannelGrpcServiceContext;
import io.servicetalk.grpc.customtransport.Utils.GrpcStreamingDeserializer;
import io.servicetalk.grpc.customtransport.Utils.GrpcStreamingSerializer;
import io.servicetalk.grpc.customtransport.Utils.UtilGrpcExecutionContext;
import io.servicetalk.serializer.api.StreamingDeserializer;
import io.servicetalk.serializer.api.StreamingSerializer;
import io.servicetalk.transport.netty.internal.GlobalExecutionContext;

import com.google.rpc.Status;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.grpc.api.GrpcStatusCode.INTERNAL;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.lang.Math.ceil;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;

final class InMemoryServerTransport implements ServerTransport {
    private static final AttributeKey<GrpcServiceContext> GRPC_SERVICE_CONTEXT_KEY =
            AttributeKey.newInstance("GrpcServiceContext");
    private static final Map<EventLoop, GrpcExecutionContext> EVENT_LOOP_GRPC_EXECUTION_CONTEXT_MAP =
            new ConcurrentHashMap<>();
    private final Map<String, Route> routeMap;
    private final BufferAllocator allocator;

    InMemoryServerTransport(final BufferAllocator allocator, final GrpcBindableService<?> grpcService)
            throws NoSuchMethodException, IllegalAccessException {
        this.allocator = requireNonNull(allocator);
        routeMap = new HashMap<>((int) ceil(grpcService.methodDescriptors().size() / 0.75));
        for (MethodDescriptor<?, ?> md : grpcService.methodDescriptors()) {
            routeMap.put(md.httpPath(), new Route(md, grpcService));
        }
    }

    @Override
    public Publisher<Buffer> handle(Channel channel,
                                    @Nullable final String clientId, final String method,
                                    final Publisher<Buffer> requestMessages) {
        Route route = routeMap.get(method);
        if (route == null) {
            return failed(GrpcStatusException.of(Status.newBuilder().setCode(UNIMPLEMENTED.value())
                    .setMessage("unimplemented method: " + method).build()));
        }
        try {
            return route.handle(getServiceContext(channel, allocator), allocator, requestMessages);
        } catch (Throwable cause) {
            return failed((cause instanceof GrpcStatusException) ? cause : mapUnknownException(cause));
        }
    }

    private static final class Route {
        private final StreamingDeserializer<?> deserializer;
        private final StreamingSerializer<?> serializer;
        private final GrpcBindableService<?> grpcService;
        private final MethodHandle methodHandle;
        private final byte methodSignature;

        Route(MethodDescriptor<?, ?> descriptor, final GrpcBindableService<?> grpcService)
                throws NoSuchMethodException, IllegalAccessException {
            requireNonNull(descriptor);
            this.grpcService = requireNonNull(grpcService);
            deserializer = new GrpcStreamingDeserializer<>(
                    descriptor.requestDescriptor().serializerDescriptor().serializer());
            SerializerDescriptor<?> respSerDesc = descriptor.responseDescriptor().serializerDescriptor();
            @SuppressWarnings({"unchecked", "rawtypes"})
            final StreamingSerializer<?> localSerializer =
                    new GrpcStreamingSerializer(respSerDesc.bytesEstimator(), respSerDesc.serializer());
            serializer = localSerializer;
            final MethodType mt;
            final ParameterDescriptor<?> respDesc = descriptor.responseDescriptor();
            final ParameterDescriptor<?> reqDesc = descriptor.requestDescriptor();
            if (respDesc.isStreaming()) {
                if (respDesc.isAsync()) {
                    if (reqDesc.isStreaming()) {
                        mt = methodType(Publisher.class, GrpcServiceContext.class, Publisher.class);
                        methodSignature = 0;
                    } else {
                        mt = methodType(Publisher.class, GrpcServiceContext.class, reqDesc.parameterClass());
                        methodSignature = 1;
                    }
                } else if (reqDesc.isStreaming()) { // 3 params!
                    mt = methodType(Void.TYPE, GrpcServiceContext.class, BlockingIterable.class,
                            GrpcPayloadWriter.class);
                    methodSignature = 2;
                } else { // 3 params!
                    mt = methodType(Void.TYPE, GrpcServiceContext.class, reqDesc.parameterClass(),
                            GrpcPayloadWriter.class);
                    methodSignature = 3;
                }
            } else if (respDesc.isAsync()) {
                assert reqDesc.isStreaming() == reqDesc.isAsync();
                if (reqDesc.isStreaming()) {
                    mt = methodType(Single.class, GrpcServiceContext.class, Publisher.class);
                    methodSignature = 4;
                } else {
                    mt = methodType(Single.class, GrpcServiceContext.class, reqDesc.parameterClass());
                    methodSignature = 5;
                }
            } else {
                assert !reqDesc.isAsync();
                if (reqDesc.isStreaming()) {
                    mt = methodType(respDesc.parameterClass(), GrpcServiceContext.class, BlockingIterable.class);
                    methodSignature = 6;
                } else {
                    mt = methodType(respDesc.parameterClass(), GrpcServiceContext.class, reqDesc.parameterClass());
                    methodSignature = 7;
                }
            }
            methodHandle = lookup().findVirtual(grpcService.getClass(), descriptor.javaMethodName(), mt);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Publisher<Buffer> handle(GrpcServiceContext ctx, BufferAllocator allocator, Publisher<Buffer> requestMessages)
                throws Throwable {
            final Publisher<Buffer> result;
            switch (methodSignature) {
                case 0:
                    result = serializer.serialize((Publisher) methodHandle.invoke(grpcService, ctx,
                            deserializer.deserialize(requestMessages, allocator)), allocator);
                    break;
                case 1:
                    result = deserializer.deserialize(requestMessages, allocator).flatMapMerge(req -> {
                        try {
                            return serializer.serialize((Publisher) methodHandle.invoke(grpcService, ctx, req),
                                    allocator);
                        } catch (Throwable t) {
                            return failed(t);
                        }
                    });
                    break;
                case 2:
                    result = defer(() -> {
                        ConnectablePayloadWriter payloadWriter = new ConnectablePayloadWriter();
                        GrpcPayloadWriter grpcWriter = new GrpcPayloadWriter() {
                            @Override
                            public void flush() throws IOException {
                                payloadWriter.flush();
                            }

                            @Override
                            public void close() throws IOException {
                                payloadWriter.close();
                            }

                            @Override
                            public void write(final Object o) throws IOException {
                                payloadWriter.write(o);
                            }

                            @Override
                            public void close(final Throwable cause) throws IOException {
                                payloadWriter.close(cause);
                            }
                        };
                        return ctx.executionContext().executor().submit(() -> {
                            try {
                                methodHandle.invoke(grpcService, ctx,
                                        deserializer.deserialize(requestMessages, allocator).toIterable(), grpcWriter);
                            } catch (Throwable t) {
                                throwException(t);
                            }
                        }).merge(serializer.serialize(payloadWriter.connect(), allocator));
                    });
                    break;
                case 3:
                    result = deserializer.deserialize(requestMessages, allocator).flatMapMerge(req -> {
                        ConnectablePayloadWriter payloadWriter = new ConnectablePayloadWriter();
                        GrpcPayloadWriter grpcWriter = new GrpcPayloadWriter() {
                            @Override
                            public void flush() throws IOException {
                                payloadWriter.flush();
                            }

                            @Override
                            public void close() throws IOException {
                                payloadWriter.close();
                            }

                            @Override
                            public void write(final Object o) throws IOException {
                                payloadWriter.write(o);
                            }

                            @Override
                            public void close(final Throwable cause) throws IOException {
                                payloadWriter.close(cause);
                            }
                        };
                        return ctx.executionContext().executor().submit(() -> {
                            try {
                                methodHandle.invoke(grpcService, ctx, req, grpcWriter);
                            } catch (Throwable t) {
                                throwException(t);
                            }
                        }).merge(serializer.serialize(payloadWriter.connect(), allocator));
                    });
                    break;
                case 4:
                    result = serializer.serialize(((Single) methodHandle.invoke(grpcService, ctx,
                            deserializer.deserialize(requestMessages, allocator))).toPublisher(), allocator);
                    break;
                case 5:
                    result = deserializer.deserialize(requestMessages, allocator).flatMapMerge(req -> {
                        try {
                            return serializer.serialize(((Single) methodHandle.invoke(grpcService, ctx, req))
                                    .toPublisher(), allocator);
                        } catch (Throwable t) {
                            return failed(t);
                        }
                    });
                    break;
                case 6:
                    result = serializer.serialize((Publisher) from(methodHandle.invoke(grpcService, ctx,
                                    deserializer.deserialize(requestMessages, allocator).toIterable())), allocator)
                            .subscribeOn(ctx.executionContext().executor());
                    break;
                case 7:
                    result = deserializer.deserialize(requestMessages, allocator).flatMapMerge(req -> {
                        try {
                            return serializer.serialize(
                                    (Publisher) from(methodHandle.invoke(grpcService, ctx, req)), allocator);
                        } catch (Throwable t) {
                            return failed(t);
                        }
                    }).subscribeOn(ctx.executionContext().executor());
                    break;
                default:
                    result = unexpectedMethodSignature();
                    break;
            }

            return applyErrorRecovery(result);
        }

        private Publisher<Buffer> unexpectedMethodSignature() {
            return failed(new IllegalStateException("unexpected methodSignature: " + (int) methodSignature));
        }

        private static Publisher<Buffer> applyErrorRecovery(Publisher<Buffer> publisher) {
            return publisher.onErrorMap(cause -> !(cause instanceof GrpcStatusException),
                    InMemoryServerTransport::mapUnknownException);
        }
    }

    private static GrpcStatusException mapUnknownException(Throwable cause) {
        return GrpcStatusException.of(Status.newBuilder().setCode(INTERNAL.value())
                .setMessage("unexpected exception: " + cause).build());
    }

    private static GrpcServiceContext getServiceContext(Channel channel, BufferAllocator allocator) {
        // GrpcServiceContext is 1:1 with the Channel for the request. Closing it also closes the connection.
        Attribute<GrpcServiceContext> attr = channel.attr(GRPC_SERVICE_CONTEXT_KEY);
        GrpcServiceContext serviceContext = attr.get();
        if (serviceContext != null) {
            return serviceContext;
        }

        // GrpcExecutionContext exposes IoExecutor which is 1:1 with EventLoop. You could also use a
        // FastThreadLocal to store this info as well.
        GrpcExecutionContext executionContext =
                EVENT_LOOP_GRPC_EXECUTION_CONTEXT_MAP.computeIfAbsent(channel.eventLoop(), el ->
                        new UtilGrpcExecutionContext(allocator, fromNettyEventLoop(el, false),
                                GlobalExecutionContext.globalExecutionContext().executor()));

        serviceContext = new ChannelGrpcServiceContext(channel, executionContext);
        attr.set(serviceContext);
        return serviceContext;
    }
}
