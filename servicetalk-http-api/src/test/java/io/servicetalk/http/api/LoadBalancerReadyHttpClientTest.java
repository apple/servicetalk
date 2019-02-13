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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.MockitoAnnotations.initMocks;

public class LoadBalancerReadyHttpClientTest {
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            allocator, INSTANCE);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Object> loadBalancerPublisher = new PublisherRule<>();
    @Mock
    private ExecutionContext mockExecutionCtx;

    private StreamingHttpClient client = new TestStreamingHttpClient(reqRespFactory, mockExecutionCtx) {
        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return defer(new DeferredSuccessSupplier<>(newOkResponse()));
        }

        @Override
        public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                                   final HttpRequestMetaData metaData) {
            return defer(new DeferredSuccessSupplier<>(mockReservedConnection));
        }
    };

    private ReservedStreamingHttpConnection mockReservedConnection;

    @Before
    public void setup() {
        mockReservedConnection = mock(ReservedStreamingHttpConnection.class,
                withSettings().useConstructor(reqRespFactory, defaultStrategy()));
        initMocks(this);
    }

    @Test
    public void requestsAreDelayed() throws InterruptedException {
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.request(filter.get("/noop")));
    }

    @Test
    public void reserveIsDelayed() throws InterruptedException {
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    @Test
    public void initializedFailedAlsoFailsRequest() throws InterruptedException {
        verifyOnInitializedFailedFailsAction(filter -> filter.request(filter.get("/noop")));
    }

    @Test
    public void initializedFailedAlsoFailsReserve() throws InterruptedException {
        verifyOnInitializedFailedFailsAction(filter -> filter.reserveConnection(filter.get("/noop")));
    }

    private void verifyOnInitializedFailedFailsAction(Function<StreamingHttpClient,
            Single<?>> action) throws InterruptedException {
        TestPublisher<Object> loadBalancerPublisher = new TestPublisher<>();
        LoadBalancerReadyStreamingHttpClient filter =
                new LoadBalancerReadyStreamingHttpClient(1, loadBalancerPublisher, client);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        action.apply(filter).subscribe(new Subscriber<Object>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                latch.countDown();
            }

            @Override
            public void onSuccess(@Nullable final Object result) {
                latch.countDown();
            }

            @Override
            public void onError(final Throwable t) {
                causeRef.set(t);
                latch.countDown();
            }
        });

        // We don't expect the request to complete until onInitialized completes.
        assertThat(latch.await(100, MILLISECONDS), is(false));

        // When a failure occurs that should also fail the action!
        loadBalancerPublisher.fail(DELIBERATE_EXCEPTION);
        latch.await();
        assertThat(causeRef.get(), is(DELIBERATE_EXCEPTION));
    }

    private void verifyActionIsDelayedUntilAfterInitialized(Function<StreamingHttpClient, Single<?>> action)
            throws InterruptedException {
        LoadBalancerReadyStreamingHttpClient filter =
                new LoadBalancerReadyStreamingHttpClient(1, loadBalancerPublisher.getPublisher(), client);
        CountDownLatch latch = new CountDownLatch(1);
        action.apply(filter).subscribe(resp -> latch.countDown());

        // We don't expect the request to complete until onInitialized completes.
        assertThat(latch.await(100, MILLISECONDS), is(false));

        loadBalancerPublisher.sendItems(LOAD_BALANCER_READY_EVENT);
        latch.await();
    }

    private static final class DeferredSuccessSupplier<T> implements Supplier<Single<T>> {
        private final T value;
        private int count;

        DeferredSuccessSupplier(T value) {
            this.value = value;
        }

        @Override
        public Single<T> get() {
            return ++count == 1 ? error(new NoAvailableHostException("deliberate testing")) : success(value);
        }
    }

    private static StreamingHttpResponse newOkResponse() {
        return StreamingHttpResponses.newResponse(OK, HTTP_1_1, INSTANCE.newHeaders(), INSTANCE.newTrailers(),
                DEFAULT_ALLOCATOR);
    }
}
