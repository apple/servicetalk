/*
 * Copyright © 2019-2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NettyHttpServerConnectionDrainTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String LARGE_TEXT;

    static {
        int capacity = 1_000_000;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(capacity);
        for (int i = 0; i < capacity; i++) {
            sb.append((char) rnd.nextInt(32, 128)); // ASCII
        }
        LARGE_TEXT = sb.toString();
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestIsAutoDrainedWhenUserFailsToConsume(HttpProtocol protocol) throws Exception {
        CountDownLatch requestDrainedByCompletion = new CountDownLatch(1);
        try (ServerContext serverContext = server(protocol, true, (ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(from("OK"), appSerializerUtf8FixLen())),
                transformRequestMessageBody(body -> body.afterOnComplete(requestDrainedByCompletion::countDown)));
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildBlocking()) {

            postLargePayloadAndAssertResponseOk(client);
            requestDrainedByCompletion.await();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestIsAutoDrainedWhenUserFailsToConsumeAndResponseBodyFails(HttpProtocol protocol) throws Exception {
        CountDownLatch requestDrainedByCancellation = new CountDownLatch(1);
        try (ServerContext serverContext = server(protocol, true, (ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(Publisher.failed(DELIBERATE_EXCEPTION))),
                transformRequestMessageBody(body -> body.afterCancel(requestDrainedByCancellation::countDown)));
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildBlocking()) {

            assertThrows(Exception.class, () -> client.request(client.get("/foo")));
            requestDrainedByCancellation.await();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestIsAutoDrainedWhenUserFailsToConsumeAndServiceFails(HttpProtocol protocol) throws Exception {
        CountDownLatch requestDrainedByCompletion = new CountDownLatch(1);
        try (ServerContext serverContext = server(protocol, true,
                (ctx, request, responseFactory) -> Single.failed(DELIBERATE_EXCEPTION),
                transformRequestMessageBody(body -> body.afterOnComplete(requestDrainedByCompletion::countDown)));
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildBlocking()) {

            HttpResponse response = client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializerUtf8()));
            assertThat(response.status(), equalTo(INTERNAL_SERVER_ERROR));
            requestDrainedByCompletion.await();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestIsDrainedByUserWithDrainingDisabled(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = server(protocol, false, (ctx, request, responseFactory) ->
                request.messageBody().ignoreElements() // User consumes payload (ignoring)
                        .concat(succeeded(responseFactory.ok().payloadBody(from("OK"), appSerializerUtf8FixLen()))));
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildBlocking()) {

            postLargePayloadAndAssertResponseOk(client);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestIsConsumedByUserWithDrainingEnabled(HttpProtocol protocol) throws Exception {
        AtomicReference<String> resultRef = new AtomicReference<>();
        try (ServerContext serverContext = server(protocol, true, (ctx, request, responseFactory) ->
                request.payloadBody()
                        // User consumes payload and stores for assert ensuring auto-draining doesn't break it
                        .collect(() -> new StringBuilder(LARGE_TEXT.length()),
                                (sb, b) -> sb.append(b.toString(StandardCharsets.US_ASCII)))
                        .map(StringBuilder::toString)
                        .whenOnSuccess(resultRef::set)
                        .toCompletable()
                        .concat(succeeded(responseFactory.ok().payloadBody(from("OK"), appSerializerUtf8FixLen()))));

             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildBlocking()) {

            postLargePayloadAndAssertResponseOk(client);
            assertThat(resultRef.get(), equalTo(LARGE_TEXT));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void requestTimesOutWithoutAutoDrainingOrUserConsuming(HttpProtocol protocol) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        try (ServerContext serverContext = server(protocol, false, (ctx, request, responseFactory) -> {
            latch.countDown();
            return succeeded(responseFactory.ok().payloadBody(from("OK"), appSerializerUtf8FixLen()));
        });
             StreamingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .buildStreaming()) {

            client.request(client.post("/").payloadBody(from(LARGE_TEXT), appSerializerUtf8FixLen()))
                    // Subscribe to send the request, don't care about the response since graceful close of the server
                    // will hang until the request is consumed, thus we expect the timeout to hit
                    .ignoreElement().subscribe();

            latch.await(); // Wait till the request is received before initiating graceful close of the server
            assertThrows(TimeoutException.class, serverContext::close);
        }
    }

    private static void postLargePayloadAndAssertResponseOk(final BlockingHttpClient client) throws Exception {
        HttpResponse response = client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializerUtf8()));
        assertThat(response.toStreamingResponse().payloadBody(appSerializerUtf8FixLen())
                        .collect(StringBuilder::new, StringBuilder::append).toFuture().get().toString(), equalTo("OK"));
    }

    private static StreamingHttpServiceFilterFactory transformRequestMessageBody(
            UnaryOperator<Publisher<?>> transformer) {
        return service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                request.transformMessageBody(transformer);
                return delegate().handle(ctx, request, responseFactory);
            }
        };
    }

    private static ServerContext server(HttpProtocol protocol, boolean autoDrain, StreamingHttpService handler,
                                        StreamingHttpServiceFilterFactory... filters) throws Exception {
        HttpServerBuilder httpServerBuilder = newServerBuilder(SERVER_CTX, protocol);
        if (!autoDrain) {
            httpServerBuilder = httpServerBuilder.drainRequestPayloadBody(false);
        }
        for (StreamingHttpServiceFilterFactory filter : filters) {
            httpServerBuilder.appendServiceFilter(filter);
        }
        ServerContext serverContext = httpServerBuilder
                .listenStreamingAndAwait(handler);
        return new ServerContext() {
            @Override
            public SocketAddress listenAddress() {
                return serverContext.listenAddress();
            }

            @Override
            public ExecutionContext<?> executionContext() {
                return serverContext.executionContext();
            }

            @Override
            public void acceptConnections(final boolean accept) {
                serverContext.acceptConnections(accept);
            }

            @Override
            public Completable onClose() {
                return serverContext.onClose();
            }

            @Override
            public Completable onClosing() {
                return serverContext.onClosing();
            }

            @Override
            public Completable closeAsync() {
                return serverContext.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return serverContext.closeAsyncGracefully();
            }

            @Override
            public void close() {
                // Without draining, graceful close is expected to hang until the request is consumed. Don't wait
                // too long unless on CI.
                int timeoutSeconds = CI ? 10 : 1;
                awaitTermination(serverContext.closeAsyncGracefully()
                        .timeout(timeoutSeconds, SECONDS)
                        .onErrorResume(t -> serverContext.closeAsync().concat(Completable.failed(t))).toFuture());
            }
        };
    }
}
