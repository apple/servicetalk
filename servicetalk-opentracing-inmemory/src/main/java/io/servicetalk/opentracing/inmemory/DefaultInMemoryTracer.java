/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemoryReference;
import io.servicetalk.opentracing.inmemory.api.InMemoryScopeManager;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanBuilder;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanEventListener;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;

import io.opentracing.Scope;
import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.internal.HexUtils.hexBytesOfLong;
import static java.util.Objects.requireNonNull;

/**
 * Tracer implementation that propagates spans in-memory and emits events to listeners.
 */
public final class DefaultInMemoryTracer extends AbstractInMemoryTracer {
    private static final Logger logger = LoggerFactory.getLogger(DefaultInMemoryTracer.class);
    private final InMemoryScopeManager scopeManager;
    private final BiFunction<String, Boolean, Boolean> sampler;
    private final InMemorySpanEventListener listeners;
    private final int maxTagSize;
    private final boolean persistLogs;
    private final boolean use128BitTraceId;

    /**
     * Builders for {@link DefaultInMemoryTracer}.
     */
    public static final class Builder {
        private final InMemoryScopeManager scopeManager;
        private final CopyOnWriteInMemorySpanEventListenerSet listeners = new CopyOnWriteInMemorySpanEventListenerSet();
        private BiFunction<String, Boolean, Boolean> sampler = SamplingStrategies.sampleUnlessFalse();
        private int maxTagSize = 16;
        private boolean persistLogs;
        private boolean use128BitTraceId;

        /**
         * Constructs a builder for an {@link DefaultInMemoryTracer}.
         *
         * @param scopeManager a {@link InMemoryScopeManager}.
         */
        public Builder(InMemoryScopeManager scopeManager) {
            this.scopeManager = requireNonNull(scopeManager);
        }

        /**
         * Sets the sampler.
         *
         * @param sampler policy which takes a traceId and returns whether the given trace should be sampled
         * @return this
         */
        public Builder withSampler(Predicate<String> sampler) {
            requireNonNull(sampler);
            withSampler((traceId, requested) -> requested != null ? requested : sampler.test(traceId));
            return this;
        }

        /**
         * Sets the sampler.
         *
         * @param sampler policy which takes a traceId and the sampling flag specified in carrier (optional,
         *                could be {@code null}), and returns whether the given trace should be sampled.
         * @return this
         */
        public Builder withSampler(BiFunction<String, Boolean, Boolean> sampler) {
            this.sampler = requireNonNull(sampler);
            return this;
        }

        /**
         * Add a trace event listener.
         *
         * @param listener listener to add
         * @return this
         */
        public Builder addListener(InMemorySpanEventListener listener) {
            listeners.add(requireNonNull(listener));
            return this;
        }

        /**
         * Sets the maximum number of tags.
         *
         * @param maxTagSize maximum number of tags
         * @return this
         */
        public Builder withMaxTagSize(int maxTagSize) {
            if (maxTagSize < 0) {
                throw new IllegalArgumentException("maxTagSize must be >= 0");
            }
            this.maxTagSize = maxTagSize;
            return this;
        }

        /**
         * Sets whether logs are persisted in the span object. This is necessary when using using
         * listeners which sends the span to a backend on span finish.
         *
         * @param persistLogs whether to persist logs in the span object. Defaults to false.
         * @return this
         */
        public Builder persistLogs(boolean persistLogs) {
            this.persistLogs = persistLogs;
            return this;
        }

        /**
         * Sets whether to use 128-bit trace IDs.
         *
         * @param use128BitTraceId whether to use 128-bit trace IDs.
         * @return this
         */
        public Builder use128BitTraceId(boolean use128BitTraceId) {
            this.use128BitTraceId = use128BitTraceId;
            return this;
        }

        /**
         * Builds the {@link DefaultInMemoryTracer}.
         *
         * @return tracer
         */
        public DefaultInMemoryTracer build() {
            return new DefaultInMemoryTracer(scopeManager, sampler, listeners, maxTagSize, persistLogs,
                    use128BitTraceId);
        }
    }

    private DefaultInMemoryTracer(
            InMemoryScopeManager scopeManager, BiFunction<String, Boolean, Boolean> sampler,
            InMemorySpanEventListener listeners, int maxTagSize, boolean persistLogs,
            boolean use128BitTraceId) {
        this.scopeManager = scopeManager;
        this.sampler = sampler;
        this.listeners = listeners;
        this.maxTagSize = maxTagSize;
        this.persistLogs = persistLogs;
        this.use128BitTraceId = use128BitTraceId;
    }

    @Override
    public InMemoryScopeManager scopeManager() {
        return scopeManager;
    }

    @Nullable
    @Override
    public InMemorySpan activeSpan() {
        return scopeManager.activeSpan();
    }

    @Override
    public Scope activateSpan(Span span) {
        return scopeManager.activate(span);
    }

    @Override
    public InMemorySpanBuilder buildSpan(final String operationName) {
        return new DefaultInMemorySpanBuilder(operationName);
    }

    @Override
    public void close() {
        //noop
    }

    @Override
    protected InMemorySpanContext newSpanContext(final InMemoryTraceState state) {
        return new DefaultInMemorySpanContext(state, isSampled(state.traceIdHex(), state.isSampled()));
    }

    private final class DefaultInMemorySpanBuilder extends AbstractInMemorySpanBuilder {
        DefaultInMemorySpanBuilder(String operationName) {
            super(operationName, DefaultInMemoryTracer.this.maxTagSize);
        }

        @Override
        protected InMemorySpan createSpan(
                @Nullable String kind, String operationName, List<InMemoryReference> references,
                Map<String, Object> tags, int maxTagSize, boolean ignoreActiveSpan, long startTimestampMicros) {
            InMemorySpanContext maybeParent = parent();
            if (maybeParent == null && !ignoreActiveSpan) {
                // Try to infer the parent based upon the ScopeManager's active Scope.
                InMemorySpan span = scopeManager().activeSpan();
                if (span != null) {
                    maybeParent = span.context();
                }
            }

            final String traceIdHex;
            final String spanIdHex;
            final String parentSpanIdHex;
            final boolean sampled;
            if (maybeParent != null) {
                traceIdHex = maybeParent.traceState().traceIdHex();
                spanIdHex = nextId();
                parentSpanIdHex = maybeParent.traceState().spanIdHex();
                sampled = maybeParent.isSampled();
            } else {
                spanIdHex = nextId();
                traceIdHex = use128BitTraceId ? nextId() + spanIdHex : spanIdHex;
                parentSpanIdHex = null;
                sampled = isSampled(traceIdHex, null);
            }

            if (sampled) {
                SampledInMemorySpan span = new SampledInMemorySpan(operationName, references,
                        traceIdHex, spanIdHex, parentSpanIdHex, tags, maxTagSize, startTimestampMicros,
                        listeners, persistLogs);
                span.start();
                return span;
            } else {
                return new UnsampledInMemorySpan(operationName, references, traceIdHex, spanIdHex, parentSpanIdHex);
            }
        }
    }

    private static String nextId() {
        // We should be careful to select a randomly generated ID. If we choose to use a counter, and another
        // application also chooses a counter there is a chance we will be synchronized and have a higher probability of
        // overlapping IDs.
        return hexBytesOfLong(ThreadLocalRandom.current().nextLong());
    }

    private boolean isSampled(String traceId, @Nullable Boolean requestedByCarrier) {
        try {
            return sampler.apply(traceId, requestedByCarrier);
        } catch (Throwable t) {
            logger.warn("Exception from sampler={}, default to not sampling", sampler, t);
            return false; // play safe, default to not sampling
        }
    }
}
