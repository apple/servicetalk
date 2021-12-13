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
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.grpc.api.GrpcExecutionContext;
import io.servicetalk.grpc.api.GrpcExecutionStrategies;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.MethodDescriptor;
import io.servicetalk.grpc.netty.TesterProto;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingDeserializer;
import io.servicetalk.serializer.api.StreamingSerializer;
import io.servicetalk.serializer.utils.FramedDeserializerOperator;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

final class Utils {
    private static final int METADATA_SIZE = 5; // 1 byte for compression flag and 4 bytes for length of data
    private static final byte FLAG_UNCOMPRESSED = 0x0;
    private static final byte FLAG_COMPRESSED = 0x1;
    private Utils() {
    }

    static final class UtilGrpcExecutionContext implements GrpcExecutionContext {
        private final BufferAllocator allocator;
        private final IoExecutor ioExecutor;
        private final Executor executor;

        UtilGrpcExecutionContext(final BufferAllocator allocator,
                                 final IoExecutor ioExecutor, final Executor executor) {
            this.allocator = allocator;
            this.ioExecutor = ioExecutor;
            this.executor = executor;
        }

        @Override
        public BufferAllocator bufferAllocator() {
            return allocator;
        }

        @Override
        public IoExecutor ioExecutor() {
            return ioExecutor;
        }

        @Override
        public Executor executor() {
            return executor;
        }

        @Override
        public GrpcExecutionStrategy executionStrategy() {
            return GrpcExecutionStrategies.offloadNever();
        }
    }

    static final class ChannelGrpcServiceContext implements GrpcServiceContext {
        private final ListenableAsyncCloseable closeAsync;
        private final GrpcExecutionContext ctx;

        ChannelGrpcServiceContext(Channel channel, GrpcExecutionContext ctx) {
            closeAsync = toListenableAsyncCloseable(new AsyncCloseable() {
                private final Completable closeAsync = new SubscribableCompletable() {
                    @Override
                    protected void handleSubscribe(final Subscriber subscriber) {
                        ChannelFuture closeFuture;
                        try {
                            subscriber.onSubscribe(IGNORE_CANCEL);
                            closeFuture = channel.close();
                        } catch (Throwable cause) {
                            handleExceptionFromOnSubscribe(subscriber, cause);
                            return;
                        }
                        closeFuture.addListener(f -> {
                            Throwable cause = f.cause();
                            if (cause == null) {
                                subscriber.onComplete();
                            } else {
                                subscriber.onError(cause);
                            }
                        });
                    }
                };

                @Override
                public Completable closeAsync() {
                    return closeAsync;
                }
            });
            this.ctx = Objects.requireNonNull(ctx);
        }

        @Override
        public Completable closeAsync() {
            return closeAsync.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeAsync.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return closeAsync.onClose();
        }

        @Deprecated
        @Override
        public String path() {
            return "<deprecated>";
        }

        @Override
        public SocketAddress localAddress() {
            return InMemorySocketAddress.INSTANCE;
        }

        @Override
        public SocketAddress remoteAddress() {
            return InMemorySocketAddress.INSTANCE;
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public GrpcExecutionContext executionContext() {
            return ctx;
        }

        @Nullable
        @Override
        public <T> T socketOption(final SocketOption<T> option) {
            return null;
        }

        @Override
        public GrpcProtocol protocol() {
            return InMemoryGrpcProtocol.INSTANCE;
        }

        @Deprecated
        @Override
        public List<ContentCodec> supportedMessageCodings() {
            return Collections.emptyList();
        }
    }

    private static final class InMemorySocketAddress extends SocketAddress {
        static final SocketAddress INSTANCE = new InMemorySocketAddress();
        private InMemorySocketAddress() {
        }

        @Override
        public String toString() {
            return "InMemory";
        }
    }

