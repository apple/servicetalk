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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;

import io.opentelemetry.sdk.common.export.Compressor;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.HttpSender;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;

final class ServiceTalkHttpSender extends AbstractServiceTalkSender implements HttpSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkHttpSender.class);

    private final String contentType;
    @Nullable
    private final Compressor compressor;
    private final BufferAllocator bufferAllocator;

    ServiceTalkHttpSender(HttpClient httpClient,
                          @Nullable Compressor compressor,
                          String contentType,
                          String requestTarget,
                          @Nullable Supplier<Map<String, List<String>>> headersSupplier) {
        super(httpClient, requestTarget, headersSupplier);
        this.contentType = contentType;
        this.compressor = compressor;
        this.bufferAllocator = httpClient.executionContext().bufferAllocator();
        LOGGER.debug("Created ServiceTalkHttpSender: requestTarget={}, contentType={}, compression={}",
                requestTarget, contentType, compressor != null);
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<HttpResponse> onResponse, Consumer<Throwable> onError) {
        LOGGER.debug("Sending HTTP request via ServiceTalk");
        doSend(messageWriter, onResponse, onError);
    }

    @Override
    protected Object buildResponse(HttpResponseStatus status, HttpHeaders headers, byte[] responseBody) {
        return new HttpResponseImpl(status.code(), status.reasonPhrase(), responseBody);
    }

    @Override
    protected void prepareMessage(MessageWriter messageWriter, HttpRequest request) throws IOException {
        Buffer payload = bufferAllocator.wrap(readMessage(messageWriter, compressor));
        request.payloadBody(payload);
        LOGGER.debug("Prepared message payload: size={} bytes", payload.readableBytes());

        String compressorEncoding = compressor == null ? null : compressor.getEncoding();
        if (compressorEncoding != null) {
            request.setHeader(CONTENT_ENCODING, compressorEncoding);
            LOGGER.debug("Applied compression: encoding={}", compressorEncoding);
        }
        applyHeaders(request);
        request.setHeader(CONTENT_TYPE, contentType);
        LOGGER.debug("Prepared HTTP request: method={}, target={}, contentType={}",
                request.method(), request.requestTarget(), contentType);
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
