/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiter.Ticket;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.RETRY_AFTER;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * Rejection Policy to rule the behavior of service rejections due to capacity or open circuit.
 * This is meant to be used as a policy on the {@link TrafficResilienceHttpServiceFilter}.
 * <p>
 * @see TrafficResilienceHttpServiceFilter.Builder#onRejectionPolicy(RejectionPolicy)
 */
public final class RejectionPolicy {

    /**
     * Custom retry-after header that supports milliseconds resolution, rather than seconds.
     */
    public static final CharSequence RETRY_AFTER_MILLIS = newAsciiString("retry-after-millis");

    private final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
            onLimitResponseBuilder;

    private final Consumer<HttpResponseMetaData> onLimitRetryAfter;

    private final boolean onLimitStopAcceptingConnections;

    private final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
            onOpenCircuitResponseBuilder;

    private final BiConsumer<HttpResponseMetaData, StateContext> onOpenCircuitRetryAfter;

    private RejectionPolicy(final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
            Single<StreamingHttpResponse>> onLimitResponseBuilder,
                            final Consumer<HttpResponseMetaData> onLimitRetryAfter,
                            final boolean onLimitStopAcceptingConnections,
                            final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
                                    Single<StreamingHttpResponse>> onOpenCircuitResponseBuilder,
                            final BiConsumer<HttpResponseMetaData, StateContext>
                                    onOpenCircuitRetryAfter) {
        this.onLimitResponseBuilder = onLimitResponseBuilder;
        this.onLimitRetryAfter = onLimitRetryAfter;
        this.onLimitStopAcceptingConnections = onLimitStopAcceptingConnections;
        this.onOpenCircuitResponseBuilder = onOpenCircuitResponseBuilder;
        this.onOpenCircuitRetryAfter = onOpenCircuitRetryAfter;
    }

    BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
    onLimitResponseBuilder() {
        return onLimitResponseBuilder;
    }

    Consumer<HttpResponseMetaData> onLimitRetryAfter() {
        return onLimitRetryAfter;
    }

    boolean onLimitStopAcceptingConnections() {
        return onLimitStopAcceptingConnections;
    }

    BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
    onOpenCircuitResponseBuilder() {
        return onOpenCircuitResponseBuilder;
    }

    BiConsumer<HttpResponseMetaData, StateContext> onOpenCircuitRetryAfter() {
        return onOpenCircuitRetryAfter;
    }

    /**
     * Default response rejection policy.
     * <ul>
     *     <li>When a request is rejected due to capacity, the service will respond
     *     {@link RejectionPolicy#tooManyRequests()}.</li>
     *     <li>When a request is rejected due to capacity, the service will NOT include a retry-after header.</li>
     *     <li>When a request is rejected due to breaker, the service will respond
     *     {@link RejectionPolicy#serviceUnavailable()}.</li>
     *     <li>When a request is rejected due to breaker, the service will respond with Retry-After header hinting
     *     the duration the breaker will remain open.</li>
     * </ul>
     *
     * @return The default {@link RejectionPolicy}.
     */
    public static RejectionPolicy defaultRejectionResponsePolicy() {
        return new RejectionPolicy.Builder().build();
    }

    /**
     * A hard-coded delay in seconds to be supplied as a Retry-After HTTP header in a {@link HttpResponseMetaData}.
     *
     * @param seconds The value (in seconds) to be used in the Retry-After header.
     * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a fixed Retry-After figure in
     * seconds.
     */
    public static Consumer<HttpResponseMetaData> retryAfterHint(final int seconds) {
        final CharSequence secondsSeq = newAsciiString(valueOf(seconds));
        return resp -> resp.addHeader(RETRY_AFTER, secondsSeq);
    }

