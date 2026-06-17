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
import io.servicetalk.http.api.HttpDataSourceTransformations.PayloadAndTrailers;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregatePayloadSizeLimitTest {

    private static final HttpHeadersFactory HEADERS_FACTORY = DefaultHttpHeadersFactory.INSTANCE;

    private static StreamingHttpPayloadHolder holder(final int maxAggregatedSize, final Buffer... payload) {
        final HttpHeaders headers = HEADERS_FACTORY.newHeaders();
        final DefaultPayloadInfo payloadInfo = forTransportReceive(false, HTTP_1_1, headers);
        return new StreamingHttpPayloadHolder(headers, DEFAULT_ALLOCATOR, Publisher.from((Object[]) payload),
                payloadInfo, HEADERS_FACTORY, maxAggregatedSize);
    }

    private static Buffer ascii(final int size) {
        final StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append('x');
        }
        return DEFAULT_ALLOCATOR.fromAscii(sb);
    }

    @Test
    void underLimitAggregates() throws Exception {
        PayloadAndTrailers result = holder(16, ascii(5), ascii(5)).aggregate().toFuture().get();
        assertThat(result.payload.readableBytes(), is(10));
    }

    @Test
    void atLimitAggregates() throws Exception {
        PayloadAndTrailers result = holder(10, ascii(5), ascii(5)).aggregate().toFuture().get();
        assertThat(result.payload.readableBytes(), is(10));
    }

    @Test
    void overLimitAcrossMultipleBuffersFails() {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> holder(9, ascii(5), ascii(5)).aggregate().toFuture().get());
        assertThat(e.getCause(), is(instanceOf(PayloadTooLargeException.class)));
    }

    @Test
    void overLimitSingleBufferFails() {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> holder(4, ascii(5)).aggregate().toFuture().get());
        assertThat(e.getCause(), is(instanceOf(PayloadTooLargeException.class)));
    }

    @Test
    void zeroLimitIsUnlimited() throws Exception {
        assertThat(holder(0, ascii(64)).aggregate().toFuture().get().payload.readableBytes(), is(64));
    }
}
