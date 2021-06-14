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
package io.servicetalk.client.api;

import io.servicetalk.client.api.AutoRetryStrategyProvider.AutoRetryStrategy;
import io.servicetalk.client.api.DefaultAutoRetryStrategyProvider.Builder;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.function.UnaryOperator;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.IsNull.nullValue;

class DefaultAutoRetryStrategyProviderTest {
    private static final RetryableConnectException RETRYABLE_EXCEPTION =
            new RetryableConnectException("deliberate exception");
    private static final NoAvailableHostException NO_AVAILABLE_HOST =
            new NoAvailableHostException("deliberate exception");
    private static final UnknownHostException UNKNOWN_HOST_EXCEPTION =
            new UnknownHostException("deliberate exception");

    private final TestPublisher<Object> lbEvents;
    private final TestCompletable sdStatus;
    private final TestCompletableSubscriber retrySubscriber;

    DefaultAutoRetryStrategyProviderTest() {
        lbEvents = new TestPublisher<>();
        sdStatus = new TestCompletable();
        retrySubscriber = new TestCompletableSubscriber();
    }

    @Test
    void disableWaitForLb() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableWaitForLoadBalancer);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    void disableRetryAllRetryableExWithRetryable() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    void disableRetryAllRetryableExWithNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void disableRetryAllRetryableExWithNoAvailableHostAndUnknownHostException() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    void disableAll() {
        AutoRetryStrategy strategy = newStrategy(builder ->
                builder.disableWaitForLoadBalancer()
                        .disableRetryAllRetryableExceptions());
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    void defaultForNonRetryableEx() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, DELIBERATE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    void defaultForRetryableEx() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHostOnUnknownHostException() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    void defaultForNoAvailableHostOnServiceDiscovererError() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(DELIBERATE_EXCEPTION);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    void ignoreSdErrorsForNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(Builder::ignoreServiceDiscovererErrors);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected subscribe for SD errors.", sdStatus.isSubscribed(), is(false));
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHostWhenServiceDiscovererTerminated() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onComplete();
        verifyRetryResultCompleted();
    }

    @Test
    void maxRetriesAreHonored() {
        AutoRetryStrategy strategy = newStrategy(builder -> builder.maxRetries(1));
        Completable retry = strategy.apply(2, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    private void verifyRetryResultCompleted() {
        retrySubscriber.awaitOnComplete();
    }

    private void verifyRetryResultError(Throwable expected) {
        assertThat(retrySubscriber.awaitOnError(), is(sameInstance(expected)));
    }

    private AutoRetryStrategy newStrategy(UnaryOperator<Builder> updater) {
        AutoRetryStrategyProvider provider = updater.apply(new Builder()).build();
        return provider.newStrategy(lbEvents, sdStatus);
    }
}