    /**
     * A delay in seconds to be supplied as a Retry-After HTTP header in a {@link HttpResponseMetaData} based on the
     * {@link CircuitBreaker} that matched the {@link HttpRequestMetaData}.
     *
     * @param fallbackSeconds The value (in seconds) to be used if no {@link CircuitBreaker} matched.
     * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a Retry-After figure in
     * seconds based on the duration the matching {@link CircuitBreaker} will remain open, or a fallback period.
     */
    public static BiConsumer<HttpResponseMetaData, StateContext>
    retryAfterHintOfBreaker(final int fallbackSeconds) {
        final CharSequence secondsSeq = newAsciiString(valueOf(fallbackSeconds));
        return (resp, state) -> {
            if (state.breaker() != null || fallbackSeconds > 0) {
                resp.setHeader(RETRY_AFTER, state.breaker() != null ? newAsciiString(valueOf(
                        state.breaker().remainingDurationInOpenState().getSeconds())) : secondsSeq);
            }
        };
    }

    /**
     * A hard-coded delay in milliseconds to be supplied as a Retry-After-Millis HTTP header in a
     * {@link HttpResponseMetaData}. Being a custom Http header, it will require special handling on the peer side.
     *
     * @param duration The duration to be used in the Retry-After-Millis header.
     * @return A {@link HttpResponseMetaData} consumer, that enhances the headers with a fixed
     * Retry-After-Millis figure in milliseconds.
     */
    public static BiConsumer<HttpResponseMetaData, CircuitBreaker> retryAfterMillisHint(final Duration duration) {
        final CharSequence millisSeq = newAsciiString(valueOf(duration.toMillis()));
        return (resp, breaker) -> resp.setHeader(RETRY_AFTER_MILLIS, millisSeq);
    }

    /**
     * Pre-defined {@link StreamingHttpResponse response} that signals
     * {@link io.servicetalk.http.api.HttpResponseStatus#TOO_MANY_REQUESTS} to the peer.
     *
     * @return A {@link BiFunction} that regardless the input, it will always return a
     * {@link StreamingHttpResponseFactory#tooManyRequests() too-many-requests} response.
     */
    public static BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
    tooManyRequests() {
        return (__, factory) -> succeeded(factory.tooManyRequests());
    }

    /**
     * Pre-defined {@link StreamingHttpResponse response} that signals
     * {@link io.servicetalk.http.api.HttpResponseStatus#SERVICE_UNAVAILABLE} to the peer.
     *
     * @return A {@link BiFunction} that regardless the input, it will always return a
     * {@link StreamingHttpResponseFactory#serviceUnavailable() service-unavailable} response.
     */
    public static BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
    serviceUnavailable() {
        return (__, factory) -> succeeded(factory.serviceUnavailable());
    }

    /**
     * A {@link RejectionPolicy} builder to support a custom policy.
     */
    public static final class Builder {
        private BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                onLimitResponseBuilder = tooManyRequests();

        private Consumer<HttpResponseMetaData> onLimitRetryAfter = __ -> { };

        private boolean onLimitStopAcceptingConnections;

        private BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
                onOpenCircuitResponseBuilder = serviceUnavailable();

        private BiConsumer<HttpResponseMetaData, StateContext> onOpenCircuitRetryAfter =
                retryAfterHintOfBreaker(-1);

        /**
         * Determines the {@link StreamingHttpResponse} when a capacity limit is met.
         *
         * @param onLimitResponseBuilder A factory function used to generate a {@link StreamingHttpResponse} based
         * on the {@link HttpRequestMetaData request} when a {@link CapacityLimiter capacity} limit is observed.
         * @return {@code this}.
         */
        public Builder onLimitResponseBuilder(final BiFunction<HttpRequestMetaData, StreamingHttpResponseFactory,
                Single<StreamingHttpResponse>> onLimitResponseBuilder) {
            this.onLimitResponseBuilder = requireNonNull(onLimitResponseBuilder);
            return this;
        }

        /**
         * Determines a {@link HttpHeaderNames#RETRY_AFTER retry-after} header in the
         * {@link StreamingHttpResponse} when a capacity limit is met.
         *
         * @param onLimitRetryAfter A {@link HttpResponseMetaData} consumer, that can allow response decoration with
         * additional headers to hint the peer (upon capacity limits) about a possible wait-time before a
         * retry could be issued.
         * @return {@code this}.
         */
        public Builder onLimitRetryAfter(final Consumer<HttpResponseMetaData> onLimitRetryAfter) {
            this.onLimitRetryAfter = requireNonNull(onLimitRetryAfter);
            return this;
        }

