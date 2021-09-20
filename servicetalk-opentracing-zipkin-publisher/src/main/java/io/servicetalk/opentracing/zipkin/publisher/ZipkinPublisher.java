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
package io.servicetalk.opentracing.zipkin.publisher;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanEventListener;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanLog;

import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A publisher of {@link io.opentracing.Span}s to the zipkin transport.
 */
public final class ZipkinPublisher implements InMemorySpanEventListener, AsyncCloseable, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinPublisher.class);

    private final Reporter<Span> reporter;
    private final Endpoint endpoint;
    private final ListenableAsyncCloseable closeable;

    private ZipkinPublisher(final String serviceName,
                            final Reporter<Span> reporter,
                            @Nullable final InetSocketAddress localSocketAddress) {
        this.reporter = reporter;
        this.endpoint = buildEndpoint(serviceName, localSocketAddress);
        this.closeable = toAsyncCloseable(graceful -> {
            // Some Reporter implementations may batch and need an explicit flush before closing (AsyncReporter)
            Completable flush = Completable.completed();
            if (graceful && reporter instanceof Flushable) {
                flush = globalExecutionContext().executor().submit(() -> {
                    try {
                        ((Flushable) reporter).flush();
                    } catch (IOException e) {
                        LOGGER.error("Exception while flushing reporter: {}", e.getMessage(), e);
                    }
                });
            }
            // ST Reporters can implement AsyncCloseable so we wouldn't have to call a blocking close in that case
            Completable close = Completable.completed();
            if (reporter instanceof AsyncCloseable) {
                close = graceful ? ((AsyncCloseable) reporter).closeAsyncGracefully() :
                        ((AsyncCloseable) reporter).closeAsync();
            } else if (reporter instanceof Closeable) {
                close = globalExecutionContext().executor().submit(() -> {
                    try {
                        ((Closeable) reporter).close();
                    } catch (IOException e) {
                        LOGGER.error("Exception while closing reporter: {}", e.getMessage(), e);
                    }
                });
            }
            return flush.concat(close);
        });
    }

    /**
     * Builder for {@link ZipkinPublisher}.
     */
    public static final class Builder {

        private final String serviceName;
        private final Reporter<Span> reporter;
        @Nullable
        private InetSocketAddress localAddress;

        /**
         * Create a new instance.
         *
         * @param serviceName the service name.
         * @param reporter a {@link Reporter} implementation to send {@link Span}s to
         */
        public Builder(String serviceName, Reporter<Span> reporter) {
            this.serviceName = requireNonNull(serviceName);
            this.reporter = requireNonNull(reporter);
        }

        /**
         * Configures the local address.
         *
         * @param localAddress the local {@link InetSocketAddress}.
         * @return this.
         */
        public Builder localAddress(InetSocketAddress localAddress) {
            this.localAddress = requireNonNull(localAddress);
            return this;
        }

        /**
         * Builds the ZipkinPublisher with supplied options.
         *
         * @return A ZipkinPublisher which can publish tracing data using the zipkin Reporter API.
         */
        public ZipkinPublisher build() {
            return new ZipkinPublisher(serviceName, reporter, localAddress);
        }
    }

    private static Endpoint buildEndpoint(String serviceName, @Nullable InetSocketAddress localSocketAddress) {
        final Endpoint.Builder builder = Endpoint.newBuilder().serviceName(serviceName);
        if (localSocketAddress != null) {
            builder.ip(localSocketAddress.getAddress()).port(localSocketAddress.getPort());
        }
        return builder.build();
    }

    @Override
    public void onSpanStarted(final InMemorySpan span) {
        // nothing to do
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
        // nothing to do
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
        // nothing to do
    }

    /**
     * Converts a finished {@link InMemorySpan} to a {@link Span} and passes it to the {@link Reporter#report(Object)}.
     */
    @Override
    public void onSpanFinished(final InMemorySpan span, long durationMicros) {
        Span.Builder builder = Span.newBuilder()
                .name(span.operationName())
                .traceId(span.context().toTraceId())
                .id(span.context().toSpanId())
                .parentId(span.context().parentSpanId())
                .timestamp(span.startEpochMicros())
                .localEndpoint(endpoint)
                .duration(durationMicros);
        span.tags().forEach((k, v) -> builder.putTag(k, v.toString()));
        Iterable<? extends InMemorySpanLog> logs = span.logs();
        if (logs != null) {
            logs.forEach(log -> builder.addAnnotation(log.epochMicros(), log.eventName()));
        }
        Object type = span.tags().get(Tags.SPAN_KIND.getKey());
        boolean removeKindTag = true;
        if (Tags.SPAN_KIND_SERVER.equals(type)) {
            builder.kind(Span.Kind.SERVER);
        } else if (Tags.SPAN_KIND_CLIENT.equals(type)) {
            builder.kind(Span.Kind.CLIENT);
        } else if (Tags.SPAN_KIND_PRODUCER.equals(type)) {
            builder.kind(Span.Kind.PRODUCER);
        } else if (Tags.SPAN_KIND_CONSUMER.equals(type)) {
            builder.kind(Span.Kind.CONSUMER);
        } else {
            removeKindTag = false;
        }

        Span s = builder.build();
        if (removeKindTag) {
            s.tags().remove(Tags.SPAN_KIND.getKey());
        }
        try {
            reporter.report(s);
        } catch (Throwable t) {
            LOGGER.error("Failed to report a span {}", s, t);
        }
    }

    /**
     * Blocking close method delegates to {@link #closeAsync()}.
     */
    @Override
    public void close() {
        awaitTermination(closeable.closeAsync().toFuture());
    }

    /**
     * Attempts to close the configured {@link Reporter}.
     *
     * @return a {@link Completable} that is completed when the close is done
     */
    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    /**
     * Attempts to flush and close the configured {@link Reporter}.
     *
     * @return a {@link Completable} that is completed when the flush and close is done
     */
    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }
}
