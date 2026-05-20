/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.parseLong;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;

/**
 * Limits the response payload size. The filter will throw an exception which may result in stream/connection closure.
 * A {@link PayloadTooLargeException} will be thrown when the maximum payload size is exceeded. The
 * {@code Content-Length} response header (when present) is inspected before the body is read so oversized responses
 * that declare their size fail early; otherwise the streaming body is bounded as bytes arrive.
 */
public final class PayloadSizeLimitingHttpRequesterFilter implements
                        StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {
    private final int maxResponsePayloadSize;

    /**
     * Create a new instance.
     * @param maxResponsePayloadSize The maximum response payload size allowed.
     */
    public PayloadSizeLimitingHttpRequesterFilter(int maxResponsePayloadSize) {
        this.maxResponsePayloadSize = ensureNonNegative(maxResponsePayloadSize, "maxResponsePayloadSize");
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return applyLimit(request, delegate::request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return applyLimit(request, super::request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    private Single<StreamingHttpResponse> applyLimit(
            StreamingHttpRequest request, Function<StreamingHttpRequest, Single<StreamingHttpResponse>> delegator) {
        final HttpRequestMethod method = request.method();
        return delegator.apply(request).flatMap(response -> {
            // HEAD responses and 1xx/204/304 responses carry Content-Length as metadata but have no body (RFC 9110
            // sections 9.3.2 and 8.6). Skip the early check for these; the streaming limiter is a no-op here too
            // because no payload bytes are delivered.
            final PayloadTooLargeException ex = responseMayHaveBody(method, response) ?
                    checkContentLength(response.headers(), maxResponsePayloadSize) : null;
            if (ex != null) {
                // Drain the payload before failing so the connection isn't abandoned with undrained bytes,
                // which would typically force it closed.
                return response.messageBody().ignoreElements()
                        .concat(Single.<StreamingHttpResponse>failed(ex))
                        .shareContextOnSubscribe();
            }
            // We could use transformPayloadBody to convert into Buffers, but transformMessageBody has slightly
            // less overhead. Since this implementation is internal to ServiceTalk we take the more advanced route.
            return Single.succeeded(response.transformMessageBody(newLimiter(maxResponsePayloadSize)))
                    .shareContextOnSubscribe();
        });
    }

    private static boolean responseMayHaveBody(HttpRequestMethod method, StreamingHttpResponse response) {
        if (HEAD.equals(method)) {
            return false;
        }
        final int code = response.status().code();
        // 1xx (informational), 204 (No Content), and 304 (Not Modified) never have a body.
        return code >= 200 && code != 204 && code != 304;
    }

    /**
     * If {@code headers} declares a {@code Content-Length} exceeding {@code maxPayloadSize}, return a
     * {@link PayloadTooLargeException} so messages with known content-lengths can fail early. Returns {@code null}
     * otherwise. A malformed value falls through to the streaming byte-counting limit.
     */
    @Nullable
    static PayloadTooLargeException checkContentLength(HttpHeaders headers, int maxPayloadSize) {
        final CharSequence cl = headers.get(CONTENT_LENGTH);
        if (cl == null) {
            return null;
        }
        final long declared;
        try {
            declared = parseLong(cl);
        } catch (NumberFormatException ignored) {
            // We shouldn't get here because the decoders should reject it, but since this
            // was opportunistic anyway we can fall back to the 'byte counting' pathway.
            return null;
        }
        if (declared > maxPayloadSize) {
            return new PayloadTooLargeException("Maximum payload size=" + maxPayloadSize +
                    " declared Content-Length=" + declared);
        }
        return null;
    }

    static UnaryOperator<Publisher<?>> newLimiter(int maxPayloadSize) {
        return publisher -> Publisher.defer(() -> {
            final MutableInt responsePayloadSize = new MutableInt();
            return publisher.beforeOnNext(obj -> {
                if (obj instanceof Buffer) {
                    final Buffer buff = (Buffer) obj;
                    if (maxPayloadSize - responsePayloadSize.value < buff.readableBytes()) {
                        throw new PayloadTooLargeException("Maximum payload size=" + maxPayloadSize +
                                " current payload size=" + responsePayloadSize.value + " new buffer size=" +
                                buff.readableBytes());
                    }
                    responsePayloadSize.value += buff.readableBytes();
                }
            }).shareContextOnSubscribe();
        });
    }
}
