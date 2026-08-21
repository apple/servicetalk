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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.netty.NettyBufferEncoders;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.Compressor;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.HttpSender;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;

final class ServiceTalkHttpSender implements HttpSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkHttpSender.class);

    private static final BufferDecoder GZIP_DECODER = NettyBufferEncoders.gzipDefault();
    private static final String GZIP = "gzip";

    private final HttpClient httpClient;
    private final BufferAllocator bufferAllocator;
    private final String requestTarget;
    private final String contentType;
    @Nullable
    private final Compressor compressor;
    private final AtomicBoolean isShutdown = new AtomicBoolean();

    ServiceTalkHttpSender(HttpClient httpClient,
                          @Nullable Compressor compressor,
                          String contentType,
                          String requestTarget) {
        this.httpClient = httpClient;
        this.bufferAllocator = httpClient.executionContext().bufferAllocator();
        this.requestTarget = requestTarget;
        this.contentType = contentType;
        this.compressor = compressor;
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<HttpResponse> onResponse, Consumer<Throwable> onError) {
        if (isShutdown.get()) {
            onError.accept(new IllegalStateException("Sender is shut down"));
            return;
        }
        try {
            HttpRequest request = httpClient.post(requestTarget);
            Buffer payload = bufferAllocator.wrap(readMessage(messageWriter, compressor));
            request.payloadBody(payload);
            if (compressor != null) {
                request.setHeader(CONTENT_ENCODING, compressor.getEncoding());
            }
            request.setHeader(CONTENT_TYPE, contentType);
            // Advertise gzip so collectors may compress responses; we'll decompress on receipt.
            request.setHeader(ACCEPT_ENCODING, GZIP);

            httpClient.request(request).subscribe(httpResponse -> {
                HttpResponse mapped;
                try {
                    mapped = buildResponse(httpResponse);
                } catch (Throwable t) {
                    LOGGER.debug("Failed to build response", t);
                    onError.accept(t);
                    return;
                }
                onResponse.accept(mapped);
            }, onError::accept);
        } catch (Exception e) {
            LOGGER.debug("Exception during request preparation", e);
            onError.accept(e);
        }
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
                    throwable -> {
                        LOGGER.debug("HTTP sender shutdown failed", throwable);
                        result.fail();
                    });
        } catch (Exception e) {
            LOGGER.debug("HTTP sender shutdown threw", e);
            result.fail();
        }
        return result;
    }

    private HttpResponse buildResponse(io.servicetalk.http.api.HttpResponse httpResponse) throws IOException {
        HttpResponseStatus status = httpResponse.status();
        Buffer body = httpResponse.payloadBody();
        HttpHeaders headers = httpResponse.headers();
        CharSequence encoding = headers.get(CONTENT_ENCODING);
        if (encoding != null && GZIP.contentEquals(encoding)) {
            try {
                body = GZIP_DECODER.decoder().deserialize(body, bufferAllocator);
            } catch (RuntimeException e) {
                throw new IOException("Failed to decompress gzip response body", e);
            }
        }
        byte[] responseBody = new byte[body.readableBytes()];
        body.getBytes(body.readerIndex(), responseBody);
        return new HttpResponseImpl(status.code(), status.reasonPhrase(), responseBody);
    }

    static byte[] readMessage(MessageWriter messageWriter, @Nullable Compressor compressor) throws IOException {
        // OTel JSON marshalers report contentLength == -1; binary marshalers report a real size.
        int contentLength = messageWriter.getContentLength();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32, contentLength));
        try (OutputStream toWrite = compressor == null ? baos : compressor.compress(baos)) {
            messageWriter.writeMessage(toWrite);
        }
        return baos.toByteArray();
    }

    private static final class HttpResponseImpl implements HttpResponse {
        private final int statusCode;
        private final String statusMessage;
        private final byte[] responseBody;

        HttpResponseImpl(int statusCode, String statusMessage, byte[] responseBody) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.responseBody = responseBody;
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getStatusMessage() {
            return statusMessage;
        }

        @Override
        public byte[] getResponseBody() {
            return responseBody;
        }
    }
}
