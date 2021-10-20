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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.encoding.api.internal.ContentCodecToBufferDecoder;
import io.servicetalk.encoding.api.internal.ContentCodecToBufferEncoder;
import io.servicetalk.encoding.api.internal.HeaderUtils;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.SerializerDeserializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.CharSequences.regionMatches;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.encodingForRaw;
import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.INTERNAL;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusCode.fromHttp2ErrorCode;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_TIMEOUT_HEADER_KEY;
import static io.servicetalk.grpc.internal.DeadlineUtils.makeTimeoutHeader;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_ACCEPT_ENCODING_KEY;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_CONTENT_TYPE;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_CONTENT_TYPE_PREFIX;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_MESSAGE_ENCODING_KEY;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_CODE_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_DETAILS_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_MESSAGE_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TE;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.TRAILERS;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.lang.String.valueOf;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

final class GrpcUtils {
    private static final GrpcStatus STATUS_OK = GrpcStatus.fromCodeValue(GrpcStatusCode.OK.value());
    private static final BufferDecoderGroup EMPTY_BUFFER_DECODER_GROUP = new BufferDecoderGroupBuilder().build();

    private static final TrailersTransformer<Object, Buffer> ENSURE_GRPC_STATUS_RECEIVED =
            new StatelessTrailersTransformer<Buffer>() {
                @Override
                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                    ensureGrpcStatusReceived(trailers);
                    return trailers;
                }

