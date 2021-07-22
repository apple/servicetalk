/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.Reporter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.BufferStrategies.forCountOrTime;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static zipkin2.CheckResult.OK;
import static zipkin2.CheckResult.failed;

/**
 * A {@link Span} {@link Reporter} that will publish to an HTTP endpoint with a configurable encoding {@link Codec}.
 */
public final class HttpReporter extends Component implements Reporter<Span>, AsyncCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpReporter.class);
    static final String V1_PATH = "/api/v1/spans";
    static final String V2_PATH = "/api/v2/spans";
    static final CharSequence THRIFT_CONTENT_TYPE = newAsciiString("application/x-thrift");
    static final CharSequence PROTO_CONTENT_TYPE = newAsciiString("application/protobuf");

    private final PublisherSource.Processor<Span, Span> buffer;
    private final CompositeCloseable closeable;

    private volatile boolean closeInitiated;

    private HttpReporter(final Builder builder) {
        closeable = newCompositeCloseable();
        final HttpClient client = closeable.append(builder.clientBuilder.build());
        try {
            buffer = initReporter(builder, client);
        } catch (Throwable t) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close the client.", e);
            }
            throw t;
        }
    }

    @Override
    public CheckResult check() {
        return closeInitiated ? failed(new IllegalStateException("Reporter is closed.")) : OK;
    }

    @Override
    public void report(final Span span) {
        if (closeInitiated) {
            throw new IllegalStateException("Span: " + span + " reported after reporter " + this + " is closed.");
        }
        buffer.onNext(span);
    }

    @Override
    public void close() {
        awaitTermination(closeable.closeAsync().toFuture());
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }

    private PublisherSource.Processor<Span, Span> initReporter(final Builder builder, final HttpClient client) {
        final PublisherSource.Processor<Span, Span> buffer;
        SpanBytesEncoder spanEncoder = builder.codec.spanBytesEncoder();
        final BufferAllocator allocator = client.executionContext().bufferAllocator();
        final Publisher<Buffer> spans;
        if (builder.disableBatching) {
            buffer = newPublisherProcessorDropHeadOnOverflow(builder.maxConcurrentReports);
            spans = fromSource(buffer).map(span -> allocator.wrap(spanEncoder.encode(span)));
        } else {
            // As we send maxConcurrentReports number of parallel requests, each with roughly batchSizeHint number of
            // spans, we hold a maximum of that many Spans in-memory that we can send in parallel to the collector.
            buffer = newPublisherProcessorDropHeadOnOverflow(builder.batchSizeHint * builder.maxConcurrentReports);
            spans = fromSource(buffer)
                    .buffer(forCountOrTime(builder.batchSizeHint, builder.maxBatchDuration,
                            () -> new ListAccumulator(builder.batchSizeHint), client.executionContext().executor()))
                    .filter(accumulate -> !accumulate.isEmpty())
                    .map(bufferedSpans -> allocator.wrap(spanEncoder.encodeList(bufferedSpans)));
        }

        final CompletableSource.Processor spansTerminated = newCompletableProcessor();
        toSource(spans.flatMapCompletable(encodedSpansReporter(client, builder.codec), builder.maxConcurrentReports))
                .subscribe(spansTerminated);

        closeable.prepend(toAsyncCloseable(graceful -> {
            closeInitiated = true;
            try {
                buffer.onComplete();
            } catch (Throwable t) {
                LOGGER.error("Failed to dispose request buffer. Ignoring.", t);
            }
            return graceful ? fromSource(spansTerminated) : completed();
        }));

        return buffer;
    }

    private static Function<Buffer, Completable> encodedSpansReporter(final HttpClient client, final Codec codec) {
        final String path;
        final CharSequence contentType;
        switch (codec) {
            case JSON_V1:
                path = V1_PATH;
                contentType = APPLICATION_JSON;
                break;
            case JSON_V2:
                path = V2_PATH;
                contentType = APPLICATION_JSON;
                break;
            case THRIFT:
                path = V2_PATH;
                contentType = THRIFT_CONTENT_TYPE;
                break;
            case PROTO3:
                path = V2_PATH;
                contentType = PROTO_CONTENT_TYPE;
                break;
            default:
                throw new IllegalArgumentException("Unknown codec: " + codec);
        }
        return encodedSpans -> client.request(
                client.post(path).setHeader(CONTENT_TYPE, contentType).payloadBody(encodedSpans))
                .beforeOnSuccess(response -> {
                    HttpResponseStatus status = response.status();
                    if (status.statusClass() != SUCCESSFUL_2XX) {
                        LOGGER.info("Unexpected response from the collector. Response headers: {}",
                                response.toString((__, headerValue) -> headerValue));
                    }
                })
                .ignoreElement()
                .onErrorComplete(cause -> {
                    LOGGER.error("Failed to send a span, ignoring.", cause);
                    return true;
                });
    }

    /**
     * A builder to create a new {@link HttpReporter}.
     */
    public static final class Builder {
        private Codec codec = Codec.JSON_V2;
        private final SingleAddressHttpClientBuilder<?, ?> clientBuilder;
        private boolean disableBatching;
        private int batchSizeHint = 16;
        private int maxConcurrentReports = 32;
        private Duration maxBatchDuration = ofSeconds(30);

        /**
         * Create a new {@link Builder} using the passed {@link SingleAddressHttpClientBuilder}.
         *
         * @param clientBuilder the collector SocketAddress
         */
        public Builder(final SingleAddressHttpClientBuilder<?, ?> clientBuilder) {
            this.clientBuilder = clientBuilder;
        }

        /**
         * Sets the {@link Codec} to encode the Spans with.
         *
         * @param codec the codec to use for this span.
         * @return {@code this}
         */
        public Builder codec(Codec codec) {
            this.codec = requireNonNull(codec);
            return this;
        }

        /**
         * Sets the maximum number of concurrent requests that will be made to the zipkin collector at any time.
         *
         * @param maxConcurrentReports maximum number of concurrent requests that will be made to the zipkin collector
         * at any time.
         * @return {@code this}.
         */
        public Builder maxConcurrentReports(final int maxConcurrentReports) {
            if (maxConcurrentReports <= 0) {
                throw new IllegalArgumentException("maxConcurrentReports: " + maxConcurrentReports + " (expected > 0)");
            }
            this.maxConcurrentReports = maxConcurrentReports;
            return this;
        }

        /**
         * Configure batching of spans before sending it to the zipkin collector.
         *
         * @param batchSizeHint Hint of how many spans should be batched together.
         * @param maxBatchDuration {@link Duration} of time to wait for {@code batchSizeHint} spans in a batch.
         * @return {@code this}.
         */
        public Builder batchSpans(final int batchSizeHint, final Duration maxBatchDuration) {
            if (batchSizeHint <= 0) {
                throw new IllegalArgumentException("batchSizeHint: " + batchSizeHint + " (expected > 0)");
            }
            disableBatching = false;
            this.batchSizeHint = batchSizeHint;
            this.maxBatchDuration = requireNonNull(maxBatchDuration);
            return this;
        }

        /**
         * Disable batching of spans before sending them to the zipkin collector.
         *
         * @return {@code this}.
         */
        public Builder disableSpanBatching() {
            disableBatching = true;
            return this;
        }

        /**
         * Builds a new {@link HttpReporter} instance with this builder's options.
         *
         * @return a new {@link HttpReporter}
         */
        public HttpReporter build() {
            return new HttpReporter(this);
        }
    }

    private static final class ListAccumulator implements Accumulator<Span, List<Span>> {
        private final List<Span> accumulate;

        ListAccumulator(final int size) {
            accumulate = new ArrayList<>(size);
        }

        @Override
        public void accumulate(@Nonnull final Span item) {
            accumulate.add(requireNonNull(item));
        }

        @Override
        public List<Span> finish() {
            return accumulate;
        }
    }
}