        /**
         * When a certain {@link CapacityLimiter} rejects a request due to the active limit,
         * (e.g., no {@link Ticket} is returned) influence the server to also stop accepting new connections
         * until the capacity is under healthy conditions again.
         * <b>This setting only works when a {@link CapacityLimiter} matches the incoming request, in cases this
         * doesn't hold (see. {@link TrafficResilienceHttpServiceFilter.Builder#Builder(Supplier, boolean)}
         * Builder's rejectedNotMatched argument}) this won't be effective.</b>
         * <p>
         * When a server socket stops accepting new connections
         * (see. {@link HttpServiceContext#acceptConnections(boolean)}) due to capacity concerns, the state will be
         * toggled back when the {@link Ticket ticket's} terminal callback ({@link Ticket#dropped() dropped},
         * {@link Ticket#failed(Throwable) failed}, {@link Ticket#completed() completed}, {@link Ticket#ignored()
         * ignored}) returns a positive or negative value, demonstrating available capacity or not_supported
         * respectively. When the returned value is {@code 0} that means no-capacity available, which will keep the
         * server in the not-accepting mode.
         * <p>
         * When enabling this feature, it's recommended for clients using this service to configure timeouts
         * for their opening connection time and connection idleness time. For example, a client without
         * connection-timeout or idle-timeout on the outgoing connections towards this service, won't be able to
         * detect on time the connection delays. Likewise, on the server side you can configure the
         * {@link io.servicetalk.transport.api.ServiceTalkSocketOptions#SO_BACKLOG server backlog} to a very small
         * number or even disable it completely, to avoid holding established connections in the OS.
         * <p>
         * Worth noting that established connections that stay in the OS backlog, usually have a First In First Out
         * behavior, which depending on the size of that queue, may result in extending latencies on newer
         * requests because older ones are served first. Disabling the
         * {@link io.servicetalk.transport.api.ServiceTalkSocketOptions#SO_BACKLOG server backlog} will give a
         * better behavior.
         *
         * @param stopAccepting {@code true} will allow this filter to control the connection acceptance of the
         * overall server socket.
         * @return {@code this}.
         */
        public Builder onLimitStopAcceptingConnections(final boolean stopAccepting) {
            this.onLimitStopAcceptingConnections = stopAccepting;
            return this;
        }

        /**
         * Determines the {@link StreamingHttpResponse} when a circuit-breaker limit is met.
         *
         * @param onOpenCircuitResponseBuilder A factory function used to generate a {@link StreamingHttpResponse}
         * based on the {@link HttpRequestMetaData request} when an open {@link CircuitBreaker breaker} is observed.
         * @return {@code this}.
         */
        public Builder onOpenCircuitResponseBuilder(final BiFunction<HttpRequestMetaData,
                StreamingHttpResponseFactory, Single<StreamingHttpResponse>> onOpenCircuitResponseBuilder) {
            this.onOpenCircuitResponseBuilder = requireNonNull(onOpenCircuitResponseBuilder);
            return this;
        }

        /**
         * Determines a {@link HttpHeaderNames#RETRY_AFTER retry-after} header in the
         * {@link StreamingHttpResponse} when a capacity limit is met.
         *
         * @param onOpenCircuitRetryAfter A {@link HttpResponseMetaData} consumer, that can allow response
         * decoration with additional headers to hint the peer (upon open breaker) about a possible wait-time
         * before a retry could be issued.
         * @return {@code this}.
         */
        public Builder onOpenCircuitRetryAfter(final BiConsumer<HttpResponseMetaData,
                StateContext> onOpenCircuitRetryAfter) {
            this.onOpenCircuitRetryAfter = requireNonNull(onOpenCircuitRetryAfter);
            return this;
        }

        /**
         * Return a custom {@link RejectionPolicy} based on the options of this builder.
         *
         * @return A custom {@link RejectionPolicy} based on the options of this builder.
         */
        public RejectionPolicy build() {
            return new RejectionPolicy(onLimitResponseBuilder, onLimitRetryAfter, onLimitStopAcceptingConnections,
                    onOpenCircuitResponseBuilder, onOpenCircuitRetryAfter);
        }
    }
}
