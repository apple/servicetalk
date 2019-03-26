/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionClosedException;
import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ConcurrentRequestsHttpConnectionFilterTest {

    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ExecutionContext executionContext;
    @Mock
    private ConnectionContext connectionContext;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<Buffer> response1Publisher = new TestPublisher<>();
    private final TestPublisher<Buffer> response2Publisher = new TestPublisher<>();
    private final TestPublisher<Buffer> response3Publisher = new TestPublisher<>();

    // TODO(jayv) Temporary workaround until DefaultNettyConnection leverages strategy.offloadReceive()
    private static final HttpExecutionStrategy FULLY_NO_OFFLOAD_STRATEGY =
            customStrategyBuilder().executor(immediate()).build();

    @Test
    public void decrementWaitsUntilResponsePayloadIsComplete() throws Exception {
        HttpConnectionFilterFactory mockConnection = new HttpConnectionFilterFactory() {
            @Override
            public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
                return new StreamingHttpConnectionFilter(connection) {
                    private final AtomicInteger reqCount = new AtomicInteger(0);

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
                        return settingKey == MAX_CONCURRENCY ? (Publisher<T>) just(2) : super.settingStream(settingKey);
                    }

                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        switch (reqCount.incrementAndGet()) {
                            case 1: return success(reqRespFactory.ok().payloadBody(response1Publisher));
                            case 2: return success(reqRespFactory.ok().payloadBody(response2Publisher));
                            case 3: return success(reqRespFactory.ok().payloadBody(response3Publisher));
                            default: return error(new UnsupportedOperationException());
                        }
                    }

                    @Override
                    public Completable onClose() {
                        return Completable.never();
                    }
                };
            }
        };

        StreamingHttpConnection limitedConnection = TestStreamingHttpConnection.from(reqRespFactory, executionContext,
                connectionContext, new ConcurrentRequestsHttpConnectionFilter(2).append(mockConnection));

        StreamingHttpResponse resp1 = awaitIndefinitelyNonNull(
                limitedConnection.request(limitedConnection.get("/foo")));
        awaitIndefinitelyNonNull(limitedConnection.request(limitedConnection.get("/bar")));
        try {
            limitedConnection.request(limitedConnection.get("/baz")).toFuture().get();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(MaxRequestLimitExceededException.class)));
        }

        // Consume the first response payload and ignore the content.
        resp1.payloadBody().forEach(chunk -> { });
        response1Publisher.onNext(EMPTY_BUFFER);
        response1Publisher.onComplete();

        // Verify that a new request can be made after the first request completed.
        awaitIndefinitelyNonNull(limitedConnection.request(limitedConnection.get("/baz")));
    }

    @Test
    public void throwMaxConcurrencyExceededOnOversubscribedConnection() throws Exception {
        final Processor lastRequestFinished = newCompletableProcessor();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    Publisher<Buffer> deferredPayload = fromSource(lastRequestFinished).concatWith(empty());
                    return request.payloadBody().ignoreElements()
                            .concatWith(Single.success(responseFactory.ok().payloadBody(deferredPayload)));
                });

             StreamingHttpConnection connection = new DefaultHttpConnectionBuilder<>()
                     .maxPipelinedRequests(2)
                     .buildStreaming(serverContext.listenAddress())
                     .toFuture().get()) {

            Single<StreamingHttpResponse> resp1 = connection.request(connection.get("/one"));
            Single<StreamingHttpResponse> resp2 = connection.request(connection.get("/two"));
            Single<StreamingHttpResponse> resp3 = connection.request(connection.get("/three"));

            try {
                Publisher.from(resp1, resp2, resp3) // Don't consume payloads to build up concurrency
                        .flatMapSingle(Function.identity())
                        .toFuture().get();

                fail("Should not allow three concurrent requests to complete normally");
            } catch (ExecutionException e) {
                assertThat(e.getCause(), instanceOf(MaxRequestLimitExceededException.class));
            } finally {
                lastRequestFinished.onComplete();
            }
        }
    }

    @Test
    public void throwConnectionClosedOnConnectionClose() throws Exception {

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        request.payloadBody().ignoreElements().concatWith(
                        Single.success(responseFactory.ok()
                                .setHeader(HttpHeaderNames.CONNECTION, "close"))));

             HttpConnection connection = new DefaultHttpConnectionBuilder<>()
                     .maxPipelinedRequests(99)
                     .executionStrategy(FULLY_NO_OFFLOAD_STRATEGY)
                     .build(serverContext.listenAddress())
                     .toFuture().get()) {

            Single<? extends HttpResponse> resp1 = connection.request(connection.get("/one"));
            Single<? extends HttpResponse> resp2 = connection.request(connection.get("/two"));

            resp1.toFuture().get();

            try {
                connection.onClose().concatWith(resp2).toFuture().get();
                fail("Should not allow request to complete normally on a closed connection");
            } catch (ExecutionException e) {
                assertThat(e.getCause(), instanceOf(ConnectionClosedException.class));
                assertThat(e.getCause().getCause(), instanceOf(ClosedChannelException.class));
                assertThat(e.getCause().getCause().getMessage(), startsWith("PROTOCOL_CLOSING_INBOUND"));
            }
        }
    }

    @Test
    public void throwConnectionClosedWithCauseOnUnexpectedConnectionClose() throws Exception {

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .socketOption(StandardSocketOptions.SO_LINGER, 0) // Force connection reset on close
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        request.payloadBody().ignoreElements()
                                .concatWith(ctx.closeAsync()) // trigger reset after client is done writing
                                .concatWith(Single.never()));

             HttpConnection connection = new DefaultHttpConnectionBuilder<>()
                     .maxPipelinedRequests(99)
                     .executionStrategy(FULLY_NO_OFFLOAD_STRATEGY)
                     .build(serverContext.listenAddress())
                     .toFuture().get()) {

            Single<? extends HttpResponse> resp1 = connection.request(connection.get("/one"));
            Single<? extends HttpResponse> resp2 = connection.request(connection.get("/two"));

            final AtomicReference<Throwable> ioEx = new AtomicReference<>();

            Publisher.empty()
                    .concatWith(resp1).recoverWith(reset -> {
                        ioEx.set(reset); // Capture connection reset
                        return Publisher.empty();
                    })
                    .toFuture().get();

            final Processor closedFinally = newCompletableProcessor();
            connection.onClose().doAfterFinally(closedFinally::onComplete).subscribe();

            try {
                fromSource(closedFinally).concatWith(resp2).toFuture().get();
                fail("Should not allow request to complete normally on a closed connection");
            } catch (ExecutionException e) {
                assertThat(e.getCause(), instanceOf(ConnectionClosedException.class));
                assertThat(e.getCause().getCause(), instanceOf(IOException.class));
                assertThat(e.getCause().getCause(), equalTo(ioEx.get())); // Assert connection reset
            }
        }
    }
}
