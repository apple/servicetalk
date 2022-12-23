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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcExecutionContext;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
import io.servicetalk.grpc.api.MethodDescriptor;
import io.servicetalk.grpc.customtransport.Utils.UtilGrpcExecutionContext;

import io.netty.channel.EventLoopGroup;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.grpc.customtransport.Utils.deserializeResp;
import static io.servicetalk.grpc.customtransport.Utils.serializeReq;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoopGroup;

final class ClientTransportGrpcCallFactory implements GrpcClientCallFactory {
    private final ClientTransport transport;
    private final GrpcExecutionContext ctx;
    private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

    ClientTransportGrpcCallFactory(ClientTransport transport, EventLoopGroup group, boolean isIoThreadSupported) {
        this.transport = transport;
        ctx = new UtilGrpcExecutionContext(DEFAULT_ALLOCATOR, fromNettyEventLoopGroup(group, isIoThreadSupported),
                globalExecutionContext().executor());
    }

    @Override
    public <Req, Resp> ClientCall<Req, Resp> newCall(final MethodDescriptor<Req, Resp> methodDescriptor,
                                                     final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.from(request), ctx.bufferAllocator())),
                ctx.bufferAllocator()).firstOrError();
    }

    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(request, ctx.bufferAllocator())),
                ctx.bufferAllocator());
    }

    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(request, ctx.bufferAllocator())),
                ctx.bufferAllocator()).firstOrError();
    }

    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.from(request), ctx.bufferAllocator())),
                ctx.bufferAllocator());
    }

    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.from(request), ctx.bufferAllocator())),
                ctx.bufferAllocator()).firstOrError().toFuture().get();
    }

    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.fromIterable(request), ctx.bufferAllocator())),
                ctx.bufferAllocator()).toIterable();
    }

    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.fromIterable(request), ctx.bufferAllocator())),
                ctx.bufferAllocator()).firstOrError().toFuture().get();
    }

    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        return (metadata, request) -> deserializeResp(methodDescriptor).deserialize(
                transport.send(methodDescriptor.httpPath(),
                        serializeReq(methodDescriptor)
                                .serialize(Publisher.from(request), ctx.bufferAllocator())),
                ctx.bufferAllocator()).toIterable();
    }

    @Override
    public GrpcExecutionContext executionContext() {
        return ctx;
    }

    @Override
    public Completable onClose() {
        return closeable.onClose();
    }

    @Override
    public Completable onClosing() {
        return closeable.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }

    @Deprecated
    @Override
    public <Req, Resp> ClientCall<Req, Resp> newCall(final GrpcSerializationProvider serializationProvider,
                                                     final Class<Req> requestClass,
                                                     final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        throw new UnsupportedOperationException();
    }
}