                @Override
                protected HttpHeaders payloadFailed(final Throwable cause, final HttpHeaders trailers)
                        throws Throwable {
                    // local cancel
                    if (cause instanceof CancellationException) {
                        // include the cause so that caller can determine who cancelled request
                        throw new GrpcStatusException(new GrpcStatus(CANCELLED, cause), () -> null);
                    }

                    // local timeout
                    if (cause instanceof TimeoutException) {
                        // include the cause so the caller sees the time duration.
                        throw new GrpcStatusException(new GrpcStatus(DEADLINE_EXCEEDED, cause), () -> null);
                    }

                    throw cause;
                }
            };

    private GrpcUtils() {
        // No instances.
    }

    static void initRequest(final HttpRequestMetaData request,
                            final CharSequence contentType,
                            @Nullable final CharSequence encoding,
                            @Nullable final CharSequence acceptedEncoding,
                            @Nullable final Duration timeout) {
        assert POST.equals(request.method());
        final HttpHeaders headers = request.headers();
        final CharSequence timeoutValue = makeTimeoutHeader(timeout);
        if (null != timeoutValue) {
            headers.set(GRPC_TIMEOUT_HEADER_KEY, timeoutValue);
        }
        headers.set(USER_AGENT, GRPC_USER_AGENT);
        headers.set(TE, TRAILERS);
        headers.set(CONTENT_TYPE, contentType);
        if (encoding != null) {
            headers.set(GRPC_MESSAGE_ENCODING_KEY, encoding);
        }
        if (acceptedEncoding != null) {
            headers.set(GRPC_ACCEPT_ENCODING_KEY, acceptedEncoding);
        }
    }

    static <T> StreamingHttpResponse newResponse(final StreamingHttpResponseFactory responseFactory,
                                                 final CharSequence contentType,
                                                 @Nullable final CharSequence encoding,
                                                 @Nullable final CharSequence acceptedEncoding,
                                                 final Publisher<T> payload,
                                                 final GrpcStreamingSerializer<T> serializer,
                                                 final BufferAllocator allocator) {
        return newStreamingResponse(responseFactory, contentType, encoding, acceptedEncoding)
                .payloadBody(serializer.serialize(payload, allocator))
                .transform(new GrpcStatusUpdater(allocator, STATUS_OK));
    }

    static StreamingHttpResponse newResponse(final StreamingHttpResponseFactory responseFactory,
                                             final CharSequence contentType,
                                             @Nullable final CharSequence encoding,
                                             @Nullable final CharSequence acceptedEncoding,
                                             final GrpcStatus status,
                                             final BufferAllocator allocator) {
        return newStreamingResponse(responseFactory, contentType, encoding, acceptedEncoding)
                .transform(new GrpcStatusUpdater(allocator, status));
    }

    static HttpResponse newResponse(final HttpResponseFactory responseFactory,
                                    final CharSequence contentType,
                                    @Nullable final CharSequence encoding,
                                    @Nullable final CharSequence acceptedEncoding) {
        final HttpResponse response = responseFactory.ok();
        initResponse(response, contentType, encoding, acceptedEncoding);
        setStatusOk(response.trailers());
        return response;
    }

    static HttpResponse newErrorResponse(final HttpResponseFactory responseFactory,
                                         final CharSequence contentType,
                                         final Throwable cause, final BufferAllocator allocator) {
        final HttpResponse response = responseFactory.ok();
        initResponse(response, contentType, null, null);
        setStatus(response.headers(), cause, allocator);
        return response;
    }

    static StreamingHttpResponse newErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                  final CharSequence contentType, final Throwable cause,
                                                  final BufferAllocator allocator) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, contentType, null, null);
        setStatus(response.headers(), cause, allocator);
        return response;
    }

    private static StreamingHttpResponse newStreamingResponse(final StreamingHttpResponseFactory responseFactory,
                                                              final CharSequence contentType,
                                                              @Nullable final CharSequence encoding,
                                                              @Nullable final CharSequence acceptedEncoding) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, contentType, encoding, acceptedEncoding);
        return response;
    }

    static void setStatusOk(final HttpHeaders trailers) {
        setStatus(trailers, STATUS_OK, null, null);
    }

    static void setStatus(final HttpHeaders trailers, final GrpcStatus status, @Nullable final Status details,
                          @Nullable final BufferAllocator allocator) {
        trailers.set(GRPC_STATUS_CODE_TRAILER, valueOf(status.code().value()));
        if (status.description() != null) {
            trailers.set(GRPC_STATUS_MESSAGE_TRAILER, status.description());
        }
        if (details != null) {
            assert allocator != null;
            trailers.set(GRPC_STATUS_DETAILS_TRAILER,
                    newAsciiString(allocator.wrap(Base64.getEncoder().encode(details.toByteArray()))));
        }
    }

    static void setStatus(final HttpHeaders trailers, final Throwable cause, final BufferAllocator allocator) {
        if (cause instanceof GrpcStatusException) {
            GrpcStatusException grpcStatusException = (GrpcStatusException) cause;
            setStatus(trailers, grpcStatusException.status(), grpcStatusException.applicationStatus(), allocator);
        } else {
            setStatus(trailers, toGrpcStatus(cause), null, allocator);
        }
    }

    static GrpcStatus toGrpcStatus(Throwable cause) {
        final GrpcStatus status;
        if (cause instanceof Http2Exception) {
            Http2Exception h2Exception = (Http2Exception) cause;
            status = new GrpcStatus(fromHttp2ErrorCode(h2Exception.errorCode()), cause);
        } else if (cause instanceof MessageEncodingException) {
            MessageEncodingException msgEncException = (MessageEncodingException) cause;
            status = new GrpcStatus(UNIMPLEMENTED, cause, "Message encoding '" + msgEncException.encoding()
                    + "' not supported ");
        } else if (cause instanceof SerializationException) {
            status = new GrpcStatus(UNKNOWN, cause, "Serialization error: " + cause.getMessage());
        } else if (cause instanceof CancellationException) {
            status = new GrpcStatus(CANCELLED, cause);
        } else if (cause instanceof TimeoutException) {
            status = new GrpcStatus(DEADLINE_EXCEEDED, cause);
        } else {
            // Initialize detail because cause is often lost
            status = new GrpcStatus(UNKNOWN, cause, cause.toString());
        }

        return status;
    }

    static GrpcStatusException toGrpcException(Throwable cause) {
        return cause instanceof GrpcStatusException ? (GrpcStatusException) cause
                : new GrpcStatusException(toGrpcStatus(cause), () -> null);
    }

    static <Resp> Publisher<Resp> validateResponseAndGetPayload(final StreamingHttpResponse response,
                                                                final CharSequence expectedContentType,
                                                                final BufferAllocator allocator,
                                                                final GrpcStreamingDeserializer<Resp> deserializer) {
        // In case of an empty response, gRPC-server may return only one HEADER frame with endStream=true. Our
        // HTTP1-based implementation translates them into response headers so we need to look for a grpc-status in both
        // headers and trailers. Since this is streaming response and we have the headers now, we check for the
        // grpc-status here first. If there is no grpc-status in headers, we look for it in trailers later.
        final HttpHeaders headers = response.headers();
        validateContentType(headers, expectedContentType);
        final GrpcStatusCode grpcStatusCode = extractGrpcStatusCodeFromHeaders(headers);
        if (grpcStatusCode != null) {
            final GrpcStatusException grpcStatusException = convertToGrpcStatusException(grpcStatusCode, headers);
            if (grpcStatusException != null) {
                // Give priority to the error if it happens, to allow delayed requests or streams to terminate.
                return Publisher.<Resp>failed(grpcStatusException)
                        .concat(response.messageBody().ignoreElements());
            } else {
                return response.messageBody().ignoreElements().toPublisher();
            }
        }

        response.transform(ENSURE_GRPC_STATUS_RECEIVED);
        return deserializer.deserialize(response.payloadBody(), allocator);
    }

    static <Resp> Resp validateResponseAndGetPayload(final HttpResponse response,
                                                     final CharSequence expectedContentType,
                                                     final BufferAllocator allocator,
                                                     final GrpcDeserializer<Resp> deserializer) {
        // In case of an empty response, gRPC-server may return only one HEADER frame with endStream=true. Our
        // HTTP1-based implementation translates them into response headers so we need to look for a grpc-status in both
        // headers and trailers.
        final HttpHeaders headers = response.headers();
        final HttpHeaders trailers = response.trailers();
        validateContentType(headers, expectedContentType);

        // We will try the trailers first as this is the most likely place to find the gRPC-related headers.
        final GrpcStatusCode grpcStatusCode = extractGrpcStatusCodeFromHeaders(trailers);
        if (grpcStatusCode != null) {
            final GrpcStatusException grpcStatusException = convertToGrpcStatusException(grpcStatusCode, trailers);
            if (grpcStatusException != null) {
                throw grpcStatusException;
            }
            return deserializer.deserialize(response.payloadBody(), allocator);
        }

        // There was no grpc-status in the trailers, so it must be in headers.
        ensureGrpcStatusReceived(headers);
        return deserializer.deserialize(response.payloadBody(), allocator);
    }

    static void validateContentType(HttpHeaders headers, CharSequence expectedContentType) {
        CharSequence requestContentType = headers.get(CONTENT_TYPE);
        if (!contentEqualsIgnoreCase(requestContentType, expectedContentType) &&
                (requestContentType == null ||
                    !regionMatches(requestContentType, true, 0, GRPC_CONTENT_TYPE, 0, GRPC_CONTENT_TYPE.length()))) {
            throw GrpcStatusException.of(Status.newBuilder().setCode(INTERNAL.value())
                    .setMessage("invalid content-type: " + requestContentType).build());
        }
    }

    static CharSequence grpcContentType(CharSequence contentType) {
        return newAsciiString(GRPC_CONTENT_TYPE_PREFIX + contentType);
    }

    private static void ensureGrpcStatusReceived(final HttpHeaders headers) {
        final GrpcStatusCode statusCode = extractGrpcStatusCodeFromHeaders(headers);
        if (statusCode == null) {
            // This is a protocol violation as we expect to receive grpc-status.
            throw new GrpcStatus(UNKNOWN, null, "Response does not contain " +
                    GRPC_STATUS_CODE_TRAILER + " header or trailer").asException();
        }
        final GrpcStatusException grpcStatusException = convertToGrpcStatusException(statusCode, headers);
        if (grpcStatusException != null) {
            throw grpcStatusException;
        }
    }

    static <T> T readGrpcMessageEncodingRaw(final HttpHeaders headers, final T identityEncoder,
                                            final List<T> supportedEncoders,
                                            final Function<T, CharSequence> messageEncodingFunc) {
        final CharSequence encoding = headers.get(GRPC_MESSAGE_ENCODING_KEY);
        if (encoding == null || contentEqualsIgnoreCase(Identity.identityEncoder().encodingName(), encoding)) {
            return identityEncoder;
        }
        final T result = encodingForRaw(supportedEncoders, messageEncodingFunc, encoding);
        if (result == null) {
            throw GrpcStatusException.of(Status.newBuilder().setCode(UNIMPLEMENTED.value())
                    .setMessage("Invalid " + GRPC_MESSAGE_ENCODING_KEY + ": " + encoding).build());
        }

        return result;
    }

    static <T> T negotiateAcceptedEncodingRaw(final HttpHeaders headers,
                                              final T identityEncoder,
                                              final List<T> supportedEncoders,
                                              final Function<T, CharSequence> messageEncodingFunc) {
        T result = HeaderUtils.negotiateAcceptedEncodingRaw(headers.get(GRPC_ACCEPT_ENCODING_KEY),
                supportedEncoders, messageEncodingFunc);
        return result == null ? identityEncoder : result;
    }

    static void initResponse(final HttpResponseMetaData response,
                             final CharSequence contentType,
                             @Nullable final CharSequence encoding,
                             @Nullable final CharSequence acceptedEncoding) {
        // The response status is 200 no matter what. Actual status is put in trailers.
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, contentType);
        if (encoding != null) {
            headers.set(GRPC_MESSAGE_ENCODING_KEY, encoding);
        }
        if (acceptedEncoding != null) {
            headers.set(GRPC_ACCEPT_ENCODING_KEY, acceptedEncoding);
        }
    }

    @Nullable
    private static GrpcStatusCode extractGrpcStatusCodeFromHeaders(final HttpHeaders headers) {
        final CharSequence statusCode = headers.get(GRPC_STATUS_CODE_TRAILER);
        if (statusCode == null) {
            return null;
        }
        return GrpcStatusCode.fromCodeValue(statusCode);
    }

    @Nullable
    private static GrpcStatusException convertToGrpcStatusException(final GrpcStatusCode grpcStatusCode,
                                                                    final HttpHeaders headers) {
        if (grpcStatusCode.value() == GrpcStatusCode.OK.value()) {
            return null;
        }
        final GrpcStatus grpcStatus = new GrpcStatus(grpcStatusCode, null, headers.get(GRPC_STATUS_MESSAGE_TRAILER));
        return grpcStatus.asException(new StatusSupplier(headers, grpcStatus));
    }

    @Nullable
    private static Status getStatusDetails(final HttpHeaders headers) {
        final CharSequence details = headers.get(GRPC_STATUS_DETAILS_TRAILER);
        if (details == null) {
            return null;
        }

        try {
            return Status.parser().parseFrom(Base64.getDecoder().decode(details.toString()));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Could not decode grpc status details", e);
        }
    }

    static <T> ToIntFunction<T> defaultToInt() {
        return t -> 256;
    }

    static <T> List<GrpcStreamingDeserializer<T>> streamingDeserializers(Deserializer<T> desrializer,
                                                                         List<BufferDecoder> decompressors) {
        if (decompressors.isEmpty()) {
            return emptyList();
        }
        List<GrpcStreamingDeserializer<T>> deserializers = new ArrayList<>(decompressors.size());
        for (BufferDecoder decompressor : decompressors) {
            deserializers.add(new GrpcStreamingDeserializer<>(desrializer, decompressor));
        }

        return deserializers;
    }

    static <T> List<GrpcStreamingSerializer<T>> streamingSerializers(SerializerDeserializer<T> serializer,
                                                                     ToIntFunction<T> bytesEstimator,
                                                                     List<BufferEncoder> compressors) {
        if (compressors.isEmpty()) {
            return emptyList();
        }
        List<GrpcStreamingSerializer<T>> serializers = new ArrayList<>(compressors.size());
        for (BufferEncoder compressor : compressors) {
            serializers.add(new GrpcStreamingSerializer<>(bytesEstimator, serializer, compressor));
        }
        return serializers;
    }

    static <T> List<GrpcDeserializer<T>> deserializers(Deserializer<T> deserializer,
                                                       List<BufferDecoder> decompressors) {
        if (decompressors.isEmpty()) {
            return emptyList();
        }
        List<GrpcDeserializer<T>> deserializers = new ArrayList<>(decompressors.size());
        for (BufferDecoder decompressor : decompressors) {
            deserializers.add(new GrpcDeserializer<>(deserializer, decompressor));
        }

        return deserializers;
    }

    static <T> List<GrpcSerializer<T>> serializers(Serializer<T> serializer, ToIntFunction<T> byteEstimator,
                                                   List<BufferEncoder> compressors) {
        if (compressors.isEmpty()) {
            return emptyList();
        }
        List<GrpcSerializer<T>> serializers = new ArrayList<>(compressors.size());
        for (BufferEncoder compressor : compressors) {
            serializers.add(new GrpcSerializer<>(byteEstimator, serializer, compressor));
        }
        return serializers;
    }

    @Deprecated
    static <T> SerializerDeserializer<T> serializerDeserializer(
            final GrpcSerializationProvider serializationProvider, Class<T> clazz) {
        return new HttpSerializerToSerializer<>(serializationProvider.serializerFor(Identity.identity(), clazz),
                serializationProvider.deserializerFor(Identity.identity(), clazz));
    }

    @Deprecated
    static List<BufferEncoder> compressors(List<ContentCodec> codecs) {
        if (codecs.isEmpty()) {
            return emptyList();
        }
        List<BufferEncoder> encoders = new ArrayList<>(codecs.size());
        for (ContentCodec codec : codecs) {
            encoders.add(new ContentCodecToBufferEncoder(codec));
        }
        return encoders;
    }

    @Deprecated
    static BufferDecoderGroup decompressors(List<ContentCodec> codecs) {
        if (codecs.isEmpty()) {
            return EMPTY_BUFFER_DECODER_GROUP;
        }
        BufferDecoderGroupBuilder builder = new BufferDecoderGroupBuilder(codecs.size());
        for (ContentCodec codec : codecs) {
            builder.add(new ContentCodecToBufferDecoder(codec), codec != identity());
        }
        return builder.build();
    }

    @Deprecated
    static final class HttpSerializerToSerializer<T> implements SerializerDeserializer<T> {
        private final HttpSerializer<T> httpSerializer;
        private final HttpDeserializer<T> httpDeserializer;

        HttpSerializerToSerializer(HttpSerializer<T> httpSerializer, HttpDeserializer<T> httpDeserializer) {
            this.httpSerializer = requireNonNull(httpSerializer);
            this.httpDeserializer = requireNonNull(httpDeserializer);
        }

        @Override
        public T deserialize(final Buffer serializedData, final BufferAllocator allocator) {
            // Re-apply the gRPC framing that was previously stripped. Previously the gRPC framing was understood and
            // parsed by the external HttpDeserializer.
            Buffer wrappedBuffer = allocator.newBuffer(serializedData.readableBytes() + 5);
            wrappedBuffer.writeByte(0); // Compression is applied at a higher level now with the new APIs.
            wrappedBuffer.writeInt(serializedData.readableBytes());
            wrappedBuffer.writeBytes(serializedData);
            return httpDeserializer.deserialize(DefaultHttpHeadersFactory.INSTANCE.newHeaders(), wrappedBuffer);
        }

        @Override
        public void serialize(final T toSerialize, final BufferAllocator allocator, final Buffer buffer) {
            // Skip gRPC payload framing applied externally, because it is now applied internally.
            Buffer httpResult = httpSerializer.serialize(DefaultHttpHeadersFactory.INSTANCE.newHeaders(), toSerialize,
                    allocator);
            buffer.writeBytes(httpResult, httpResult.readerIndex() + 5, httpResult.readableBytes() - 5);
        }
    }

    static final class DefaultParameterDescriptor<T> implements ParameterDescriptor<T> {
        private final boolean isStreaming;
        private final boolean isAsync;
        private final Class<T> parameterClass;
        private final SerializerDescriptor<T> serializerDescriptor;

        DefaultParameterDescriptor(final boolean isStreaming, final boolean isAsync, final Class<T> parameterClass,
                                   final SerializerDescriptor<T> serializerDescriptor) {
            this.isStreaming = isStreaming;
            this.isAsync = isAsync;
            this.parameterClass = parameterClass;
            this.serializerDescriptor = serializerDescriptor;
        }

        @Override
        public boolean isStreaming() {
            return isStreaming;
        }

        @Override
        public boolean isAsync() {
            return isAsync;
        }

        @Override
        public Class<T> parameterClass() {
            return parameterClass;
        }

        @Override
        public SerializerDescriptor<T> serializerDescriptor() {
            return serializerDescriptor;
        }
    }

    static final class DefaultSerializerDescriptor<T> implements SerializerDescriptor<T> {
        private final CharSequence contentType;
        private final SerializerDeserializer<T> serializer;
        private final ToIntFunction<T> bytesEstimator;

        DefaultSerializerDescriptor(final CharSequence contentType, final SerializerDeserializer<T> serializer,
                                    final ToIntFunction<T> bytesEstimator) {
            this.contentType = requireNonNull(contentType);
            this.serializer = requireNonNull(serializer);
            this.bytesEstimator = requireNonNull(bytesEstimator);
        }

        @Override
        public CharSequence contentType() {
            return contentType;
        }

        @Override
        public SerializerDeserializer<T> serializer() {
            return serializer;
        }

        @Override
        public ToIntFunction<T> bytesEstimator() {
            return bytesEstimator;
        }
    }

    static final class DefaultMethodDescriptor<Req, Resp> implements MethodDescriptor<Req, Resp> {
        private final String httpPath;
        private final String javaMethodName;
        private final ParameterDescriptor<Req> requestDescriptor;
        private final ParameterDescriptor<Resp> responseDescriptor;

        @Deprecated
        DefaultMethodDescriptor(final String httpPath, final boolean reqIsStreaming,
                                final boolean reqIsAsync, final Class<Req> reqClass, final CharSequence reqContentType,
                                final SerializerDeserializer<Req> reqSerializer,
                                final ToIntFunction<Req> reqBytesEstimator, final boolean respIsStreaming,
                                final boolean respIsAsync, final Class<Resp> respClass,
                                final CharSequence respContentType,
                                final SerializerDeserializer<Resp> respSerializer,
                                final ToIntFunction<Resp> respBytesEstimator) {
            this(httpPath, extractJavaMethodName(httpPath), reqIsStreaming, reqIsAsync, reqClass, reqContentType,
                    reqSerializer, reqBytesEstimator, respIsStreaming, respIsAsync, respClass, respContentType,
                    respSerializer, respBytesEstimator);
        }

        DefaultMethodDescriptor(final String httpPath, final String javaMethodName, final boolean reqIsStreaming,
                                final boolean reqIsAsync, final Class<Req> reqClass, final CharSequence reqContentType,
                                final SerializerDeserializer<Req> reqSerializer,
                                final ToIntFunction<Req> reqBytesEstimator, final boolean respIsStreaming,
                                final boolean respIsAsync, final Class<Resp> respClass,
                                final CharSequence respContentType,
                                final SerializerDeserializer<Resp> respSerializer,
                                final ToIntFunction<Resp> respBytesEstimator) {
            this(httpPath, javaMethodName,
                    new DefaultParameterDescriptor<>(reqIsStreaming, reqIsAsync, reqClass,
                            new DefaultSerializerDescriptor<>(reqContentType, reqSerializer, reqBytesEstimator)),
                    new DefaultParameterDescriptor<>(respIsStreaming, respIsAsync, respClass,
                            new DefaultSerializerDescriptor<>(respContentType, respSerializer, respBytesEstimator)));
        }

        private DefaultMethodDescriptor(final String httpPath, final String javaMethodName,
                                        final ParameterDescriptor<Req> requestDescriptor,
                                        final ParameterDescriptor<Resp> responseDescriptor) {
            this.httpPath = requireNonNull(httpPath);
            this.javaMethodName = requireNonNull(javaMethodName);
            this.requestDescriptor = requireNonNull(requestDescriptor);
            this.responseDescriptor = requireNonNull(responseDescriptor);
        }

        private static String extractJavaMethodName(String httpPath) {
            int i = httpPath.lastIndexOf('/');
            if (i < 0) {
                return "";
            }
            String result = httpPath.substring(i + 1);
            final char firstChar;
            if (result.isEmpty() || Character.isLowerCase((firstChar = result.charAt(0)))) {
                return result;
            }
            return Character.toLowerCase(firstChar) + result.substring(1);
        }

        @Override
        public String httpPath() {
            return httpPath;
        }

        @Override
        public String javaMethodName() {
            return javaMethodName;
        }

        @Override
        public ParameterDescriptor<Req> requestDescriptor() {
            return requestDescriptor;
        }

        @Override
        public ParameterDescriptor<Resp> responseDescriptor() {
            return responseDescriptor;
        }
    }

    /**
     * Supplies the {@link Status} by parsing (and caching) it in a lazy fashion once requested the
     * first time.
     */
    private static final class StatusSupplier implements Supplier<Status> {

        private final HttpHeaders headers;
        private final GrpcStatus fallbackStatus;
        @Nullable
        private volatile StatusHolder statusHolder;

        StatusSupplier(HttpHeaders headers, final GrpcStatus fallbackStatus) {
            this.headers = headers;
            this.fallbackStatus = fallbackStatus;
        }

        @Nullable
        @Override
        public Status get() {
            StatusHolder statusHolder = this.statusHolder;
            if (statusHolder == null) {
                // Cache the status (we don't bother caching any errors tho). Also its fine to only use a volatile here
                // as at worse this will just update to the "same" status again.
                final Status statusFromHeaders = getStatusDetails(headers);
                if (statusFromHeaders == null) {
                    final Status.Builder builder = Status.newBuilder().setCode(fallbackStatus.code().value());
                    if (fallbackStatus.description() != null) {
                        builder.setMessage(fallbackStatus.description());
                    }
                    this.statusHolder = statusHolder = new StatusHolder(builder.build());
                } else {
                    this.statusHolder = statusHolder = new StatusHolder(statusFromHeaders);
                }
            }
            return statusHolder.status;
        }

        /**
         * This class differentiates between the {@code null} application status if none is present to avoid
         * continuously retrying to parse status when it isn't present.
         */
        private static final class StatusHolder {
            @Nullable
            final Status status;

            StatusHolder(@Nullable Status status) {
                this.status = status;
            }
        }
    }

    static final class GrpcStatusUpdater extends StatelessTrailersTransformer<Buffer> {
        private final BufferAllocator allocator;
        private final GrpcStatus successStatus;

        GrpcStatusUpdater(final BufferAllocator allocator, final GrpcStatus successStatus) {
            this.allocator = allocator;
            this.successStatus = successStatus;
        }

        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            setStatus(trailers, successStatus, null, allocator);
            return trailers;
        }

        @Override
        protected HttpHeaders payloadFailed(final Throwable cause, final HttpHeaders trailers) {
            setStatus(trailers, cause, allocator);
            // Swallow exception as we are converting it to the trailers.
            return trailers;
        }
    }
}
