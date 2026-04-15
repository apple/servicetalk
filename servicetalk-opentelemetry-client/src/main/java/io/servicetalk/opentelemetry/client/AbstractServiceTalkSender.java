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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.Compressor;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

abstract class AbstractServiceTalkSender<Response> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractServiceTalkSender.class);

    private final HttpClient httpClient;
    private final AtomicBoolean isShutdown;
    private final String requestTarget;
    @Nullable
    private final Supplier<Map<String, List<String>>> headersSupplier;

    protected AbstractServiceTalkSender(HttpClient httpClient, String requestTarget,
                                        @Nullable Supplier<Map<String, List<String>>> headersSupplier) {
        this.httpClient = httpClient;
        this.requestTarget = requestTarget;
        this.headersSupplier = headersSupplier;
        this.isShutdown = new AtomicBoolean(false);
    }

    protected abstract Response buildResponse(HttpResponseStatus status, HttpHeaders headers, byte[] responseBody);

    protected final void doSend(MessageWriter messageWriter,
                     Consumer<Response> onResponse,
                     Consumer<Throwable> onError) {
        try {
            LOGGER.debug("Preparing request to: {}", requestTarget);
            // both http and grpc are post requests, it's just a matter of what exactly.
            io.servicetalk.http.api.HttpRequest request = httpClient.post(requestTarget);
            prepareMessage(messageWriter, request);

            LOGGER.debug("Executing request to: {}", requestTarget);
            // Handle response asynchronously
            httpClient.request(request).subscribe(httpResponse -> {
                byte[] responseBody = readResponseBody(httpResponse.payloadBody());
                LOGGER.debug("Received response: status={}, size={} bytes",
                        httpResponse.status(), responseBody.length);
                Response response = buildResponse(httpResponse.status(), httpResponse.headers(), responseBody);
                onResponse.accept(response);
            }, error -> {
                LOGGER.debug("Request failed with error", error);
                onError.accept(error);
            });

        } catch (Exception e) {
            LOGGER.debug("Exception during request preparation", e);
            onError.accept(e);
        }
    }

    public CompletableResultCode shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            LOGGER.debug("Sender already shutdown");
            return CompletableResultCode.ofSuccess();
        }

        LOGGER.debug("Shutting down sender");
        CompletableResultCode result = new CompletableResultCode();
        try {
            httpClient.closeAsync().subscribe(
                    () -> {
                        LOGGER.debug("Sender shutdown complete");
                        result.succeed();
                    },
                    throwable -> {
                        LOGGER.debug("Sender shutdown failed", throwable);
                        result.fail();
                    }
            );
        } catch (Exception e) {
            LOGGER.debug("Exception during sender shutdown", e);
            result.fail();
        }
        return result;
    }

    protected abstract void prepareMessage(MessageWriter messageWriter, HttpRequest request) throws IOException;

    final void applyHeaders(HttpRequest request) {
        // Apply dynamic headers from config
        if (headersSupplier != null) {
            Map<String, List<String>> headers = headersSupplier.get();
            LOGGER.debug("Applying {} dynamic headers", headers.size());
            headers.forEach((key, values) -> {
                if (values == null || values.isEmpty()) {
                    return;
                }
                if (values.size() == 1) {
                    request.setHeader(key, values.get(0));
                } else {
                    request.headers().set(key, values);
                }
            });
        }
    }

    private static byte[] readResponseBody(Buffer responseBuffer) {
        byte[] responseBody = new byte[responseBuffer.readableBytes()];
        responseBuffer.getBytes(responseBuffer.readerIndex(), responseBody);
        return responseBody;
    }

    static byte[] readMessage(MessageWriter messageWriter, @Nullable Compressor compressor) throws IOException {
        int contentLength = messageWriter.getContentLength();
        LOGGER.debug("Converting message writer to buffer: contentLength={}", contentLength);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength);
        try (OutputStream toWrite = compressor == null ? baos : compressor.compress(baos)) {
            messageWriter.writeMessage(toWrite);
        }
        byte[] bytes = baos.toByteArray();
        LOGGER.debug("Message written: originalSize={}, finalSize={}", contentLength, bytes.length);
        return bytes;
    }
}
