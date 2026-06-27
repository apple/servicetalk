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
package io.servicetalk.opentelemetry.client;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.grpc.api.GrpcHeaderValues;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;

import io.opentelemetry.sdk.common.export.Compressor;
import io.opentelemetry.sdk.common.export.GrpcResponse;
import io.opentelemetry.sdk.common.export.GrpcSender;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_MESSAGE_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;

final class ServiceTalkGrpcSender extends AbstractServiceTalkSender<GrpcResponse> implements GrpcSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkGrpcSender.class);

    private static final byte UNCOMPRESSED_FLAG = 0;
    private static final byte COMPRESSED_FLAG = 1;
    private static final int HEADER_LENGTH = 5;

    @Nullable
    private final Compressor compressor;
    private final BufferAllocator bufferAllocator;

    ServiceTalkGrpcSender(HttpClient httpClient,
                          @Nullable Compressor compressor,
                          String fullMethodName,
                          @Nullable Supplier<Map<String, List<String>>> headersSupplier) {
        super(httpClient, fullMethodName, headersSupplier);
        this.compressor = compressor;
        this.bufferAllocator = httpClient.executionContext().bufferAllocator();
        LOGGER.debug("Created ServiceTalkGrpcSender: fullMethodName={}, compression={}",
                fullMethodName, compressor != null);
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<GrpcResponse> onResponse, Consumer<Throwable> onError) {
        LOGGER.debug("Sending gRPC request via ServiceTalk");
        doSend(messageWriter, onResponse, onError);
    }

    @Override
    protected void prepareMessage(MessageWriter messageWriter, HttpRequest request) throws IOException {
        applyHeaders(request);
        int messageSize = messageWriter.getContentLength();
        // We need to write the gRPC message header, which depends on whether it's compressed or not.
        Buffer buffer;
        if (compressor == null) {
            buffer = bufferAllocator.newBuffer(messageSize + HEADER_LENGTH);
            buffer.writeByte(UNCOMPRESSED_FLAG);
            buffer.writeInt(messageSize);
            buffer.writeBytes(readMessage(messageWriter, null));
        } else {
            request.setHeader(GRPC_MESSAGE_ENCODING, compressor.getEncoding());
            byte[] compressedBody = readMessage(messageWriter, compressor);
            buffer = bufferAllocator.newBuffer(compressedBody.length + HEADER_LENGTH);
            buffer.writeByte(COMPRESSED_FLAG);
            buffer.writeInt(compressedBody.length);
            buffer.writeBytes(compressedBody);
        }

        request.payloadBody(buffer);
        request.setHeader("te", "trailers");
        request.setHeader(CONTENT_TYPE, GrpcHeaderValues.APPLICATION_GRPC);
        LOGGER.debug("Prepared gRPC request: method={}, path={}, contentType={}",
                request.method(), request.requestTarget(), GrpcHeaderValues.APPLICATION_GRPC);
    }

    @Override
    protected GrpcResponse buildResponse(HttpResponseStatus status, HttpHeaders headers, byte[] responseBody) {
        CharSequence grpcStatusSeq = headers.get("grpc-status");
        String grpcStatusStr = grpcStatusSeq != null ? grpcStatusSeq.toString() : "0"; // 0 = OK
        CharSequence grpcMessageSeq = headers.get("grpc-message");
        String grpcMessage = grpcMessageSeq != null ? grpcMessageSeq.toString() : "";

        int grpcStatusValue = Integer.parseInt(grpcStatusStr);
        io.opentelemetry.sdk.common.export.GrpcStatusCode statusCode =
                io.opentelemetry.sdk.common.export.GrpcStatusCode.fromValue(grpcStatusValue);
        LOGGER.debug("Received gRPC response: grpcStatus={}, grpcMessage={}, responseSize={} bytes",
                statusCode, grpcMessage, responseBody.length);
        return new GrpcResponseImpl(statusCode, grpcMessage, responseBody);
    }

    private static final class GrpcResponseImpl implements GrpcResponse {
        private final io.opentelemetry.sdk.common.export.GrpcStatusCode statusCode;
        private final String statusDescription;
        private final byte[] responseMessage;

        GrpcResponseImpl(io.opentelemetry.sdk.common.export.GrpcStatusCode statusCode,
                        String statusDescription, byte[] responseMessage) {
            this.statusCode = statusCode;
            this.statusDescription = statusDescription;
            this.responseMessage = responseMessage;
        }

        @Override
        public io.opentelemetry.sdk.common.export.GrpcStatusCode getStatusCode() {
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
