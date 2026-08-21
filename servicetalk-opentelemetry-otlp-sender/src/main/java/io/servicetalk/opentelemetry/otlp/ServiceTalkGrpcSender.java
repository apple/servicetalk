/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.encoding.netty.NettyBufferEncoders;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientCallConfig;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcClientCallFactory.ClientCall;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.api.MethodDescriptor;
import io.servicetalk.grpc.api.MethodDescriptors;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.serializer.api.SerializerDeserializer;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.Compressor;
import io.opentelemetry.sdk.common.export.GrpcResponse;
import io.opentelemetry.sdk.common.export.GrpcSender;
import io.opentelemetry.sdk.common.export.GrpcStatusCode;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.serializer.utils.ByteArraySerializer.byteArraySerializer;

final class ServiceTalkGrpcSender implements GrpcSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkGrpcSender.class);

    private static final SerializerDeserializer<byte[]> BYTES = byteArraySerializer(false);
    private static final BufferDecoderGroup DECOMPRESSORS =
            new BufferDecoderGroupBuilder().add(NettyBufferEncoders.gzipDefault(), true).build();
    private static final String PROTO_CONTENT_TYPE_SUFFIX = "+proto";

    private final StreamingHttpClient httpClient;
    private final ClientCall<byte[], byte[]> call;
    @Nullable
    private final OtelCompressorBufferEncoder requestCompressor;
    private final AtomicBoolean isShutdown = new AtomicBoolean();

    ServiceTalkGrpcSender(StreamingHttpClient httpClient,
                          @Nullable Compressor compressor,
                          String fullMethodName) {
        this.httpClient = httpClient;
        MethodDescriptor<byte[], byte[]> md = MethodDescriptors.newMethodDescriptor(
                fullMethodName, "send", false, false,
                byte[].class, PROTO_CONTENT_TYPE_SUFFIX, BYTES, b -> b.length,
                false, true,
                byte[].class, PROTO_CONTENT_TYPE_SUFFIX, BYTES, b -> b.length);
        this.call = GrpcClientCallFactory.from(httpClient, new GrpcClientCallConfig.Builder().build())
                .newCall(md, DECOMPRESSORS);
        this.requestCompressor = compressor == null ? null : new OtelCompressorBufferEncoder(compressor);
    }

    private GrpcClientMetadata newMetadata() {
        // Fresh per send(): DefaultGrpcClientMetadata's request/response context is a lazy,
        // non-thread-safe field that must not be reused across calls.
        return requestCompressor == null ? new DefaultGrpcClientMetadata()
                : new DefaultGrpcClientMetadata(requestCompressor);
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<GrpcResponse> onResponse, Consumer<Throwable> onError) {
        if (isShutdown.get()) {
            onError.accept(new IllegalStateException("Sender is shut down"));
            return;
        }
        byte[] body;
        try {
            body = ServiceTalkHttpSender.readMessage(messageWriter, null);
        } catch (IOException e) {
            onError.accept(e);
            return;
        }
        call.request(newMetadata(), body).subscribe(
                respBytes -> onResponse.accept(new GrpcResponseImpl(GrpcStatusCode.OK, "", respBytes)),
                err -> {
                    if (err instanceof GrpcStatusException) {
                        GrpcStatus st = ((GrpcStatusException) err).status();
                        String desc = st.description();
                        onResponse.accept(new GrpcResponseImpl(
                                GrpcStatusCode.fromValue(st.code().value()),
                                desc != null ? desc : "",
                                new byte[0]));
                    } else {
                        onError.accept(err);
                    }
                });
    }

    @Override
    public CompletableResultCode shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            return CompletableResultCode.ofSuccess();
        }
        CompletableResultCode result = new CompletableResultCode();
        try {
            // Use closeAsync (not graceful) so JVM-shutdown paths don't hang on an unreachable collector.
            httpClient.closeAsync().subscribe(result::succeed,
                    t -> {
                        LOGGER.debug("gRPC sender shutdown failed", t);
                        result.fail();
                    });
        } catch (Exception e) {
            LOGGER.debug("gRPC sender shutdown threw", e);
            result.fail();
        }
        return result;
    }

    private static final class GrpcResponseImpl implements GrpcResponse {
        private final GrpcStatusCode statusCode;
        private final String statusDescription;
        private final byte[] responseMessage;

        GrpcResponseImpl(GrpcStatusCode statusCode, String statusDescription, byte[] responseMessage) {
            this.statusCode = statusCode;
            this.statusDescription = statusDescription;
            this.responseMessage = responseMessage;
        }

        @Override
        public GrpcStatusCode getStatusCode() {
            return statusCode;
        }

        @Override
        public String getStatusDescription() {
            return statusDescription;
        }

        @Override
        public byte[] getResponseMessage() {
            return responseMessage;
        }
    }
}
