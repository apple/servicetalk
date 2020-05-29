/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestCompletableSubscriber;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Test;

import java.net.UnknownHostException;
import java.util.function.UnaryOperator;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class DefaultAutoRetryStrategyProviderTest {
    private static final RetryableConnectException RETRYABLE_EXCEPTION =
            new RetryableConnectException("deliberate exception");
    private static final NoAvailableHostException NO_AVAILABLE_HOST =
            new NoAvailableHostException("deliberate exception");
    private static final UnknownHostException UNKNOWN_HOST_EXCEPTION =
            new UnknownHostException("deliberate exception");

    private final TestPublisher<Object> lbEvents;
    private final TestPublisher<Throwable> sdErrors;
    private final TestCompletableSubscriber retrySubscriber;

    public DefaultAutoRetryStrategyProviderTest() {
        lbEvents = new TestPublisher<>();
        sdErrors = new TestPublisher<>();
        retrySubscriber = new TestCompletableSubscriber();
    }

    @Test
    public void disableWaitForLb() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableWaitForLoadBalancer);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    public void disableRetryAllRetryableExWithRetryable() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    public void disableRetryAllRetryableExWithNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected terminal.", retrySubscriber.takeTerminal(), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    public void disableRetryAllRetryableExWithNoAvailableHostAndUnknownHostException() {
        AutoRetryStrategy strategy = newStrategy(Builder::disableRetryAllRetryableExceptions);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected terminal.", retrySubscriber.takeTerminal(), is(nullValue()));
        sdErrors.onNext(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    public void disableAll() {
        AutoRetryStrategy strategy = newStrategy(builder ->
                builder.disableWaitForLoadBalancer()
                        .disableRetryAllRetryableExceptions());
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    public void defaultForNonRetryableEx() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, DELIBERATE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void defaultForRetryableEx() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    public void defaultForNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected terminal.", retrySubscriber.takeTerminal(), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    public void defaultForNoAvailableHostOnUnknownHostException() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        sdErrors.onNext(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    public void defaultForNoAvailableHostOnServiceDiscovererError() {
        AutoRetryStrategy strategy = newStrategy(identity());
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        sdErrors.onError(DELIBERATE_EXCEPTION);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void ignoreSdErrorsForNoAvailableHost() {
        AutoRetryStrategy strategy = newStrategy(Builder::ignoreServiceDiscovererErrors);
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected subscribe for SD errors.", sdErrors.isSubscribed(), is(false));
        assertThat("Unexpected terminal.", retrySubscriber.takeTerminal(), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    public void defaultForNoAvailableHostWhenServiceDiscovererTerminated() {
        AutoRetryStrategy strategy = newStrategy(identity());
        sdErrors.onComplete();
        Completable retry = strategy.apply(1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        TerminalNotification terminal = retrySubscriber.takeTerminal();
        assertThat("Unexpected terminal.", terminal, is(notNullValue()));
        assertThat("Unexpected terminal.", terminal.cause(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void maxRetriesAreHonored() {
        AutoRetryStrategy strategy = newStrategy(builder -> builder.maxRetries(1));
        Completable retry = strategy.apply(2, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    private void verifyRetryResultCompleted() {
        TerminalNotification terminal = retrySubscriber.takeTerminal();
        assertThat("Unexpected terminal.", terminal, is(notNullValue()));
        assertThat("Unexpected terminal.", terminal.cause(), is(nullValue()));
    }

    private void verifyRetryResultError(Throwable expected) {
        TerminalNotification terminal = retrySubscriber.takeTerminal();
        assertThat("Unexpected terminal.", terminal, is(notNullValue()));
        assertThat("Unexpected terminal.", terminal.cause(), is(sameInstance(expected)));
    }

    private AutoRetryStrategy newStrategy(UnaryOperator<Builder> updater) {
        AutoRetryStrategyProvider provider = updater.apply(new Builder()).build();
        return provider.newStrategy(lbEvents, sdErrors);
    }
}
