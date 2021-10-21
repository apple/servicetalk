/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter.BackOffPolicy;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter.DefaultRequestRetryPolicyBuilder;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
 * To configure the maximum number of retry attempts see {@link #maxTotalRetries(int)}.
 */
public final class RetryingHttpRequesterFilterBuilder {
    private boolean waitForLb = true;
    private boolean ignoreSdErrors;

    private int maxRetries = 3;

    @Nullable
    private Function<HttpResponseMetaData, BackOffPolicy> retryForResponsesMapper;

    @Nullable
    private BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy>
            retryForRequestsMapper = new DefaultRequestRetryPolicyBuilder().build();

    /**
     * By default, automatic retries wait for the associated {@link LoadBalancer} to be ready before triggering a
     * retry for requests. This behavior may add latency to requests till the time the load balancer is ready
     * instead of failing fast. This method allows controlling that behavior.
     *
     * @param waitForLb Whether to wait for the {@link LoadBalancer} to be ready before retrying requests.
     * @return {@code this}.
     */
    public RetryingHttpRequesterFilterBuilder waitForLoadBalancer(final boolean waitForLb) {
        this.waitForLb = waitForLb;
        return this;
    }

    /**
     * By default, fail a request if the last signal from the associated {@link ServiceDiscoverer} was an error.
     * This method disables that behavior.
     *
     * @param ignoreSdErrors ignore {@link ServiceDiscoverer} errors when evaluating a request failure.
     * @return {@code this}.
     */
    public RetryingHttpRequesterFilterBuilder ignoreServiceDiscovererErrors(final boolean ignoreSdErrors) {
        this.ignoreSdErrors = ignoreSdErrors;
        return this;
    }

    /**
     * Set the maximum number of allowed retry operations before giving up, applied as total max across both
     * {@link #retryRequests(BiFunction)} and {@link #retryResponses(Function)}.
     *
     * @param maxRetries Maximum number of allowed retries before giving up
     * @return {@code this}
     */
    public RetryingHttpRequesterFilterBuilder maxTotalRetries(final int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries: " + maxRetries + " (expected: >0)");
        }
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Overrides the default criterion for determining which responses should be retried.
     * <strong>It's important that this {@link Function} doesn't block to avoid performance impacts.</strong>
     *
     * @param mapper {@link Function} that checks whether a given {@link HttpResponseMetaData meta-data} should
     * be retried, producing a {@link BackOffPolicy} in such cases.
     * @return {@code this}
     */
    public RetryingHttpRequesterFilterBuilder retryResponses(
            final Function<HttpResponseMetaData, BackOffPolicy> mapper) {
        this.retryForResponsesMapper = requireNonNull(mapper);
        return this;
    }

    /**
     * Overrides the default criterion for determining which requests or errors should be retried.
     * <strong>It's important that this {@link Function} doesn't block to avoid performance impacts.</strong>
     *
     * @param mapper {@link BiFunction} that checks whether a given combination of
     * {@link HttpRequestMetaData meta-data} and {@link Throwable cause} should be retried, producing a
     * {@link BackOffPolicy} in such cases.
     * @return {@code this}
     */
    public RetryingHttpRequesterFilterBuilder retryRequests(
            final BiFunction<HttpRequestMetaData, Throwable, BackOffPolicy> mapper) {
        this.retryForRequestsMapper = requireNonNull(mapper);
        return this;
    }

    /**
     * Builds a retrying {@link RetryingHttpRequesterFilter} with this' builders configuration.
     *
     * @return A new retrying {@link RetryingHttpRequesterFilter}
     */
    public RetryingHttpRequesterFilter build() {
        return new RetryingHttpRequesterFilter(waitForLb, ignoreSdErrors, maxRetries, retryForResponsesMapper,
                retryForRequestsMapper);
    }
}
