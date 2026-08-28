/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.DefaultPayloadInfo.forUserCreated;

/**
 * Factory methods for creating {@link StreamingHttpRequest}s.
 */
public final class StreamingHttpRequests {
    private StreamingHttpRequests() {
        // No instances
    }

    /**
     * Creates a new {@link StreamingHttpRequest}.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request. Note that newly created and returned
     * {@link StreamingHttpRequest} will use this {@link HttpHeaders} directly, which means later mutation of
     * {@link HttpHeaders} will have side effects on returned request and should be avoided as these operations are not
     * thread safe.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static StreamingHttpRequest newRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final BufferAllocator allocator, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpRequest(method, requestTarget, version, headers, null, null, null, allocator,
                null, forUserCreated(), headersFactory, StreamingHttpPayloadHolder.NO_AGGREGATED_PAYLOAD_LIMIT);
    }

    /**
     * Creates a new {@link StreamingHttpRequest} which is read from the transport. If the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed {@code payload}
     * {@link Publisher} should also emit {@link HttpHeaders} before completion.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request. Note that newly created and returned
     * {@link StreamingHttpRequest} will use this {@link HttpHeaders} directly, which means later mutation of
     * {@link HttpHeaders} will have side effects on returned request and should be avoided as these operations are not
     * thread safe.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param payload a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpRequest}.
     * @deprecated Use {@link #newTransportRequest(HttpRequestMetaData, BufferAllocator, Publisher, boolean,
     * HttpHeadersFactory, LongConsumer)} instead.
     */
    @Deprecated
    public static StreamingHttpRequest newTransportRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final BufferAllocator allocator, final Publisher<Object> payload,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpRequest(method, requestTarget, version, headers, null, null, null, allocator,
                payload, forTransportReceive(requireTrailerHeader, version, headers), headersFactory,
                StreamingHttpPayloadHolder.NO_AGGREGATED_PAYLOAD_LIMIT);
    }

    /**
     * Creates a new {@link StreamingHttpRequest} which is read from the transport.
     * <p>
     * If the request contains <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed
     * {@code payload} {@link Publisher} must also emit {@link HttpHeaders} before completion.
     *
     * @param metaData the {@link HttpRequestMetaData} of the request parsed by the transport layer. Note that newly
     * created and returned {@link StreamingHttpRequest} will use this {@link HttpRequestMetaData} directly and share
     * its parts, which means later mutation of {@link HttpRequestMetaData} will have side effects on returned request
     * and should be avoided as these operations are not thread safe.
     * @param allocator the allocator to use for serialization purposes if necessary.
     * @param payload a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use to allocate trailers if necessary.
     * @return a new {@link StreamingHttpRequest}.
     * @deprecated Use {@link #newTransportRequest(HttpRequestMetaData, BufferAllocator, Publisher, boolean,
     * HttpHeadersFactory, LongConsumer)} which allows bounding the size of the payload buffered during
     * aggregation. This overload leaves the aggregated payload unbounded.
     */
    @Deprecated
    public static StreamingHttpRequest newTransportRequest(
            final HttpRequestMetaData metaData, final BufferAllocator allocator, final Publisher<Object> payload,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory) {
        return newTransportRequest(metaData, allocator, payload, requireTrailerHeader, headersFactory,
                StreamingHttpPayloadHolder.NO_AGGREGATED_PAYLOAD_LIMIT);
    }

    /**
     * Creates a new {@link StreamingHttpRequest} which is read from the transport, applying a limit to the size of the
     * payload that may be buffered when the request is later aggregated.
     * <p>
     * If the request contains <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed
     * {@code payload} {@link Publisher} must also emit {@link HttpHeaders} before completion.
     *
     * @param metaData the {@link HttpRequestMetaData} of the request parsed by the transport layer. Note that newly
     * created and returned {@link StreamingHttpRequest} will use this {@link HttpRequestMetaData} directly and share
     * its parts, which means later mutation of {@link HttpRequestMetaData} will have side effects on returned request
     * and should be avoided as these operations are not thread safe.
     * @param allocator the allocator to use for serialization purposes if necessary.
     * @param payload a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use to allocate trailers if necessary.
     * @param payloadSizeLimiter invoked with the running aggregated payload size (in bytes) as the request is
     * aggregated (e.g. via {@link StreamingHttpRequest#toRequest()}); it may reject an oversized message by throwing a
     * {@link PayloadTooLargeException}. Use {@code size -> { }} to leave the payload unbounded.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static StreamingHttpRequest newTransportRequest(
            final HttpRequestMetaData metaData, final BufferAllocator allocator, final Publisher<Object> payload,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory,
            final LongConsumer payloadSizeLimiter) {
        @Nullable
        ContextMap context = metaData instanceof DefaultHttpRequestMetaData ?
                ((DefaultHttpRequestMetaData) metaData).context0() : metaData.context();
        return new DefaultStreamingHttpRequest(metaData.method(), metaData.requestTarget(), metaData.version(),
                metaData.headers(), context, metaData.encoding(), metaData.contentEncoding(), allocator, payload,
                forTransportReceive(requireTrailerHeader, metaData.version(), metaData.headers()), headersFactory,
                payloadSizeLimiter);
    }

    /**
     * Enforces the aggregation payload-size limit configured on {@code request} at
     * {@link #newTransportRequest(HttpRequestMetaData, BufferAllocator, Publisher, boolean, HttpHeadersFactory,
     * LongConsumer) transport receive} time on the given {@code payloadBody}. Use it when aggregating a payload outside
     * {@link StreamingHttpRequest#toRequest()} (e.g. the JAX-RS router); streaming consumers should skip it. When the
     * request defines no limit, {@code payloadBody} is returned unchanged. The returned {@link Publisher} counts across
     * a single subscription and must be subscribed to at most once.
     *
     * @param request the request whose configured limit to enforce.
     * @param payloadBody the aggregated payload body to bound.
     * @return {@code payloadBody}, bounded by the request's aggregation size limit when one applies.
     */
    public static Publisher<Buffer> applyAggregationSizeLimit(final StreamingHttpRequest request,
                                                              final Publisher<Buffer> payloadBody) {
        final LongConsumer limiter = aggregationSizeLimiter(request);
        if (limiter == null) {
            return payloadBody;
        }
        // Unlike PayloadSizeLimitingHttpRequesterFilter#newLimiter, no defer(): this body is subscribed exactly once,
        // so the counter needs no per-subscribe reset.
        return payloadBody.beforeOnNext(new AggregatedPayloadSizeCounter(limiter)).shareContextOnSubscribe();
    }

    private static final class AggregatedPayloadSizeCounter implements Consumer<Buffer> {
        private final LongConsumer payloadSizeLimiter;
        // Mutated only from onNext; RS signals are sequential with happens-before, so a plain long is safe.
        private long aggregatedSize;

        AggregatedPayloadSizeCounter(final LongConsumer payloadSizeLimiter) {
            this.payloadSizeLimiter = payloadSizeLimiter;
        }

        @Override
        public void accept(final Buffer buffer) {
            payloadSizeLimiter.accept(aggregatedSize += buffer.readableBytes());
        }
    }

    /**
     * {@link InputStream} equivalent of {@link #applyAggregationSizeLimit(StreamingHttpRequest, Publisher)} for a
     * consumer that reads the payload as a blocking {@link InputStream} (e.g. a JAX-RS {@code String}/{@code byte[]}
     * reader). Reading past the limit throws {@link PayloadTooLargeException}. When the request defines no limit,
     * {@code in} is returned unchanged. Only bytes read count toward the limit; bytes skipped via
     * {@link InputStream#skip(long)} are not counted.
     *
     * @param request the request whose configured limit to enforce.
     * @param in the payload {@link InputStream} to bound.
     * @return {@code in}, bounded by the request's aggregation size limit when one applies.
     */
    public static InputStream applyAggregationSizeLimit(final StreamingHttpRequest request, final InputStream in) {
        final LongConsumer limiter = aggregationSizeLimiter(request);
        return limiter == null ? in : new PayloadSizeLimitingInputStream(in, limiter);
    }

    /**
     * The effective aggregation size limiter for {@code request}, or {@code null} when none applies (the request was
     * not created by the transport, or its limit is disabled).
     */
    @Nullable
    private static LongConsumer aggregationSizeLimiter(final StreamingHttpRequest request) {
        if (!(request instanceof DefaultStreamingHttpRequest)) {
            return null;
        }
        final LongConsumer limiter = ((DefaultStreamingHttpRequest) request).payloadHolder().payloadSizeLimiter();
        return limiter == StreamingHttpPayloadHolder.NO_AGGREGATED_PAYLOAD_LIMIT ? null : limiter;
    }

    private static final class PayloadSizeLimitingInputStream extends FilterInputStream {
        private final LongConsumer payloadSizeLimiter;
        private long aggregatedSize;

        PayloadSizeLimitingInputStream(final InputStream in, final LongConsumer payloadSizeLimiter) {
            super(in);
            this.payloadSizeLimiter = payloadSizeLimiter;
        }

        @Override
        public int read() throws IOException {
            final int b = in.read();
            if (b >= 0) {
                payloadSizeLimiter.accept(++aggregatedSize);
            }
            return b;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int read = in.read(b, off, len);
            if (read > 0) {
                aggregatedSize += read;
                payloadSizeLimiter.accept(aggregatedSize);
            }
            return read;
        }
    }
}
