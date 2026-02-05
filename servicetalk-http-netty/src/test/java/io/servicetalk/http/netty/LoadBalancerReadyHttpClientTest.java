/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponses;
import io.servicetalk.http.api.TestStreamingHttpClient;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class LoadBalancerReadyHttpClientTest {
    private static final long TIMEOUT_DURATION_SECONDS = 5;
    private static final ContextMap.Key<Integer> REQUEST_COUNT_KEY = newKey("request-count", Integer.class);
    private static final UnknownHostException UNKNOWN_HOST_EXCEPTION =
            new UnknownHostException("deliberate exception");
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);


    private final TestExecutor testExecutor = new TestExecutor();
    private final TestPublisher<Object> loadBalancerPublisher = new TestPublisher<>();
    private final TestCompletable sdStatusCompletable = new TestCompletable();

    @Mock
    private HttpExecutionContext mockExecutionCtx;

    @Mock
    private ReservedStreamingHttpConnection mockReservedConnection;

    private final StreamingHttpClientFilterFactory testHandler = client -> new StreamingHttpClientFilter(client) {
        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final StreamingHttpRequest request) {
            return defer(new DeferredSuccessSupplier<>(newOkResponse(), requestCounter(request.context())));
        }

        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
            return defer(new DeferredSuccessSupplier<>(mockReservedConnection, requestCounter(metaData.context())));
        }
    };

    private static IntSupplier requestCounter(ContextMap map) {
        return () -> map.put(REQUEST_COUNT_KEY, map.computeIfAbsent(REQUEST_COUNT_KEY, key -> 0) + 1);
    }

    @BeforeEach
    void setup() {
        mockExecutionCtx = mock(HttpExecutionContext.class);
        mockReservedConnection = mock(ReservedStreamingHttpConnection.class);
        doAnswer((Answer<StreamingHttpRequest>) invocation ->
                reqRespFactory.newRequest(invocation.getArgument(0), invocation.getArgument(1)))
            .when(mockReservedConnection).newRequest(any(), any());
        doAnswer((Answer<StreamingHttpResponseFactory>) invocation -> reqRespFactory)
                .when(mockReservedConnection).httpResponseFactory();
        doAnswer((Answer<HttpExecutionStrategy>) invocation -> offloadAll())
                .when(mockExecutionCtx).executionStrategy();
        doAnswer((Answer<Executor>) invocation -> testExecutor)
                .when(mockExecutionCtx).executor();
    }

    @Test
    void requestsAreDelayed() throws InterruptedException {
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.request(filter.get("/noop")));
    }

    @Test
    void reserveIsDelayed() throws InterruptedException {
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    @Test
    void initializedFailedAlsoFailsRequest() throws InterruptedException {
        verifyOnInitializedFailedFailsAction(filter -> filter.request(filter.get("/noop")));
    }

    @Test
    void initializedFailedAlsoFailsReserve() throws InterruptedException {
        verifyOnInitializedFailedFailsAction(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    @Test
    void lbCompleteFailedAlsoFailsReserve() throws InterruptedException {
        verifyLbCompleteFailedFailsAction(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    @Test
    void serviceDiscovererAlsoFailsRequest() throws InterruptedException {
        verifyOnServiceDiscovererErrorFailsAction(filter -> filter.request(filter.get("/noop")));
    }

    @Test
    void serviceDiscovererAlsoFailsReserve() throws InterruptedException {
        verifyOnServiceDiscovererErrorFailsAction(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    @Test
    void serviceDiscoveryErrorTimeout() throws InterruptedException {
        Consumer<Throwable> errorConsumer = ex -> testExecutor.advanceTimeBy(
                TIMEOUT_DURATION_SECONDS, TimeUnit.SECONDS);
        assertThat(verifyFailsAction0(true, filter ->
                filter.reserveConnection(filter.get("/noop")), errorConsumer, DELIBERATE_EXCEPTION),
                instanceOf(TimeoutException.class));
    }

    @Test
    void serviceDiscoveryErrorTimeoutWithError() throws InterruptedException {
        Consumer<Throwable> errorConsumer = ex -> {
            sdStatusCompletable.onError(ex);
            testExecutor.advanceTimeBy(
                    TIMEOUT_DURATION_SECONDS, TimeUnit.SECONDS);
        };
        assertThat(verifyFailsAction0(true, filter ->
                        filter.reserveConnection(filter.get("/noop")), errorConsumer, DELIBERATE_EXCEPTION),
                is(DELIBERATE_EXCEPTION));
    }

    private void verifyOnInitializedFailedFailsAction(
            Function<StreamingHttpClient, Single<?>> action) throws InterruptedException {
        verifyFailsAction(action, loadBalancerPublisher::onError, DELIBERATE_EXCEPTION);
    }

    private void verifyOnServiceDiscovererErrorFailsAction(
            Function<StreamingHttpClient, Single<?>> action) throws InterruptedException {
        verifyFailsAction(action, sdStatusCompletable::onError, UNKNOWN_HOST_EXCEPTION);
    }

    private void verifyLbCompleteFailedFailsAction(
            Function<StreamingHttpClient, Single<?>> action) throws InterruptedException {
        assertThat(verifyFailsAction0(false, action, ignored -> loadBalancerPublisher.onComplete(),
                        DELIBERATE_EXCEPTION), instanceOf(IllegalStateException.class));
    }

    private void verifyFailsAction(Function<StreamingHttpClient, Single<?>> action, Consumer<Throwable> errorConsumer,
                                   Throwable error) throws InterruptedException {
        assertThat(verifyFailsAction0(false, action, errorConsumer, error), is(error));
    }

    private Throwable verifyFailsAction0(boolean sdStatusTimeout, Function<StreamingHttpClient, Single<?>> action,
                                         Consumer<Throwable> errorConsumer, Throwable error)
            throws InterruptedException {
        StreamingHttpClient client = TestStreamingHttpClient.from(reqRespFactory, mockExecutionCtx,
                appendClientFilterFactory(newAutomaticRetryFilterFactory(sdStatusTimeout, loadBalancerPublisher,
                                sdStatusCompletable), testHandler));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();

        action.apply(client)
                .whenOnError(causeRef::set)
                .afterFinally(latch::countDown)
                .toCompletable()
                .subscribe();

        // We don't expect the request to complete until onInitialized completes.
        assertThat(latch.await(100, MILLISECONDS), is(false));

        // When a failure occurs that should also fail the action!
        errorConsumer.accept(error);
        latch.await();
        return causeRef.get();
    }

    private void verifyActionIsDelayedUntilAfterInitialized(Function<StreamingHttpClient, Single<?>> action)
            throws InterruptedException {
        StreamingHttpClient client = TestStreamingHttpClient.from(reqRespFactory, mockExecutionCtx,
                appendClientFilterFactory(newAutomaticRetryFilterFactory(loadBalancerPublisher, sdStatusCompletable),
                        testHandler));

        CountDownLatch latch = new CountDownLatch(1);
        action.apply(client).subscribe(resp -> latch.countDown());

        // We don't expect the request to complete until onInitialized completes.
        assertThat(latch.await(100, MILLISECONDS), is(false));

        loadBalancerPublisher.onNext(LOAD_BALANCER_READY_EVENT);
        latch.await();
    }

    private static StreamingHttpClientFilterFactory newAutomaticRetryFilterFactory(
            TestPublisher<Object> loadBalancerPublisher, TestCompletable sdStatusCompletable) {
        return newAutomaticRetryFilterFactory(false, loadBalancerPublisher, sdStatusCompletable);
    }

    private static StreamingHttpClientFilterFactory newAutomaticRetryFilterFactory(boolean timeoutLbReady,
            TestPublisher<Object> loadBalancerPublisher, TestCompletable sdStatusCompletable) {
                RetryingHttpRequesterFilter.Builder builder = new RetryingHttpRequesterFilter.Builder();
                if (timeoutLbReady) {
                    builder.waitForLoadBalancer(Duration.ofSeconds(TIMEOUT_DURATION_SECONDS));
                }
        final RetryingHttpRequesterFilter filter = builder.maxTotalRetries(1)
                .build();
        return client -> {
            final ContextAwareRetryingHttpClientFilter f = (ContextAwareRetryingHttpClientFilter) filter.create(client);
            f.inject(loadBalancerPublisher, sdStatusCompletable);
            return f;
        };
    }

    private static final class DeferredSuccessSupplier<T> implements Supplier<Single<T>> {
        private final T value;
        private final IntSupplier countSupplier;

        DeferredSuccessSupplier(T value, final IntSupplier countSupplier) {
            this.value = value;
            this.countSupplier = countSupplier;
        }

        @Override
        public Single<T> get() {
            return countSupplier.getAsInt() == 0 ? failed(new NoAvailableHostException("deliberate testing")) :
                    succeeded(value);
        }
    }

    private static StreamingHttpResponse newOkResponse() {
        return StreamingHttpResponses.newResponse(OK, HTTP_1_1, INSTANCE.newHeaders(), DEFAULT_ALLOCATOR, INSTANCE);
    }
}
