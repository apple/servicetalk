/*
 * Copyright © 2021-2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.RequestRejectedException;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.DefaultHttpLoadBalancerFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.netty.HttpClients.DiscoveryStrategy.BACKGROUND;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofConstantBackoffFullJitter;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofImmediate;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofImmediateBounded;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.Builder;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.DEFAULT_MAX_TOTAL_RETRIES;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofNanos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RetryingHttpRequesterFilterTest {

    private static final String RETRYABLE_HEADER = "RETRYABLE";
    private static final String RESPONSE_BODY = "ok";

    private final ServerContext svcCtx;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> normalClientBuilder;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> failingConnClientBuilder;
    private final AtomicInteger lbSelectInvoked;

    @Nullable
    private BlockingHttpClient normalClient;

    @Nullable
    private BlockingHttpClient failingClient;

    RetryingHttpRequesterFilterTest() throws Exception {
        svcCtx = forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .addHeader(RETRYABLE_HEADER, "yes")
                        .payloadBody(ctx.executionContext().bufferAllocator().fromAscii(RESPONSE_BODY)));
        failingConnClientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .loadBalancerFactory(new DefaultHttpLoadBalancerFactory<>(new InspectingLoadBalancerFactory<>()))
                .appendConnectionFactoryFilter(ClosingConnectionFactory::new);
        normalClientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .loadBalancerFactory(new DefaultHttpLoadBalancerFactory<>(new InspectingLoadBalancerFactory<>()));
        lbSelectInvoked = new AtomicInteger();
    }

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (normalClient != null) {
            closeable.append(normalClient.asClient());
        }
        if (failingClient != null) {
            closeable.append(failingClient.asClient());
        }
        closeable.append(svcCtx);
        closeable.close();
    }

    @Test
    void requestPayloadBodyIsDuplicated() throws Exception {
        byte[] hello = "hello".getBytes(UTF_8);
        byte[] world = "world".getBytes(UTF_8);
        try (ServerContext serverCtx = forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait(new BlockingHttpService() {
                    private final AtomicInteger reqCount = new AtomicInteger();
                    @Override
                    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                               final HttpResponseFactory responseFactory) {
                        int count = reqCount.incrementAndGet();
                        final HttpResponse response;
                        if (count > 1) {
                            Buffer responseBuf = ctx.executionContext().bufferAllocator().newBuffer();
                            responseBuf.writeBytes(request.payloadBody());
                            responseBuf.writeBytes(world);
                            response = responseFactory.ok().payloadBody(responseBuf);
                        } else {
                            response = responseFactory.serviceUnavailable();
                        }
                        return response;
                    }
                });
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(serverCtx))
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname())
                             .build())
                     .appendClientFilter(new Builder()
                             .waitForLoadBalancer(true)
                             .responseMapper(resp -> resp.status().statusClass() == SERVER_ERROR_5XX ?
                                     new HttpResponseException("failed", resp) : null)
                             .retryResponses((requestMetaData, throwable) -> ofImmediate(Integer.MAX_VALUE - 1))
                             .maxTotalRetries(Integer.MAX_VALUE)
                             .build())
                     .buildBlocking()) {
            HttpResponse response = client.request(client.post("/").payloadBody(
                    client.executionContext().bufferAllocator().wrap(hello)));
            assertThat(response.status(), equalTo(HttpResponseStatus.OK));
            assertThat(response.payloadBody().toString(UTF_8), equalTo(
                    new String(hello, UTF_8) + new String(world, UTF_8)));
        }
    }

    @Test
    void disableAutoRetry() {
        failingClient = failingConnClientBuilder
                .appendClientFilter(disableAutoRetries())
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> failingClient.request(failingClient.get("/")));
        assertThat(e, instanceOf(RetryableException.class));
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(lessThanOrEqualTo(2)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxTotalRetries={0}")
    @ValueSource(ints = {1, 2, 3})
    void maxTotalRetriesCapsDefaultOfImmediateBoundedBackoffPolicy(int maxTotalRetries) {
        assertThat("maxTotalRetries higher than default bounds",
                maxTotalRetries, is(lessThan(DEFAULT_MAX_TOTAL_RETRIES)));
        AtomicInteger onRequestRetryCounter = new AtomicInteger();
        failingClient = failingConnClientBuilder
                .appendClientFilter(new Builder()
                        .maxTotalRetries(maxTotalRetries)
                        .onRequestRetry((count, req, t) ->
                                assertThat(onRequestRetryCounter.incrementAndGet(), is(count)))
                        .build())
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> failingClient.request(failingClient.get("/")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(maxTotalRetries + 1));
        assertThat("Unexpected calls to onRequestRetry.", onRequestRetryCounter.get(), is(maxTotalRetries));
    }

    @Test
    void requestRetryingPredicate() {
        failingClient = failingConnClientBuilder
                .appendClientFilter(new Builder()
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        .retryOther((requestMetaData, throwable) ->
                                "/retry".equals(requestMetaData.requestTarget()) ? ofImmediateBounded() :
                                        ofNoRetries()).build())
                .buildBlocking();
        assertRequestRetryingPred(failingClient);
    }

    @Test
    void requestRetryingPredicateWithConditionalAppend() {
        failingClient = failingConnClientBuilder
                .appendClientFilter((__) -> true, new Builder()
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        .retryOther((requestMetaData, throwable) ->
                                "/retry".equals(requestMetaData.requestTarget()) ? ofImmediateBounded() :
                                        ofNoRetries()).build())
                .buildBlocking();
        assertRequestRetryingPred(failingClient);
    }

    private void assertRequestRetryingPred(final BlockingHttpClient client) {
        Exception e = assertThrows(Exception.class, () -> client.request(client.get("/")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        // Account for LB readiness
        assertThat("Unexpected calls to select.", (double) lbSelectInvoked.get(), closeTo(1.0, 1.0));

        e = assertThrows(Exception.class, () -> client.request(client.get("/retry")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        // 1 Run + 3 Retries + 1 residual count from previous request + account for LB readiness
        assertThat("Unexpected calls to select.", (double) lbSelectInvoked.get(), closeTo(5.0, 1.0));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: returnOriginalResponses={0}")
    @ValueSource(booleans = {true, false})
    void testResponseMapper(final boolean returnOriginalResponses) throws Exception {
        AtomicInteger newConnectionCreated = new AtomicInteger();
        AtomicInteger responseDrained = new AtomicInteger();
        AtomicInteger onRequestRetryCounter = new AtomicInteger();
        final int maxTotalRetries = 4;
        final String retryMessage = "Retryable header";
        normalClient = normalClientBuilder
                .appendClientFilter(new Builder()
                        .maxTotalRetries(maxTotalRetries)
                        .responseMapper(metaData -> metaData.headers().contains(RETRYABLE_HEADER) ?
                                    new HttpResponseException(retryMessage, metaData) : null)
                        // Disable request retrying
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        // Retry only responses marked so
                        .retryResponses((requestMetaData, throwable) -> ofImmediate(maxTotalRetries - 1),
                                returnOriginalResponses)
                        .onRequestRetry((count, req, t) ->
                                assertThat(onRequestRetryCounter.incrementAndGet(), is(count)))
                        .build())
                .appendConnectionFilter(c -> {
                    newConnectionCreated.incrementAndGet();
                    return new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                            return delegate().request(request)
                                    .map(response -> response.transformPayloadBody(payload -> payload
                                            .whenFinally(responseDrained::incrementAndGet)));
                        }
                    };
                })
                .buildBlocking();
        if (returnOriginalResponses) {
            HttpResponse response = normalClient.request(normalClient.get("/"));
            assertThat(response.status(), is(HttpResponseStatus.OK));
            assertThat(response.payloadBody().toString(StandardCharsets.US_ASCII), equalTo(RESPONSE_BODY));
        } else {
            HttpResponseException e = assertThrows(HttpResponseException.class,
                    () -> normalClient.request(normalClient.get("/")));
            assertThat("Unexpected exception.", e, instanceOf(HttpResponseException.class));
        }
        // The load balancer is allowed to be not ready one time, which is counted against total retry attempts but not
        // against actual requests being issued.
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), allOf(greaterThanOrEqualTo(maxTotalRetries),
                lessThanOrEqualTo(maxTotalRetries + 1)));
        assertThat("Unexpected calls to onRequestRetry.", onRequestRetryCounter.get(),
                allOf(greaterThanOrEqualTo(maxTotalRetries - 1), lessThanOrEqualTo(maxTotalRetries)));
        assertThat("Response payload body was not drained on every mapping", responseDrained.get(),
                is(maxTotalRetries));
        assertThat("Unexpected number of connections was created", newConnectionCreated.get(), is(1));
    }

    private enum ExceptionSource {
        RESPONSE_MAPPER,
        RETRY_RESPONSES
    }

    private static Stream<Arguments> lambdaExceptions() {
        return Stream.of(true, false).flatMap(returnOriginalResponses ->
                Stream.of(ExceptionSource.values())
                        .map(lambda -> Arguments.of(returnOriginalResponses, lambda)));
    }

    @Test
    void contextIsSharedAcrossBoundaries() throws Exception {
        String helloWorld = "hello world";
        final ContextMap current = AsyncContext.context();
        try (ServerContext serverCtx = forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait(new BlockingHttpService() {
                    private final AtomicInteger reqCount = new AtomicInteger();
                    @Override
                    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                               final HttpResponseFactory responseFactory) {
                        int count = reqCount.incrementAndGet();
                        final HttpResponse response;
                        if (count > 1) {
                            Buffer responseBuf = ctx.executionContext().bufferAllocator().fromUtf8(helloWorld);
                            response = responseFactory.ok().payloadBody(responseBuf);
                        } else {
                            response = responseFactory.serviceUnavailable();
                        }
                        return response;
                    }
                });
             StreamingHttpClient client = forSingleAddress(serverHostAndPort(serverCtx))
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname())
                             .build())
                     .appendClientFilter(new Builder()
                             .waitForLoadBalancer(true)
                             .responseMapper(resp -> resp.status().statusClass() == SERVER_ERROR_5XX ?
                                     new HttpResponseException("failed", resp) : null)
                             .retryResponses((requestMetaData, throwable) -> ofImmediate(Integer.MAX_VALUE - 1))
                             .maxTotalRetries(Integer.MAX_VALUE)
                             .build())
                     .appendClientFilter(new StreamingHttpClientFilterFactory() {
                         @Override
                         public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
                             return new StreamingHttpClientFilter(client) {
                                 @Override
                                 protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                                 StreamingHttpRequest request) {
                                     if (AsyncContext.context() != current) {
                                         return Single.failed(
                                                 new AssertionError("Unexpected context: " + AsyncContext.context()));
                                     }
                                     return delegate.request(request);
                                 }
                             };
                         }
                     })
                     .buildStreaming()) {
            Single<String> response = client.request(client.get("/"))
                    .flatMap(resp ->
                resp.payloadBody().collect(() -> "", (acc, buffer) -> acc + buffer.toString(UTF_8)));
            String result = response.shareContextOnSubscribe().toFuture().get();
            assertThat(result, equalTo(helloWorld));
        }
    }

    @Test
    void sdAvailableStatusCanResultInTimeout() {
        failingClient = forSingleAddress(new ForeverServiceDiscoverer(), HostAndPort.of("foo", 80), BACKGROUND)
                .appendClientFilter(
                        new Builder()
                                .waitForLoadBalancerTimeout(Duration.ofMillis(100))
                        .build())
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> failingClient.request(failingClient.get("/")));
        assertThat(e, instanceOf(TimeoutException.class));
        // Note that this message is very specific and subject to change
        assertThat(e.getMessage(), equalTo("Load balancer unavailable"));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(equalTo(0)));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: returnOriginalResponses={0}, thrower={1}")
    @MethodSource("lambdaExceptions")
    void lambdaExceptions(final boolean returnOriginalResponses, final ExceptionSource thrower) {
        final AtomicInteger newConnectionCreated = new AtomicInteger();
        final AtomicInteger requestsInitiated = new AtomicInteger();
        final AtomicInteger responseDrained = new AtomicInteger();
        final AtomicInteger onRequestRetryCounter = new AtomicInteger();
        final String retryMessage = "Retryable header";
        normalClient = normalClientBuilder
                .appendClientFilter(new Builder()
                        .maxTotalRetries(4)
                        .responseMapper(metaData -> {
                            if (thrower == ExceptionSource.RESPONSE_MAPPER) {
                                throw new RuntimeException("responseMapper");
                            }
                            return metaData.headers().contains(RETRYABLE_HEADER) ?
                                    new HttpResponseException(retryMessage, metaData) : null;
                        })
                        // Retry only responses marked so
                        .retryResponses((requestMetaData, throwable) -> {
                            if (thrower == ExceptionSource.RETRY_RESPONSES) {
                                throw new RuntimeException("retryResponses");
                            }
                            return ofImmediate(3);
                        }, returnOriginalResponses)
                        .onRequestRetry((count, req, t) ->
                            assertThat(onRequestRetryCounter.incrementAndGet(), is(count)))
                        .build())
                .appendConnectionFilter(c -> {
                    newConnectionCreated.incrementAndGet();
                    return new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                            return Single.defer(() -> {
                                requestsInitiated.incrementAndGet();
                                return delegate().request(request)
                                        .map(response -> response.transformPayloadBody(payload -> payload
                                                    .whenFinally(responseDrained::incrementAndGet)));
                            });
                        }
                    };
                })
                .buildBlocking();
        assertThrows(Exception.class, () -> normalClient.request(normalClient.get("/")));
        assertThat("Response payload body was not drained on every mapping", responseDrained.get(), is(1));
        assertThat("Multiple requests initiated", requestsInitiated.get(), is(1));
        assertThat("Unexpected number of connections was created", newConnectionCreated.get(), is(1));
    }

    @Test
    void singleInstanceFilter() {
        Assertions.assertThrows(IllegalStateException.class, () -> forResolvedAddress(localAddress(8888))
                .appendClientFilter(new Builder().build())
                .appendClientFilter(new Builder().build())
                .build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void requestPublisherTransformNoStackOverflow(boolean mayReplayRequestPayload) throws Exception {
        final int serverRequestsBeforeSucceed = 10_000;
        final RetryingHttpRequesterFilter retryFilter;
        final RetryingHttpRequesterFilter.Builder builder = new Builder()
                .waitForLoadBalancer(true)
                .maxTotalRetries(Integer.MAX_VALUE);
        if (mayReplayRequestPayload) {
            builder.responseMapper(resp -> resp.status().statusClass().equals(SERVER_ERROR_5XX) ?
                            new HttpResponseException("failed", resp) : null)
                    .retryResponses((req, throwable) -> ofImmediate(Integer.MAX_VALUE - 1));
        } else {
            builder.retryRetryableExceptions((req, throwable) ->
                            ofConstantBackoffFullJitter(ofNanos(1), Integer.MAX_VALUE - 1, Executors.global()));
        }
        retryFilter = builder.build();

        try (ServerContext serverCtx = forAddress(localAddress(0))
                .listenBlockingAndAwait(new BlockingHttpService() {
                    private final AtomicInteger reqCount = new AtomicInteger();
                    @Override
                    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                                               final HttpResponseFactory responseFactory) {
                        if (mayReplayRequestPayload) {
                            final int count = reqCount.incrementAndGet();
                            return count >= serverRequestsBeforeSucceed ?
                                    responseFactory.ok() : responseFactory.serviceUnavailable();
                        } else {
                            return responseFactory.ok();
                        }
                    }
                });
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(serverCtx))
                     .appendClientFilter(retryFilter)
                     .appendClientFilter(new StreamingHttpClientFilterFactory() {
                         @Override
                         public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                             return new StreamingHttpClientFilter(client) {
                                 @Override
                                 protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                                 final StreamingHttpRequest request) {
                                     return Single.defer(() -> delegate.request(request.transformMessageBody(pub ->
                                             // Just apply any operators, the goal is to verify we don't get stack
                                             // overflow because the operators shouldn't be chained for each retry
                                             // attempt.
                                             pub.map(x -> x)
                                                     .whenOnNext(x -> { })
                                                     .whenRequest(r -> { })
                                                     .whenCancel(() -> { })
                                                     .whenFinally(() -> { })
                                                     .whenOnSubscribe(s -> { })
                                                     .whenOnError(e -> { })
                                                     .whenOnComplete(() -> { })
                                                     .shareContextOnSubscribe())));
                                 }
                             };
                         }

                         @Override
                         public HttpExecutionStrategy requiredOffloads() {
                             return offloadNone();
                         }
                     })
                     .appendClientFilter(new StreamingHttpClientFilterFactory() {
                         @Override
                         public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                             return new StreamingHttpClientFilter(client) {
                                 private final AtomicInteger reqCount = new AtomicInteger();
                                 @Override
                                 protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                                 final StreamingHttpRequest request) {
                                     if (!mayReplayRequestPayload) {
                                         // Any RetryableException
                                         final int count = reqCount.incrementAndGet();
                                         if (count < serverRequestsBeforeSucceed) {
                                             return Single.failed(new RequestRejectedException("intentional fail"));
                                         }
                                     }
                                     return delegate.request(request);
                                 }
                             };
                         }

                         @Override
                         public HttpExecutionStrategy requiredOffloads() {
                             return offloadNone();
                         }
                     })
                     .buildBlocking()) {
            HttpResponse response = client.request(client.post("/").payloadBody(
                    client.executionContext().bufferAllocator().wrap("hello".getBytes(UTF_8))));
            assertThat(response.status(), equalTo(HttpResponseStatus.OK));
        }
    }

    private static final class ForeverServiceDiscoverer implements ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> {
        @Override
        public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(HostAndPort s) {
            return Publisher.never();
        }

        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return onClose();
        }
    }

    private final class InspectingLoadBalancerFactory<C extends LoadBalancedConnection>
            implements LoadBalancerFactory<InetSocketAddress, C> {

        private final LoadBalancerFactory<InetSocketAddress, C> rr =
                RoundRobinLoadBalancers.<InetSocketAddress, C>builder(getClass().getSimpleName()).build();

        @SuppressWarnings("deprecation")
        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                final String targetResource,
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return new InspectingLoadBalancer<>(rr.newLoadBalancer(targetResource, eventPublisher, connectionFactory));
        }

        @Override
        public LoadBalancer<C> newLoadBalancer(
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, C> connectionFactory,
                final String targetResource) {
            return new InspectingLoadBalancer<>(
                    rr.newLoadBalancer(eventPublisher, connectionFactory, targetResource));
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }

    private final class InspectingLoadBalancer<C extends LoadBalancedConnection> implements LoadBalancer<C> {
        private final LoadBalancer<C> delegate;

        private InspectingLoadBalancer(final LoadBalancer<C> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Single<C> selectConnection(final Predicate<C> selector, @Nullable ContextMap context) {
            return defer(() -> {
                lbSelectInvoked.incrementAndGet();
                return delegate.selectConnection(selector, context);
            });
        }

        @Override
        public Publisher<Object> eventStream() {
            return delegate.eventStream();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable onClosing() {
            return delegate.onClosing();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }
    }

    private static final class ClosingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {
        ClosingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
            super(original);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(inetSocketAddress, context, observer)
                    .flatMap(c -> c.closeAsync().concat(succeeded(c)));
        }
    }
}
