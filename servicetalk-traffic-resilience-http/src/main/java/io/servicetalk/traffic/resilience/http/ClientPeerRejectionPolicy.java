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
import io.servicetalk.capacity.limiter.api.RequestDroppedException;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT_PASSTHROUGH;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.Type.REJECT_RETRY;
import static java.time.Duration.ZERO;
import static java.util.Objects.requireNonNull;

/**
 * Policy for peer capacity rejections that allows customization of behavior (retries or pass-through).
 * This is meant to be used as a policy on the {@link TrafficResilienceHttpServiceFilter}.
 * @see TrafficResilienceHttpClientFilter.Builder#peerRejection(ClientPeerRejectionPolicy)
 */
public final class ClientPeerRejectionPolicy {

    /**
     * Default rejection observer for dropped requests from an external sourced.
     * see. {@link TrafficResilienceHttpClientFilter.Builder#peerRejection(ClientPeerRejectionPolicy)}.
     * <p>
     * The default predicate matches the following HTTP response codes:
     * <ul>
     *     <li>{@link HttpResponseStatus#TOO_MANY_REQUESTS}</li>
     *     <li>{@link HttpResponseStatus#BAD_GATEWAY}</li>
     *     <li>{@link HttpResponseStatus#SERVICE_UNAVAILABLE}</li>
     * </ul>
     * <p>
     * If a {@link CircuitBreaker} is used consider adjusting this predicate to avoid considering
     * {@link HttpResponseStatus#SERVICE_UNAVAILABLE} as a capacity issue.
     */
    public static final Predicate<HttpResponseMetaData> DEFAULT_CAPACITY_REJECTION_PREDICATE = metaData ->
            // Some proxies are known to return BAD_GATEWAY when the upstream is unresponsive (i.e. heavy load).
            metaData.status().code() == TOO_MANY_REQUESTS.code() || metaData.status().code() == BAD_GATEWAY.code() ||
                    metaData.status().code() == SERVICE_UNAVAILABLE.code();

    /**
     * Default rejection policy for peer responses.
     * The following responses will be considered rejections, and exercise the rejection policy;
     * <ul>
     *     <li>{@link HttpResponseStatus#TOO_MANY_REQUESTS}</li>
     *     <li>{@link HttpResponseStatus#BAD_GATEWAY}</li>
     *     <li>{@link HttpResponseStatus#SERVICE_UNAVAILABLE}</li>
     * </ul>
     * <p>
     * The default behavior upon such a case, is to issue a retryable exception with no pre-set offset delay (outside
     * the default backoff policy of configured retry filter).
     */
    public static final ClientPeerRejectionPolicy DEFAULT_PEER_REJECTION_POLICY =
            new ClientPeerRejectionPolicy(DEFAULT_CAPACITY_REJECTION_PREDICATE, REJECT_RETRY, __ -> ZERO);

    enum Type {
        REJECT,
        REJECT_PASSTHROUGH,
        REJECT_RETRY,
    }

    private final Predicate<HttpResponseMetaData> predicate;
    private final Type type;
    private final Function<HttpResponseMetaData, Duration> delayProvider;

    private ClientPeerRejectionPolicy(final Predicate<HttpResponseMetaData> predicate,
                                      final Type type) {
        this.predicate = predicate;
        this.type = type;
        this.delayProvider = __ -> ZERO;
    }

    ClientPeerRejectionPolicy(final Predicate<HttpResponseMetaData> predicate,
                              final Type type,
                              final Function<HttpResponseMetaData, Duration> delayProvider) {
        this.predicate = predicate;
        this.type = type;
        this.delayProvider = delayProvider;
    }

    Predicate<HttpResponseMetaData> predicate() {
        return predicate;
    }

    Type type() {
        return type;
    }

    Function<HttpResponseMetaData, Duration> delayProvider() {
        return delayProvider;
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, but allow the original response from the upstream to pass-through this filter.
     * @param predicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @return A {@link ClientPeerRejectionPolicy}.
     */
    public static ClientPeerRejectionPolicy ofPassthrough(final Predicate<HttpResponseMetaData> predicate) {
        return new ClientPeerRejectionPolicy(predicate, REJECT_PASSTHROUGH);
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, and translate that to en exception.
     * @param rejectionPredicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @return A {@link ClientPeerRejectionPolicy}.
     */
    public static ClientPeerRejectionPolicy ofRejection(
            final Predicate<HttpResponseMetaData> rejectionPredicate) {
        return new ClientPeerRejectionPolicy(rejectionPredicate, REJECT);
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, and translate that to an exception that contains "delay" information useful when
     * retrying it through a retrying filter.
     * @param rejectionPredicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @param delayProvider A {@link Duration} provider for delay purposes when retrying.
     * @return A {@link ClientPeerRejectionPolicy}.
     */
    public static ClientPeerRejectionPolicy ofRejectionWithRetries(
            final Predicate<HttpResponseMetaData> rejectionPredicate,
            final Function<HttpResponseMetaData, Duration> delayProvider) {
        return new ClientPeerRejectionPolicy(rejectionPredicate, REJECT_RETRY, delayProvider);
    }

    static final class PassthroughRequestDroppedException extends RequestDroppedException {
        private static final long serialVersionUID = 5494523265208777384L;
        private final StreamingHttpResponse response;
        PassthroughRequestDroppedException(final String msg, final StreamingHttpResponse response) {
            super(msg);
            this.response = requireNonNull(response);
        }

        StreamingHttpResponse response() {
            return response;
        }
    }
}
