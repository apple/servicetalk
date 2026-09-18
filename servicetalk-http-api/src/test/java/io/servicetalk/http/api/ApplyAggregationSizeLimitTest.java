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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.function.LongConsumer;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.applyAggregationSizeLimit;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplyAggregationSizeLimitTest {

    private static final int MAX = 16;
    private static final HttpHeadersFactory HEADERS_FACTORY = DefaultHttpHeadersFactory.INSTANCE;

    @Test
    void withinLimitPassesThrough() throws Exception {
        final StreamingHttpRequest request = transportRequestWithLimiter(throwingLimiter(MAX));
        assertThat(consume(applyAggregationSizeLimit(request, from(buf(MAX)))), is(MAX));
    }

    @Test
    void oversizedSingleBufferRejected() {
        final StreamingHttpRequest request = transportRequestWithLimiter(throwingLimiter(MAX));
        final ExecutionException e = assertThrows(ExecutionException.class,
                () -> consume(applyAggregationSizeLimit(request, from(buf(MAX + 1)))));
        assertThat(e.getCause(), is(instanceOf(PayloadTooLargeException.class)));
    }

    @Test
    void accumulatesAcrossBuffers() {
        // Each buffer is within the limit, but their running total exceeds it on the second.
        final StreamingHttpRequest request = transportRequestWithLimiter(throwingLimiter(MAX));
        final ExecutionException e = assertThrows(ExecutionException.class,
                () -> consume(applyAggregationSizeLimit(request, from(buf(MAX - 1), buf(2)))));
        assertThat(e.getCause(), is(instanceOf(PayloadTooLargeException.class)));
    }

    @Test
    void noLimitReturnsSamePublisher() {
        // A user-created request carries no transport limiter, so the payload body is returned unchanged.
        final StreamingHttpRequest request =
                newRequest(GET, "/", HTTP_1_1, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
        final Publisher<Buffer> body = from(buf(MAX + 1));
        assertThat(applyAggregationSizeLimit(request, body), is(sameInstance(body)));
    }

    @Test
    void nonThrowingLimiterPassesOversized() throws Exception {
        // A warn-only limiter observes the size but never throws, so an oversized body flows through.
        final StreamingHttpRequest request = transportRequestWithLimiter(size -> { });
        assertThat(consume(applyAggregationSizeLimit(request, from(buf(MAX + 1)))), is(MAX + 1));
    }

    @Test
    void inputStreamWithinLimitReads() throws Exception {
        final StreamingHttpRequest request = transportRequestWithLimiter(throwingLimiter(MAX));
        drain(applyAggregationSizeLimit(request, new ByteArrayInputStream(new byte[MAX])));
    }

    @Test
    void inputStreamOverLimitThrows() {
        final StreamingHttpRequest request = transportRequestWithLimiter(throwingLimiter(MAX));
        final InputStream limited = applyAggregationSizeLimit(request, new ByteArrayInputStream(new byte[MAX + 1]));
        assertThrows(PayloadTooLargeException.class, () -> drain(limited));
    }

    @Test
    void inputStreamNoLimitReturnsSame() {
        final StreamingHttpRequest request =
                newRequest(GET, "/", HTTP_1_1, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
        final InputStream in = new ByteArrayInputStream(new byte[MAX + 1]);
        assertThat(applyAggregationSizeLimit(request, in), is(sameInstance(in)));
    }

    private static StreamingHttpRequest transportRequestWithLimiter(final LongConsumer limiter) {
        final StreamingHttpRequest meta =
                newRequest(GET, "/", HTTP_1_1, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
        return newTransportRequest(meta, DEFAULT_ALLOCATOR, Publisher.empty(), false, HEADERS_FACTORY, limiter);
    }

    private static LongConsumer throwingLimiter(final int max) {
        return size -> {
            if (size > max) {
                throw new PayloadTooLargeException("aggregated size=" + size + " max=" + max);
            }
        };
    }

    private static Buffer buf(final int size) {
        return DEFAULT_ALLOCATOR.wrap(new byte[size]);
    }

    private static int consume(final Publisher<Buffer> payloadBody) throws Exception {
        return payloadBody.collect(() -> 0, (total, buffer) -> total + buffer.readableBytes()).toFuture().get();
    }

    private static void drain(final InputStream in) throws IOException {
        final byte[] chunk = new byte[8];
        while (in.read(chunk) >= 0) {
            // read to EOF
        }
    }
}
