/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.grpc.api.GrpcUtils.DefaultMethodDescriptor;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.grpc.api.GrpcUtils.decompressors;
import static io.servicetalk.grpc.api.GrpcUtils.defaultToInt;
import static io.servicetalk.grpc.api.GrpcUtils.grpcContentType;
import static io.servicetalk.grpc.api.GrpcUtils.initRequest;
import static io.servicetalk.grpc.api.GrpcUtils.readGrpcMessageEncodingRaw;
import static io.servicetalk.grpc.api.GrpcUtils.serializerDeserializer;
import static io.servicetalk.grpc.api.GrpcUtils.toGrpcException;
import static io.servicetalk.grpc.api.GrpcUtils.validateResponseAndGetPayload;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_DEADLINE_KEY;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_PROTO_CONTENT_TYPE;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientCallFactory implements GrpcClientCallFactory {
    private static final String UNKNOWN_PATH = "";
    private static final Map<BufferEncoder, GrpcSerializer<?>> serializerMap = new ConcurrentHashMap<>(2);
    private static final Map<BufferEncoder, GrpcStreamingSerializer<?>> streamingSerializerMap =
            new ConcurrentHashMap<>(2);
    private final StreamingHttpClient streamingHttpClient;
    private final GrpcExecutionContext executionContext;
    @Nullable
    private final Duration defaultTimeout;

    DefaultGrpcClientCallFactory(final StreamingHttpClient streamingHttpClient,
                                 @Nullable final Duration defaultTimeout) {
        this.streamingHttpClient = requireNonNull(streamingHttpClient);
        executionContext = new DefaultGrpcExecutionContext(streamingHttpClient.executionContext());
        this.defaultTimeout = defaultTimeout;
    }

    @Deprecated
    @Override
    public <Req, Resp> ClientCall<Req, Resp> newCall(final GrpcSerializationProvider serializationProvider,
            final Class<Req> requestClass, final Class<Resp> responseClass) {
        return newCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> ClientCall<Req, Resp> newCall(final MethodDescriptor<Req, Resp> methodDescriptor,
                                                     final BufferDecoderGroup decompressors) {
        final HttpClient client = streamingHttpClient.asClient();
        GrpcSerializer<Req> serializerIdentity = serializer(methodDescriptor);
        GrpcDeserializer<Resp> deserializerIdentity = deserializer(methodDescriptor);
        List<GrpcDeserializer<Resp>> deserializers = deserializers(methodDescriptor, decompressors.decoders());
        CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
        CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                .serializerDescriptor().contentType());
        CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                .serializerDescriptor().contentType());
        return (metadata, request) -> {
            Duration timeout = timeoutForRequest(metadata.timeout());
            GrpcSerializer<Req> serializer = serializer(methodDescriptor, serializerIdentity,
                    metadata.requestCompressor());
            String mdPath = methodDescriptor.httpPath();
            HttpRequest httpRequest = client.post(UNKNOWN_PATH.equals(mdPath) ? metadata.path() : mdPath);
            initRequest(httpRequest, requestContentType, serializer.messageEncoding(), acceptedEncoding, timeout);
            httpRequest.payloadBody(serializer.serialize(request, client.executionContext().bufferAllocator()));
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            return (strategy == null ? client.request(httpRequest) : client.request(strategy, httpRequest))
                    .map(response -> validateResponseAndGetPayload(response, responseContentType,
                            client.executionContext().bufferAllocator(), readGrpcMessageEncodingRaw(response.headers(),
                                    deserializerIdentity, deserializers, GrpcDeserializer::messageEncoding)))
                    .onErrorMap(GrpcUtils::toGrpcException);
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        GrpcStreamingSerializer<Req> serializerIdentity = streamingSerializer(methodDescriptor);
        GrpcStreamingDeserializer<Resp> deserializerIdentity = streamingDeserializer(methodDescriptor);
        List<GrpcStreamingDeserializer<Resp>> deserializers = streamingDeserializers(methodDescriptor,
                decompressors.decoders());
        CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
        CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                .serializerDescriptor().contentType());
        CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                .serializerDescriptor().contentType());
        return (metadata, request) -> {
            Duration timeout = timeoutForRequest(metadata.timeout());
            GrpcStreamingSerializer<Req> serializer = streamingSerializer(methodDescriptor, serializerIdentity,
                    metadata.requestCompressor());
            String mdPath = methodDescriptor.httpPath();
            StreamingHttpRequest httpRequest = streamingHttpClient.post(UNKNOWN_PATH.equals(mdPath) ?
                    metadata.path() : mdPath);
            initRequest(httpRequest, requestContentType, serializer.messageEncoding(), acceptedEncoding, timeout);
            httpRequest.payloadBody(serializer.serialize(request,
                    streamingHttpClient.executionContext().bufferAllocator()));
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            return (strategy == null ? streamingHttpClient.request(httpRequest) :
                    streamingHttpClient.request(strategy, httpRequest))
                    .flatMapPublisher(response -> validateResponseAndGetPayload(response, responseContentType,
                            streamingHttpClient.executionContext().bufferAllocator(), readGrpcMessageEncodingRaw(
                                    response.headers(), deserializerIdentity, deserializers,
                                    GrpcStreamingDeserializer::messageEncoding)))
                    .onErrorMap(GrpcUtils::toGrpcException);
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newRequestStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, true, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        final StreamingClientCall<Req, Resp> streamingClientCall =
                newStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> streamingClientCall.request(metadata, request).firstOrError();
    }

    @Deprecated
    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newResponseStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        final StreamingClientCall<Req, Resp> streamingClientCall =
                newStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> streamingClientCall.request(metadata, Publisher.from(request));
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        final BlockingHttpClient client = streamingHttpClient.asBlockingClient();
        GrpcSerializer<Req> serializerIdentity = serializer(methodDescriptor);
        GrpcDeserializer<Resp> deserializerIdentity = deserializer(methodDescriptor);
        List<GrpcDeserializer<Resp>> deserializers = deserializers(methodDescriptor, decompressors.decoders());
        CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
        CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                .serializerDescriptor().contentType());
        CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                .serializerDescriptor().contentType());
        return (metadata, request) -> {
            Duration timeout = timeoutForRequest(metadata.timeout());
            GrpcSerializer<Req> serializer = serializer(methodDescriptor, serializerIdentity,
                    metadata.requestCompressor());
            String mdPath = methodDescriptor.httpPath();
            HttpRequest httpRequest = client.post(UNKNOWN_PATH.equals(mdPath) ? metadata.path() : mdPath);
            initRequest(httpRequest, requestContentType, serializer.messageEncoding(), acceptedEncoding, timeout);
            httpRequest.payloadBody(serializer.serialize(request, client.executionContext().bufferAllocator()));
            try {
                @Nullable
                final GrpcExecutionStrategy strategy = metadata.strategy();
                final HttpResponse response = strategy == null ? client.request(httpRequest) :
                        client.request(strategy, httpRequest);
                return validateResponseAndGetPayload(response, responseContentType,
                        client.executionContext().bufferAllocator(), readGrpcMessageEncodingRaw(response.headers(),
                                deserializerIdentity, deserializers, GrpcDeserializer::messageEncoding));
            } catch (Throwable cause) {
                throw toGrpcException(cause);
            }
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        GrpcStreamingSerializer<Req> serializerIdentity = streamingSerializer(methodDescriptor);
        GrpcStreamingDeserializer<Resp> deserializerIdentity = streamingDeserializer(methodDescriptor);
        List<GrpcStreamingDeserializer<Resp>> deserializers = streamingDeserializers(methodDescriptor,
                decompressors.decoders());
        CharSequence acceptedEncoding = decompressors.advertisedMessageEncoding();
        CharSequence requestContentType = grpcContentType(methodDescriptor.requestDescriptor()
                .serializerDescriptor().contentType());
        CharSequence responseContentType = grpcContentType(methodDescriptor.responseDescriptor()
                .serializerDescriptor().contentType());
        final BlockingStreamingHttpClient client = streamingHttpClient.asBlockingStreamingClient();
        return (metadata, request) -> {
            Duration timeout = timeoutForRequest(metadata.timeout());
            GrpcStreamingSerializer<Req> serializer = streamingSerializer(methodDescriptor, serializerIdentity,
                    metadata.requestCompressor());
            String mdPath = methodDescriptor.httpPath();
            BlockingStreamingHttpRequest httpRequest = client.post(UNKNOWN_PATH.equals(mdPath) ?
                    metadata.path() : mdPath);
            initRequest(httpRequest, requestContentType, serializer.messageEncoding(), acceptedEncoding, timeout);
            httpRequest.payloadBody(serializer.serialize(request,
                    streamingHttpClient.executionContext().bufferAllocator()));
            try {
                @Nullable
                final GrpcExecutionStrategy strategy = metadata.strategy();
                final BlockingStreamingHttpResponse response = strategy == null ? client.request(httpRequest) :
                        client.request(strategy, httpRequest);
                return validateResponseAndGetPayload(response.toStreamingResponse(), responseContentType,
                        client.executionContext().bufferAllocator(), readGrpcMessageEncodingRaw(
                                response.headers(), deserializerIdentity, deserializers,
                                GrpcStreamingDeserializer::messageEncoding)).toIterable();
            } catch (Throwable cause) {
                throw toGrpcException(cause);
            }
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingRequestStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        final BlockingStreamingClientCall<Req, Resp> streamingClientCall =
                newBlockingStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> {
            try (BlockingIterator<Resp> iterator = streamingClientCall.request(metadata, request).iterator()) {
                final Resp firstItem = iterator.next();
                assert firstItem != null;
                if (iterator.hasNext()) {
                    iterator.next(); // Consume the next item to make sure it's not a TerminalNotification with an error
                    throw new IllegalArgumentException("More than one response message received");
                }
                return firstItem;
            }
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingResponseStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        false, false, requestClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_PROTO_CONTENT_TYPE,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        final BlockingStreamingClientCall<Req, Resp> streamingClientCall =
                newBlockingStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> streamingClientCall.request(metadata, singletonBlockingIterable(request));
    }

    @Override
    public GrpcExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public Completable closeAsync() {
        return streamingHttpClient.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return streamingHttpClient.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return streamingHttpClient.onClose();
    }

    /**
     * Determines the timeout for a new request using three potential sources; the deadline in the async context, the
     * request timeout, and the client default. The timeout will be the lesser of the context and request timeouts or if
     * neither are present, the default timeout.
     *
     * @param metaDataTimeout the timeout specified in client metadata or null for no timeout
     * @return The timeout {@link Duration}, potentially negative or null if no timeout.
     */
    private @Nullable Duration timeoutForRequest(@Nullable Duration metaDataTimeout) {
        Long deadline = AsyncContext.get(GRPC_DEADLINE_KEY);
        @Nullable
        Duration contextTimeout = null != deadline ? Duration.ofNanos(deadline - System.nanoTime()) : null;

        @Nullable
        Duration timeout = null != contextTimeout ?
            null == metaDataTimeout || contextTimeout.compareTo(metaDataTimeout) <= 0 ?
                contextTimeout : metaDataTimeout
                : metaDataTimeout;

        return null != timeout ? timeout : defaultTimeout;
    }

    private static <Resp> List<GrpcStreamingDeserializer<Resp>> streamingDeserializers(
            MethodDescriptor<?, Resp> methodDescriptor, List<BufferDecoder> decompressors) {
        return GrpcUtils.streamingDeserializers(
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer(), decompressors);
    }

    private static <Req> GrpcStreamingSerializer<Req> streamingSerializer(
            MethodDescriptor<Req, ?> methodDescriptor) {
        return new GrpcStreamingSerializer<>(
                methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                methodDescriptor.requestDescriptor().serializerDescriptor().serializer());
    }

    private static <Resp> GrpcStreamingDeserializer<Resp> streamingDeserializer(
            MethodDescriptor<?, Resp> methodDescriptor) {
        return new GrpcStreamingDeserializer<>(
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer());
    }

    private static <Resp> List<GrpcDeserializer<Resp>> deserializers(
            MethodDescriptor<?, Resp> methodDescriptor, List<BufferDecoder> decompressors) {
        return GrpcUtils.deserializers(
                methodDescriptor.responseDescriptor().serializerDescriptor().serializer(), decompressors);
    }

    private static <Req> GrpcSerializer<Req> serializer(MethodDescriptor<Req, ?> methodDescriptor) {
        return new GrpcSerializer<>(methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                methodDescriptor.requestDescriptor().serializerDescriptor().serializer());
    }

    @SuppressWarnings("unchecked")
    private static <Req> GrpcSerializer<Req> serializer(
            MethodDescriptor<Req, ?> methodDescriptor, GrpcSerializer<Req> serializerIdentity,
            @Nullable BufferEncoder compressor) {
        return compressor == null || compressor == identityEncoder() ? serializerIdentity :
                (GrpcSerializer<Req>) serializerMap.computeIfAbsent(compressor, key -> new GrpcSerializer<>(
                        methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                        methodDescriptor.requestDescriptor().serializerDescriptor().serializer(), key));
    }

    @SuppressWarnings("unchecked")
    private static <Req> GrpcStreamingSerializer<Req> streamingSerializer(
            MethodDescriptor<Req, ?> methodDescriptor, GrpcStreamingSerializer<Req> serializerIdentity,
            @Nullable BufferEncoder compressor) {
        return compressor == null || compressor == identityEncoder() ? serializerIdentity :
                (GrpcStreamingSerializer<Req>) streamingSerializerMap.computeIfAbsent(compressor, key ->
                        new GrpcStreamingSerializer<>(
                                methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                                methodDescriptor.requestDescriptor().serializerDescriptor().serializer(), key));
    }

    private static <Resp> GrpcDeserializer<Resp> deserializer(MethodDescriptor<?, Resp> methodDescriptor) {
        return new GrpcDeserializer<>(methodDescriptor.responseDescriptor().serializerDescriptor().serializer());
    }
}
