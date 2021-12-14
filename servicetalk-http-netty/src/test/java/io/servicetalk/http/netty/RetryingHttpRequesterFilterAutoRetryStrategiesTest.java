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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.RetryableConnectException;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.net.UnknownHostException;
import javax.annotation.Nonnull;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.NO_RETRIES;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryingHttpRequesterFilterAutoRetryStrategiesTest {
    private static final HttpRequestMetaData REQUEST_META_DATA =
            newRequest(GET, "/", HTTP_1_1, INSTANCE.newHeaders(), DEFAULT_ALLOCATOR, INSTANCE);
    private static final RetryableConnectException RETRYABLE_EXCEPTION =
            new RetryableConnectException("deliberate exception");
    private static final NoAvailableHostException NO_AVAILABLE_HOST =
            new NoAvailableHostException("deliberate exception");
    private static final UnknownHostException UNKNOWN_HOST_EXCEPTION =
            new UnknownHostException("deliberate exception");

    private final TestPublisher<Object> lbEvents;
    private final TestCompletable sdStatus;
    private final TestCompletableSubscriber retrySubscriber;

    RetryingHttpRequesterFilterAutoRetryStrategiesTest() {
        lbEvents = new TestPublisher<>();
        sdStatus = new TestCompletable();
        retrySubscriber = new TestCompletableSubscriber();
    }

    @Test
    void disableWaitForLb() {
        final ContextAwareRetryingHttpClientFilter filter =
                newFilter(new RetryingHttpRequesterFilter.Builder().waitForLoadBalancer(false));

        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    void disableRetryAllRetryableExWithRetryable() {
        final ContextAwareRetryingHttpClientFilter filter =
                newFilter(new RetryingHttpRequesterFilter.Builder().retryRetryableExceptions((__, ___) -> NO_RETRIES));

        Completable retry = applyRetry(filter, 1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    void disableRetryAllRetryableExWithNoAvailableHost() {
        final ContextAwareRetryingHttpClientFilter filter =
                newFilter(new RetryingHttpRequesterFilter.Builder().retryRetryableExceptions((__, ___) -> NO_RETRIES));

        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void disableRetryAllRetryableExWithNoAvailableHostAndUnknownHostException() {
        final ContextAwareRetryingHttpClientFilter filter =
                newFilter(new RetryingHttpRequesterFilter.Builder().retryRetryableExceptions((__, ___) -> NO_RETRIES));

        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    void disableAll() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(disableAutoRetries());
        Completable retry = applyRetry(filter, 1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    @Test
    void defaultForNonRetryableEx() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, DELIBERATE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    void defaultForRetryableEx() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHost() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHostOnUnknownHostException() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(UNKNOWN_HOST_EXCEPTION);
        verifyRetryResultError(UNKNOWN_HOST_EXCEPTION);
    }

    @Test
    void defaultForNoAvailableHostOnServiceDiscovererError() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onError(DELIBERATE_EXCEPTION);
        verifyRetryResultError(DELIBERATE_EXCEPTION);
    }

    @Test
    void ignoreSdErrorsForNoAvailableHost() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder()
                .ignoreServiceDiscovererErrors(true));
        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat("Unexpected subscribe for SD errors.", sdStatus.isSubscribed(), is(false));
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        lbEvents.onNext(LOAD_BALANCER_READY_EVENT);
        verifyRetryResultCompleted();
    }

    @Test
    void defaultForNoAvailableHostWhenServiceDiscovererTerminated() {
        final ContextAwareRetryingHttpClientFilter filter = newFilter(new RetryingHttpRequesterFilter.Builder());
        Completable retry = applyRetry(filter, 1, NO_AVAILABLE_HOST);
        toSource(retry).subscribe(retrySubscriber);
        assertThat(retrySubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        sdStatus.onComplete();
        verifyRetryResultCompleted();
    }

    @Test
    void maxRetriesAreHonored() {
        final ContextAwareRetryingHttpClientFilter filter =
                newFilter(new RetryingHttpRequesterFilter.Builder().maxTotalRetries(1));
        Completable retry = applyRetry(filter, 2, RETRYABLE_EXCEPTION);
        toSource(retry).subscribe(retrySubscriber);
        verifyRetryResultError(RETRYABLE_EXCEPTION);
    }

    private void verifyRetryResultCompleted() {
        retrySubscriber.awaitOnComplete();
    }

    private void verifyRetryResultError(Throwable expected) {
        assertThat(retrySubscriber.awaitOnError(), is(sameInstance(expected)));
    }

    private ContextAwareRetryingHttpClientFilter newFilter(final RetryingHttpRequesterFilter.Builder builder) {
        return newFilter(builder.build());
    }

    private ContextAwareRetryingHttpClientFilter newFilter(final RetryingHttpRequesterFilter filter) {
        final FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        final HttpExecutionContext executionContext = mock(HttpExecutionContext.class);
        when(executionContext.executor()).then((Answer<Executor>) invocation -> immediate());
        when(client.executionContext()).then(__ -> executionContext);
        final ContextAwareRetryingHttpClientFilter f =
                (ContextAwareRetryingHttpClientFilter) filter.create(client);
        f.inject(lbEvents, sdStatus);
        return f;
    }

    @Nonnull
    private Completable applyRetry(final ContextAwareRetryingHttpClientFilter filter,
                                   final int count, final Throwable t) {
        return filter.retryStrategy(immediate(), REQUEST_META_DATA).apply(count, t);
    }
}