    private static final class InMemoryGrpcProtocol implements GrpcServiceContext.GrpcProtocol {
        static final GrpcServiceContext.GrpcProtocol INSTANCE = new InMemoryGrpcProtocol();
        private InMemoryGrpcProtocol() {
        }

        @Override
        public String name() {
            return "gRPC";
        }

        @Override
        public HttpProtocolVersion httpProtocol() {
            return HttpProtocolVersion.HTTP_2_0;
        }
    }

    static TesterProto.TestResponse newResp(String msg) {
        return TesterProto.TestResponse.newBuilder().setMessage(msg).build();
    }

    static <Resp> StreamingDeserializer<Resp> deserializeResp(MethodDescriptor<?, Resp> methodDescriptor) {
        return new GrpcStreamingDeserializer<>(
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer());
    }

    static <Req> StreamingSerializer<Req> serializeReq(MethodDescriptor<Req, ?> methodDescriptor) {
        return new GrpcStreamingSerializer<>(
                methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                methodDescriptor.requestDescriptor().serializerDescriptor().serializer());
    }

    static final class GrpcStreamingDeserializer<T> implements StreamingDeserializer<T> {
        private final Deserializer<T> serializer;

        GrpcStreamingDeserializer(final Deserializer<T> serializer) {
            this.serializer = requireNonNull(serializer);
        }

        @Override
        public Publisher<T> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
            return serializedData.liftSync(new FramedDeserializerOperator<>(serializer, GrpcDeframer::new, allocator))
                    .flatMapConcatIterable(identity());
        }

        private static final class GrpcDeframer implements BiFunction<Buffer, BufferAllocator, Buffer> {
            private int expectedLength = -1;

            @Nullable
            @Override
            public Buffer apply(final Buffer buffer, final BufferAllocator allocator) {
                if (expectedLength < 0) {
                    if (buffer.readableBytes() < METADATA_SIZE) {
                        return null;
                    }
                    if (isCompressed(buffer)) {
                        throw new SerializationException("Compressed flag set, compression not supported");
                    }
                    expectedLength = buffer.readInt();
                    if (expectedLength < 0) {
                        throw new SerializationException("Message-Length invalid: " + expectedLength);
                    }
                }
                if (buffer.readableBytes() < expectedLength) {
                    return null;
                }
                Buffer result = buffer.readBytes(expectedLength);
                expectedLength = -1;
                return result;
            }
        }

        private static boolean isCompressed(Buffer buffer) throws SerializationException {
            final byte compressionFlag = buffer.readByte();
            if (compressionFlag == FLAG_UNCOMPRESSED) {
                return false;
            } else if (compressionFlag == FLAG_COMPRESSED) {
                return true;
            }
            throw new SerializationException("Compression flag must be 0 or 1 but was: " + compressionFlag);
        }
    }

    static final class GrpcStreamingSerializer<T> implements StreamingSerializer<T> {
        private final ToIntFunction<T> serializedBytesEstimator;
        private final Serializer<T> serializer;

        GrpcStreamingSerializer(final ToIntFunction<T> serializedBytesEstimator,
                                final Serializer<T> serializer) {
            this.serializedBytesEstimator = requireNonNull(serializedBytesEstimator);
            this.serializer = requireNonNull(serializer);
        }

        @Override
        public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
            return toSerialize.map(t -> {
                final int sizeEstimate = serializedBytesEstimator.applyAsInt(t);
                Buffer buffer = allocator.newBuffer(METADATA_SIZE + sizeEstimate);
                final int writerIndexBefore = buffer.writerIndex();
                buffer.writerIndex(writerIndexBefore + METADATA_SIZE);
                serializer.serialize(t, allocator, buffer);
                // Compression isn't supported for this example.
                buffer.setByte(writerIndexBefore, FLAG_UNCOMPRESSED);
                buffer.setInt(writerIndexBefore + 1, buffer.writerIndex() - writerIndexBefore - METADATA_SIZE);
                return buffer;
            });
        }
    }
}
