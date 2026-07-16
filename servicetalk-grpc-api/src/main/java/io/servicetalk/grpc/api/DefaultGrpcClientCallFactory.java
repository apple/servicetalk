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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.grpc.api.GrpcUtils.DefaultMethodDescriptor;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.grpc.api.GrpcHeaderValues.GRPC_CONTENT_TYPE_PROTO_SUFFIX;
import static io.servicetalk.grpc.api.GrpcUtils.decompressors;
import static io.servicetalk.grpc.api.GrpcUtils.defaultToInt;
import static io.servicetalk.grpc.api.GrpcUtils.grpcContentType;
import static io.servicetalk.grpc.api.GrpcUtils.initRequest;
import static io.servicetalk.grpc.api.GrpcUtils.readGrpcMessageEncodingRaw;
import static io.servicetalk.grpc.api.GrpcUtils.serializerDeserializer;
import static io.servicetalk.grpc.api.GrpcUtils.validateResponseAndGetPayload;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_DEADLINE_KEY;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientCallFactory implements GrpcClientCallFactory {
    private static final String UNKNOWN_PATH = "";
    private static final String NO_RESPONSE_MESSAGE = "No value received for unary call";
    private static final String TOO_MANY_RESPONSES_MESSAGE = "More than one value received for unary call";
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
                        false, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> ClientCall<Req, Resp> newCall(final MethodDescriptor<Req, Resp> methodDescriptor,
                                                     final BufferDecoderGroup decompressors) {
        // Delegate to the streaming call so a unary request benefits from the streaming response handling that
        // reads grpc-status from the response headers and fails fast (cancelling the request) for a Trailers-Only
        // error response. The aggregated HttpClient would instead wait for the whole response to aggregate, which
        // never completes if the server rejects the request before consuming a flow-control-blocked request body.
        final StreamingClientCall<Req, Resp> streamingCall = newStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> streamingCall.request(metadata, Publisher.from(request))
                .firstOrError()
                .onErrorMap(DefaultGrpcClientCallFactory::toUnaryResponseGrpcStatusException);
    }

    // Upstream errors are already GrpcStatusException (mapped by newStreamingCall); the only other errors reaching
    // here come from firstOrError's cardinality check. Match grpc-java by reporting INTERNAL when a unary call does
    // not receive exactly one response message.
    private static Throwable toUnaryResponseGrpcStatusException(final Throwable cause) {
        if (cause instanceof GrpcStatusException) {
            return cause;
        }
        return new GrpcStatusException(new GrpcStatus(GrpcStatusCode.INTERNAL,
                cause instanceof NoSuchElementException ? NO_RESPONSE_MESSAGE : TOO_MANY_RESPONSES_MESSAGE));
    }

    @Deprecated
    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, true, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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
            initRequest(httpRequest, metadata, requestContentType, serializer.messageEncoding(), acceptedEncoding,
                    timeout);
            httpRequest.payloadBody(serializer.serialize(request,
                    streamingHttpClient.executionContext().bufferAllocator()));
            return streamingHttpClient.request(httpRequest)
                    .flatMapPublisher(response -> {
                        try {

                            extractResponseContext(response, metadata);
                            return validateResponseAndGetPayload(response, responseContentType,
                                    streamingHttpClient.executionContext().bufferAllocator(),
                                    readGrpcMessageEncodingRaw(response.headers(), deserializerIdentity, deserializers,
                                            GrpcStreamingDeserializer::messageEncoding), httpRequest.requestTarget());
                        } catch (Throwable t) {
                            toSource(response.messageBody()).subscribe(CancelImmediatelySubscriber.INSTANCE);
                            return Publisher.failed(GrpcStatusException.fromThrowable(t));
                        }
                    })
                    .onErrorMap(GrpcStatusException::fromThrowable);
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newRequestStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, true, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, true, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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
                        false, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, true, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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
                        false, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, responseClass), defaultToInt()),
                decompressors(serializationProvider.supportedMessageCodings()));
    }

    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(
            final MethodDescriptor<Req, Resp> methodDescriptor, final BufferDecoderGroup decompressors) {
        // Delegate to the blocking streaming call so a unary request benefits from the streaming response handling
        // that fails fast for a Trailers-Only error response instead of blocking on the aggregated response (which
        // never completes if the server rejects the request before consuming a flow-control-blocked request body).
        final BlockingStreamingClientCall<Req, Resp> streamingCall =
                newBlockingStreamingCall(methodDescriptor, decompressors);
        return (metadata, request) -> {
            try (BlockingIterator<Resp> iterator =
                         streamingCall.request(metadata, singletonBlockingIterable(request)).iterator()) {
                // hasNext() reads through to the terminal, so a Trailers-Only error / grpc-status trailer surfaces
                // here. Match grpc-java by reporting INTERNAL when a unary call does not receive exactly one message.
                if (!iterator.hasNext()) {
                    throw new GrpcStatusException(new GrpcStatus(GrpcStatusCode.INTERNAL, NO_RESPONSE_MESSAGE));
                }
                final Resp firstItem = iterator.next();
                if (iterator.hasNext()) {
                    iterator.next(); // Throws if this is an error terminal; otherwise it is an unexpected 2nd message.
                    throw new GrpcStatusException(new GrpcStatus(GrpcStatusCode.INTERNAL, TOO_MANY_RESPONSES_MESSAGE));
                }
                return firstItem;
            } catch (GrpcStatusException e) {
                throw e;
            } catch (Throwable cause) {
                throw GrpcStatusException.fromThrowable(cause);
            }
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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
            initRequest(httpRequest, metadata, requestContentType, serializer.messageEncoding(), acceptedEncoding,
                    timeout);
            httpRequest.payloadBody(serializer.serialize(request,
                    streamingHttpClient.executionContext().bufferAllocator()));

            final BlockingStreamingHttpResponse response;
            try {
                response = client.request(httpRequest);
            } catch (Throwable cause) {
                throw GrpcStatusException.fromThrowable(cause);
            }
            try {
                extractResponseContext(response, metadata);
                return validateResponseAndGetPayload(response.toStreamingResponse(), responseContentType,
                        client.executionContext().bufferAllocator(), readGrpcMessageEncodingRaw(response.headers(),
                                deserializerIdentity, deserializers, GrpcStreamingDeserializer::messageEncoding),
                        httpRequest.requestTarget()).toIterable();
            } catch (Throwable t) {
                response.messageBody().iterator().close();
                throw GrpcStatusException.fromThrowable(t);
            }
        };
    }

    @Deprecated
    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
            final Class<Resp> responseClass) {
        return newBlockingRequestStreamingCall(new DefaultMethodDescriptor<>(UNKNOWN_PATH,
                        true, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        false, false, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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
                        false, false, requestClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
                        serializerDeserializer(serializationProvider, requestClass), defaultToInt(),
                        true, false, responseClass, GRPC_CONTENT_TYPE_PROTO_SUFFIX,
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

    @Override
    public Completable onClosing() {
        return streamingHttpClient.onClosing();
    }

    /**
     * Determines the timeout for a new request using three potential sources; the deadline in the async context, the
     * request timeout, and the client default. The timeout will be the lesser of the context and request timeouts or if
     * neither are present, the default timeout.
     *
     * @param metaDataTimeout the timeout specified in client metadata or null for no timeout
     * @return The timeout {@link Duration}, potentially negative or null if no timeout.
     */
    @Nullable
    private Duration timeoutForRequest(@Nullable Duration metaDataTimeout) {
        Long deadline = AsyncContext.get(GRPC_DEADLINE_KEY);
        @Nullable
        Duration contextTimeout = null != deadline ?
                Duration.ofNanos(deadline - executionContext().executor().currentTime(TimeUnit.NANOSECONDS)) : null;

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

    private static <Req> GrpcStreamingSerializer<Req> streamingSerializer(
            MethodDescriptor<Req, ?> methodDescriptor, GrpcStreamingSerializer<Req> serializerIdentity,
            @Nullable BufferEncoder compressor) {
        return compressor == null || compressor == identityEncoder() ? serializerIdentity :
                new GrpcStreamingSerializer<>(
                        methodDescriptor.requestDescriptor().serializerDescriptor().bytesEstimator(),
                        methodDescriptor.requestDescriptor().serializerDescriptor().serializer(), compressor);
    }

    private static void extractResponseContext(HttpResponseMetaData responseMetaData, GrpcClientMetadata grpcMetadata) {
        if (grpcMetadata instanceof DefaultGrpcMetadata) {
            final DefaultGrpcMetadata defaultGrpcMetadata = (DefaultGrpcMetadata) grpcMetadata;
            if (defaultGrpcMetadata.contextUnsupported()) {
                return;
            }
            if (defaultGrpcMetadata.responseContext(responseMetaData.context())) {
                return;
            }
        }
        // fallback:
        grpcMetadata.responseContext().putAll(responseMetaData.context());
    }
}
