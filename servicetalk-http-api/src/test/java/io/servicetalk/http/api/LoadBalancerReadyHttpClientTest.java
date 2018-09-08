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

import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

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

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class LoadBalancerReadyHttpClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Object> loadBalancerPublisher = new PublisherRule<>();

    @Mock
    private StreamingHttpClient mockClient;
    @Mock
    private ReservedStreamingHttpConnection mockReservedConnection;
    @Mock
    private UpgradableStreamingHttpResponse mockUpgradeResponse;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void requestsAreDelayed() throws InterruptedException {
        when(mockClient.request(any())).thenReturn(defer(new DeferredSuccessSupplier<>(newResponse(OK))));
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.request(newDummyRequest()));
    }

    @Test
    public void reserveIsDelayed() throws InterruptedException {
        doReturn(defer(new DeferredSuccessSupplier<>(mockReservedConnection)))
                .when(mockClient).reserveConnection(any());
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.reserveConnection(newDummyRequest()));
    }

    @Test
    public void upgradeIsDelayed() throws InterruptedException {
        doReturn(defer(new DeferredSuccessSupplier<>(mockUpgradeResponse))).when(mockClient).upgradeConnection(any());
        verifyActionIsDelayedUntilAfterInitialized(filter -> filter.upgradeConnection(newDummyRequest()));
    }

    @Test
    public void initializedFailedAlsoFailsRequest() throws InterruptedException {
        when(mockClient.request(any())).thenReturn(defer(new DeferredSuccessSupplier<>(newResponse(OK))));
        verifyOnInitializedFailedFailsAction(filter -> filter.request(newDummyRequest()));
    }

    @Test
    public void initializedFailedAlsoFailsReserve() throws InterruptedException {
        doReturn(defer(new DeferredSuccessSupplier<>(mockReservedConnection)))
                .when(mockClient).reserveConnection(any());
        verifyOnInitializedFailedFailsAction(filter -> filter.reserveConnection(newDummyRequest()));
    }

    @Test
    public void initializedFailedAlsoFailsUpgrade() throws InterruptedException {
        doReturn(defer(new DeferredSuccessSupplier<>(mockUpgradeResponse))).when(mockClient).upgradeConnection(any());
        verifyOnInitializedFailedFailsAction(filter -> filter.upgradeConnection(newDummyRequest()));
    }

    private void verifyOnInitializedFailedFailsAction(Function<StreamingHttpClient,
            Single<?>> action) throws InterruptedException {
        TestPublisher<Object> loadBalancerPublisher = new TestPublisher<>();
        LoadBalancerReadyStreamingHttpClient filter =
                new LoadBalancerReadyStreamingHttpClient(1, loadBalancerPublisher, mockClient);
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
                new LoadBalancerReadyStreamingHttpClient(1, loadBalancerPublisher.getPublisher(), mockClient);
        CountDownLatch latch = new CountDownLatch(1);
        action.apply(filter).subscribe(resp -> latch.countDown());

        // We don't expect the request to complete until onInitialized completes.
        assertThat(latch.await(100, MILLISECONDS), is(false));

        loadBalancerPublisher.sendItems(LOAD_BALANCER_READY_EVENT);
        latch.await();
    }

    private static StreamingHttpRequest<HttpPayloadChunk> newDummyRequest() {
        return newRequest(GET, "/noop");
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
}
