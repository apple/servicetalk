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
import io.servicetalk.capacity.limiter.api.RequestRejectedException;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.traffic.resilience.http.PeerCapacityRejectionPolicy.Type.REJECT;
import static io.servicetalk.traffic.resilience.http.PeerCapacityRejectionPolicy.Type.REJECT_PASSTHROUGH;
import static io.servicetalk.traffic.resilience.http.PeerCapacityRejectionPolicy.Type.REJECT_RETRY;
import static java.time.Duration.ZERO;
import static java.util.Objects.requireNonNull;

/**
 * Policy for peer capacity rejections that allows customization of behavior (retries or pass-through).
 */
public final class PeerCapacityRejectionPolicy {

    enum Type {
        REJECT,
        REJECT_PASSTHROUGH,
        REJECT_RETRY,
    }

    private final Predicate<HttpResponseMetaData> predicate;
    private final Type type;
    private final Function<HttpResponseMetaData, Duration> delayProvider;

    private PeerCapacityRejectionPolicy(final Predicate<HttpResponseMetaData> predicate,
                                        final Type type) {
        this.predicate = predicate;
        this.type = type;
        this.delayProvider = __ -> ZERO;
    }

    PeerCapacityRejectionPolicy(final Predicate<HttpResponseMetaData> predicate,
                                        final Type type,
                                        final Function<HttpResponseMetaData, Duration> delayProvider) {
        this.predicate = predicate;
        this.type = type;
        this.delayProvider = delayProvider;
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, but allow the original response from the upstream to pass-through this filter.
     * @param predicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @return A {@link PeerCapacityRejectionPolicy}.
     */
    public static PeerCapacityRejectionPolicy ofPassthrough(final Predicate<HttpResponseMetaData> predicate) {
        return new PeerCapacityRejectionPolicy(predicate, REJECT_PASSTHROUGH);
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, and translate that to en exception.
     * @param rejectionPredicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @return A {@link PeerCapacityRejectionPolicy}.
     */
    public static PeerCapacityRejectionPolicy ofRejection(
            final Predicate<HttpResponseMetaData> rejectionPredicate) {
        return new PeerCapacityRejectionPolicy(rejectionPredicate, REJECT);
    }

    /**
     * Evaluate responses with the given {@link Predicate} as capacity related rejections, that will affect the
     * {@link CapacityLimiter} in use, and translate that to an exception that contains "delay" information useful when
     * retrying it through a retrying filter.
     * @param rejectionPredicate The {@link Predicate} to evaluate responses.
     * Returning <code>true</code> from this {@link Predicate} signifies that the response was capacity
     * related rejection from the peer.
     * @param delayProvider A {@link Duration} provider for delay purposes when retrying.
     * @return A {@link PeerCapacityRejectionPolicy}.
     */
    public static PeerCapacityRejectionPolicy ofRejectionWithRetries(
            final Predicate<HttpResponseMetaData> rejectionPredicate,
            final Function<HttpResponseMetaData, Duration> delayProvider) {
        return new PeerCapacityRejectionPolicy(rejectionPredicate, REJECT_RETRY, delayProvider);
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

    static final class PassthroughRequestRejectedException extends RequestRejectedException {
        private static final long serialVersionUID = 5494523265208777384L;
        private final StreamingHttpResponse response;
        PassthroughRequestRejectedException(final String msg, final StreamingHttpResponse response) {
            super(msg);
            this.response = requireNonNull(response);
        }

        StreamingHttpResponse response() {
            return response;
        }
    }
}
