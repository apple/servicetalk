/*
 * Copyright © 2022-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.RedirectConfigBuilder;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.UNPROCESSABLE_ENTITY;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilderWithConfigs;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilderWithConfigs;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExpectContinueTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor");
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor");

    private static final String PAYLOAD = "hello";
    private static final String FAIL = "fail";
    private static final Duration CONTINUE_TIMEOUT = ofMillis(200);
    // Long enough that the timer never releases the payload body before the server sends 100 (Continue).
    private static final Duration LONG_CONTINUE_TIMEOUT = ofMinutes(1);
    private static final int RAW_SOCKET_TIMEOUT_MS = (int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS);
    private static final String CONTINUE_RESPONSE = "HTTP/1.1 100 Continue\r\n\r\n";

    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch sendContinue = new CountDownLatch(1);
    private final CountDownLatch returnResponse = new CountDownLatch(1);
    private final BlockingQueue<StreamingHttpResponse> responses = new LinkedBlockingDeque<>();

    private static List<Arguments> arguments() {
        List<Arguments> list = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            list.add(Arguments.of(protocol, false, false));
            list.add(Arguments.of(protocol, false, true));
            list.add(Arguments.of(protocol, true, false));
            list.add(Arguments.of(protocol, true, true));
        }
        return list;
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectContinue(HttpProtocol protocol, boolean useOtherHeadersFactory, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            sendRequestPayload(payload, allocator);

            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);

            sendFollowUpRequest(connection, withCL, allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectContinueAggregated(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                  boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, withCL, false, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            sendContinue.countDown();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(PAYLOAD + PAYLOAD));

            sendFollowUpRequest(connection.asStreamingConnection(), withCL,
                    allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectContinueThenFailure(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                   boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            requestReceived.countDown();
            sendContinue.await();
            StringBuilder sb = new StringBuilder();
            request.payloadBody().forEach(chunk -> sb.append(chunk.toString(US_ASCII)));
            returnResponse.await();
            try (HttpPayloadWriter<Buffer> writer = response.status(UNPROCESSABLE_ENTITY).sendMetaData()) {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii(sb));
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            sendRequestPayload(payload, allocator);

            returnResponse.countDown();
            assertResponse(UNPROCESSABLE_ENTITY, PAYLOAD + PAYLOAD);

            sendFollowUpRequest(connection, withCL, allocator, UNPROCESSABLE_ENTITY);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectContinueThenFailureThenRequestPayload(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                     boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            requestReceived.countDown();
            sendContinue.await();
            BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
            returnResponse.await();
            try (HttpPayloadWriter<Buffer> writer = response.status(UNPROCESSABLE_ENTITY).sendMetaData()) {
                while (iterator.hasNext()) {
                    Buffer next = iterator.next();
                    if (next != null) {
                        writer.write(next);
                    }
                }
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            payload.awaitSubscribed();

            returnResponse.countDown();
            StreamingHttpResponse response = responses.take();
            assertThat(response.status(), is(UNPROCESSABLE_ENTITY));

            payload.onNext(allocator.fromAscii(PAYLOAD));
            payload.onNext(allocator.fromAscii(PAYLOAD));
            payload.onComplete();
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII),
                    equalTo(PAYLOAD + PAYLOAD));

            sendFollowUpRequest(connection, withCL, allocator, UNPROCESSABLE_ENTITY);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectContinueConnectionClose(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                       boolean withCL) throws Exception {
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload).setHeader(CONNECTION, CLOSE))
                    .subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            sendRequestPayload(payload, connection.executionContext().bufferAllocator());

            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);

            connection.onClose().toFuture().get();
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void serverRespondsWithSuccess(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                   boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
                requestReceived.countDown();
                returnResponse.await();
                response.status(ACCEPTED);
                try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                    for (Buffer chunk : request.payloadBody()) {
                        writer.write(chunk);
                    }
                }
            });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();

            StreamingHttpResponse response = responses.take();
            assertThat(response.status(), is(ACCEPTED));
            sendRequestPayload(payload, allocator);
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII),
                    equalTo(PAYLOAD + PAYLOAD));

            sendFollowUpRequest(connection, withCL, allocator, ACCEPTED);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void serverRespondsWithSuccessAggregated(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                             boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            requestReceived.countDown();
            returnResponse.await();
            response.status(ACCEPTED);
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                for (Buffer chunk : request.payloadBody()) {
                    writer.write(chunk);
                }
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            returnResponse.countDown();
            HttpResponse response = connection.request(newRequest(connection, withCL, false, PAYLOAD + PAYLOAD,
                    allocator)).toFuture().get();
            assertThat(response.status(), is(ACCEPTED));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(PAYLOAD + PAYLOAD));

            sendFollowUpRequest(connection.asStreamingConnection(), withCL, allocator, ACCEPTED);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void serverRespondsWithRedirect(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                    boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            if ("/redirect".equals(request.requestTarget())) {
                response.status(PERMANENT_REDIRECT);
                response.setHeader(LOCATION, "/");
                response.sendMetaData().close();
                return;
            }
            requestReceived.countDown();
            sendContinue.await();
            StringBuilder sb = new StringBuilder();
            request.payloadBody().forEach(chunk -> sb.append(chunk.toString(US_ASCII)));
            returnResponse.await();
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii(sb));
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory,
                     new RedirectingHttpRequesterFilter(
                     new RedirectConfigBuilder()
                             .allowedMethods(POST)
                             .headersToRedirect(CONTENT_LENGTH, TRANSFER_ENCODING, EXPECT)
                             .redirectPayloadBody(true)
                             .build()));
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();
            // HTTP/1.1 closes the connection after a redirect that is sent before the payload body, so the redirect
            // can not be followed on the same connection.
            StreamingHttpRequester requester = protocol == HTTP_1 ? client : connection;

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            requester.request(newRequest(requester, withCL, false, payload).requestTarget("/redirect"))
                    .subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            sendRequestPayload(payload, allocator);

            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);

            sendFollowUpRequest(requester, withCL, allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectationFailed(HttpProtocol protocol, boolean useOtherHeadersFactory, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();
            assertResponse(EXPECTATION_FAILED, "");
            assertThat("Unexpected subscribe to payload body on expectation failed",
                    payload.isSubscribed(), is(false));

            sendContinue.countDown();
            sendFollowUpRequest(followUpRequester(protocol, client, connection), withCL,
                    connection.executionContext().bufferAllocator(), OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectationFailedAggregated(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                     boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, withCL, true, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            sendContinue.countDown();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(""));

            sendFollowUpRequest(followUpRequester(protocol, client, connection.asStreamingConnection()), withCL,
                    allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void expectationFailedConnectionClose(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                          boolean withCL) throws Exception {
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, true, payload).setHeader(CONNECTION, CLOSE))
                    .subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();
            assertResponse(EXPECTATION_FAILED, "");
            assertThat("Unexpected subscribe to payload body on expectation failed",
                    payload.isSubscribed(), is(false));

            connection.onClose().toFuture().get();
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void serverError(HttpProtocol protocol, boolean useOtherHeadersFactory, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
                requestReceived.countDown();
                returnResponse.await();
                if (request.headers().contains(EXPECT, CONTINUE)) {
                    response.status(INTERNAL_SERVER_ERROR);
                }
                response.sendMetaData().close();
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();
            assertResponse(INTERNAL_SERVER_ERROR, "");

            // send a follow-up request on the same connection, if it was not closed:
            StreamingHttpRequester requester = followUpRequester(protocol, client, connection);
            requester.request(requester.get("/")).subscribe(responses::add);
            assertResponse(OK, "");
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void serverErrorAggregated(HttpProtocol protocol, boolean useOtherHeadersFactory,
                               boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            requestReceived.countDown();
            returnResponse.await();
            if (request.headers().contains(EXPECT, CONTINUE)) {
                response.status(INTERNAL_SERVER_ERROR);
            }
            response.sendMetaData().close();
        });
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, withCL, false, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(""));

            // send a follow-up request on the same connection, if it was not closed:
            StreamingHttpRequester requester = followUpRequester(protocol, client, connection.asStreamingConnection());
            response = requester.request(requester.get("/")).toFuture().get().toResponse().toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(""));
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void retryExpectationFailed(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory,
                     new RetryingHttpRequesterFilter.Builder().retryExpectationFailed(true).build())) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            client.request(newRequest(client, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();
            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();
            sendContinue.countDown();
            sendRequestPayload(payload, client.executionContext().bufferAllocator());
            assertResponse(OK, PAYLOAD + PAYLOAD);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void retryExpectationFailedAggregated(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                          boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             HttpClient client = createClient(serverContext, protocol, useOtherHeadersFactory,
                     new RetryingHttpRequesterFilter.Builder().retryExpectationFailed(true).build()).asClient()) {

            Future<HttpResponse> responseFuture = client.request(newRequest(client, withCL, true, PAYLOAD + PAYLOAD,
                    client.executionContext().bufferAllocator())).toFuture();
            requestReceived.await();
            sendContinue.countDown();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(PAYLOAD + PAYLOAD));
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void continueTimeoutSendsPayloadAndLateContinueIsHarmless(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                              boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, useOtherHeadersFactory,
                     CONTINUE_TIMEOUT);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            // The server withholds 100 (Continue) until sendContinue is counted down, so only the timeout can
            // request the payload body.
            sendRequestPayload(payload, allocator);

            // The server reads the payload body that was already delivered and answers with a late 100 (Continue).
            sendContinue.countDown();
            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);

            sendFollowUpRequest(connection, withCL, allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void continueTimeoutThenLateErrorResponseReachesCaller(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                           boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory,
                (ctx, request, response) -> {
            requestReceived.countDown();
            returnResponse.await();
            if (request.headers().contains(EXPECT, CONTINUE)) {
                response.status(INTERNAL_SERVER_ERROR);
            }
            response.sendMetaData().close();
        });
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, useOtherHeadersFactory,
                     CONTINUE_TIMEOUT);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            sendRequestPayload(payload, allocator);
            returnResponse.countDown();
            assertResponse(INTERNAL_SERVER_ERROR, "");
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void zeroContinueTimeoutSendsPayloadWithoutWaiting(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                       boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, useOtherHeadersFactory,
                     Duration.ZERO);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload)).subscribe(responses::add);
            requestReceived.await();

            // The server withholds 100 (Continue) until sendContinue is counted down. It is ignored when it arrives.
            sendRequestPayload(payload, allocator);

            sendContinue.countDown();
            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);

            sendFollowUpRequest(connection, withCL, allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void zeroContinueTimeoutEarlyRejectionReachesCaller(HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                        boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, useOtherHeadersFactory,
                     Duration.ZERO);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();

            sendRequestPayload(payload, allocator);
            returnResponse.countDown();
            assertResponse(EXPECTATION_FAILED, "");
            // The server resets its request decoder when it rejects, so the connection can't be reused after the
            // payload body was sent.
        }
    }

    @ParameterizedTest(name = "protocol={0} useOtherHeadersFactory={1} withCL={2}")
    @MethodSource("arguments")
    void zeroContinueTimeoutAggregatedSendsPayloadWithoutWaiting(HttpProtocol protocol,
                                                                 boolean useOtherHeadersFactory,
                                                                 boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, useOtherHeadersFactory);
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, useOtherHeadersFactory,
                     Duration.ZERO);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, withCL, false, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            // The server withholds 100 (Continue) until sendContinue is counted down.
            sendContinue.countDown();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(PAYLOAD + PAYLOAD));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void zeroContinueTimeoutAggregatedEarlyRejectionReachesCaller(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, false);
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, false, Duration.ZERO);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, true, true, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void defaultContinueTimeoutSendsPayloadWithoutContinue(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, false);
             StreamingHttpClient client = createClientWithDefaultTimeout(serverContext, protocol, false);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, true, false, payload)).subscribe(responses::add);
            requestReceived.await();

            // Nothing releases the payload body except the default timeout, which blocks here until it fires.
            sendRequestPayload(payload, allocator);

            sendContinue.countDown();
            returnResponse.countDown();
            assertResponse(OK, PAYLOAD + PAYLOAD);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void continueTimeoutAppliesToEveryRequest(HttpProtocol protocol) throws Exception {
        final BlockingQueue<CountDownLatch> serverGates = new LinkedBlockingDeque<>();
        try (HttpServerContext serverContext = startServer(protocol, false, (ctx, request, response) -> {
            final CountDownLatch gate = new CountDownLatch(1);
            serverGates.add(gate);
            gate.await();
            StringBuilder sb = new StringBuilder();
            request.payloadBody().forEach(chunk -> sb.append(chunk.toString(US_ASCII)));
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii(sb));
            }
        });
             StreamingHttpClient client = createClientWithTimeout(serverContext, protocol, false,
                     CONTINUE_TIMEOUT)) {
            final BufferAllocator allocator = client.executionContext().bufferAllocator();
            for (int i = 0; i < 2; i++) {
                TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
                client.request(newRequest(client, true, false, payload)).subscribe(responses::add);
                final CountDownLatch gate = serverGates.take();

                // The server holds 100 (Continue) until the gate opens, so only the timeout requests the payload.
                sendRequestPayload(payload, allocator);
                gate.countDown();
                assertResponse(OK, PAYLOAD + PAYLOAD);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxPipelinedRequests={0}")
    @ValueSource(ints = {1, 2})
    void interimResponsesBeforeContinue(int maxPipelinedRequests) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, maxPipelinedRequests, LONG_CONTINUE_TIMEOUT);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            raw.write("HTTP/1.1 102 Processing\r\n\r\nHTTP/1.1 103 Early Hints\r\n\r\n");
            raw.write(CONTINUE_RESPONSE);
            raw.respondWithBody(contentLength);
            assertRawServerResponse(response.get(), OK, PAYLOAD + PAYLOAD);

            sendRawServerFollowUpRequest(connection, raw);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxPipelinedRequests={0}")
    @ValueSource(ints = {1, 2})
    void lateContinueAfterTimeout(int maxPipelinedRequests) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, maxPipelinedRequests, CONTINUE_TIMEOUT);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            // Only the timeout releases the payload body, the server has not responded yet.
            String body = raw.readBody(contentLength);
            raw.write(CONTINUE_RESPONSE);
            raw.write(okResponse(body));
            assertRawServerResponse(response.get(), OK, PAYLOAD + PAYLOAD);

            sendRawServerFollowUpRequest(connection, raw);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxPipelinedRequests={0} status={1}")
    @CsvSource({"1,413", "1,417", "2,413", "2,417"})
    void lateRejectionAfterTimeoutReachesCaller(int maxPipelinedRequests, int status) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, maxPipelinedRequests, CONTINUE_TIMEOUT);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            raw.readBody(contentLength);
            raw.write("HTTP/1.1 " + status + " Rejected\r\ncontent-length: 0\r\n\r\n");
            assertRawServerResponse(response.get(), HttpResponseStatus.of(status, "Rejected"), "");

            sendRawServerFollowUpRequest(connection, raw);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxPipelinedRequests={0}")
    @ValueSource(ints = {1, 2})
    void duplicateContinue(int maxPipelinedRequests) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, maxPipelinedRequests, LONG_CONTINUE_TIMEOUT);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            raw.write(CONTINUE_RESPONSE + CONTINUE_RESPONSE);
            raw.respondWithBody(contentLength);
            assertRawServerResponse(response.get(), OK, PAYLOAD + PAYLOAD);

            sendRawServerFollowUpRequest(connection, raw);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] maxPipelinedRequests={0}")
    @ValueSource(ints = {1, 2})
    void continueAndFinalResponseInOneRead(int maxPipelinedRequests) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, maxPipelinedRequests, LONG_CONTINUE_TIMEOUT);
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            raw.write(CONTINUE_RESPONSE + okResponse(""));
            assertThat(raw.readBody(contentLength), equalTo(PAYLOAD + PAYLOAD));
            assertRawServerResponse(response.get(), OK, "");

            sendRawServerFollowUpRequest(connection, raw);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] lateFinalStatus={0}")
    @ValueSource(ints = {200, 417})
    void pipelinedLateResponsesDoNotAffectNextRequest(int lateFinalStatus) throws Exception {
        try (RawServer server = new RawServer();
             HttpClient client = newRawServerClient(server, 2, ofMillis(500));
             ReservedHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            Future<HttpResponse> first = connection.request(newRawServerRequest(connection)).toFuture();
            Future<HttpResponse> second = connection.request(newRawServerRequest(connection)
                    .payloadBody(connection.executionContext().bufferAllocator().fromAscii("second"))).toFuture();
            RawConnection raw = server.accept();
            int firstContentLength = raw.readRequestHeaders();
            raw.readBody(firstContentLength);
            // The second request is written only after the first one completes.
            int secondContentLength = raw.readRequestHeaders();
            raw.write(CONTINUE_RESPONSE);
            raw.write("HTTP/1.1 " + lateFinalStatus + " Late\r\ncontent-length: 0\r\n\r\n");
            raw.write(CONTINUE_RESPONSE);
            raw.respondWithBody(secondContentLength);
            assertRawServerResponse(first.get(), HttpResponseStatus.of(lateFinalStatus, "Late"), "");
            assertRawServerResponse(second.get(), OK, "second");
        }
    }

    @Test
    void pipelinedExpectRequestIgnoresFlushStrategyOfAggregatedRequestQueuedBehind() throws Exception {
        final BufferAllocator alloc = CLIENT_CTX.bufferAllocator();
        try (RawServer server = new RawServer();
             StreamingHttpClient client = newClientBuilderWithConfigs(serverHostAndPort(server.address()), CLIENT_CTX,
                     h1().maxPipelinedRequests(3).build())
                     // Subscribe on the caller thread to queue requests in order.
                     .executionStrategy(offloadNone())
                     .expectContinueTimeout(LONG_CONTINUE_TIMEOUT)
                     .buildStreaming();
             ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            // Keeps the first write in progress, so the next two requests are queued.
            TestPublisher<Buffer> firstPayload = new TestPublisher<>();
            Future<StreamingHttpResponse> first = connection.request(connection.post("/")
                    .setHeader(CONTENT_LENGTH, valueOf(PAYLOAD.length()))
                    .payloadBody(firstPayload)).toFuture();
            Future<StreamingHttpResponse> second = connection.request(newRequest(connection, true, false,
                    from(alloc.fromAscii(PAYLOAD), alloc.fromAscii(PAYLOAD)))).toFuture();
            // Aggregated requests flush on end, which must not apply to the Expect request queued before.
            HttpConnection aggregated = connection.asConnection();
            Future<HttpResponse> third = aggregated.request(aggregated.post("/")
                    .payloadBody(alloc.fromAscii("third"))).toFuture();

            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            firstPayload.awaitSubscribed();
            firstPayload.onNext(alloc.fromAscii(PAYLOAD));
            firstPayload.onComplete();
            raw.respondWithBody(contentLength);
            // Responses are read in order, so consume the first one to let the client read the 100 (Continue).
            assertRawServerResponse(first.get().toResponse().toFuture().get(), OK, PAYLOAD);

            contentLength = raw.readRequestHeaders();
            assertThat(raw.lastRequestHadExpectContinue(), is(true));
            raw.write(CONTINUE_RESPONSE);
            raw.respondWithBody(contentLength);
            assertRawServerResponse(second.get().toResponse().toFuture().get(), OK, PAYLOAD + PAYLOAD);

            raw.respondWithBody(raw.readRequestHeaders());
            assertRawServerResponse(third.get(), OK, "third");
        }
    }

    @Test
    void retryExpectationFailedRetriesWithDuplicatedPayloadBody() throws Exception {
        final Queue<Object> writtenItems = new LinkedBlockingQueue<>();
        final Buffer body = CLIENT_CTX.bufferAllocator().fromAscii(PAYLOAD + PAYLOAD);
        try (RawServer server = new RawServer();
             StreamingHttpClient client = newClientBuilderWithConfigs(serverHostAndPort(server.address()),
                     CLIENT_CTX, h1Default())
                     .expectContinueTimeout(LONG_CONTINUE_TIMEOUT)
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .retryExpectationFailed(true).build())
                     .appendConnectionFilter(c -> new StreamingHttpConnectionFilter(c) {
                         @Override
                         public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                             return delegate().request(request.transformMessageBody(
                                     p -> p.beforeOnNext(writtenItems::add)));
                         }
                     })
                     .buildStreaming()) {
            Future<StreamingHttpResponse> response = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(CONTENT_LENGTH, valueOf(body.readableBytes()))
                    .payloadBody(from(body))).toFuture();
            RawConnection raw = server.accept();
            raw.readRequestHeaders();
            raw.write("HTTP/1.1 417 Expectation Failed\r\ncontent-length: 0\r\n\r\n");

            // The declared payload body was not sent, so the first connection can not be reused: the retry arrives on
            // a new connection.
            RawConnection retry = server.accept();
            int contentLength = retry.readRequestHeaders();
            assertThat(retry.lastRequestHadExpectContinue(), is(false));
            retry.respondWithBody(contentLength);
            assertThat(response.get().toResponse().toFuture().get().payloadBody().toString(US_ASCII),
                    equalTo(PAYLOAD + PAYLOAD));
            final Object written = writtenItems.poll();
            assertThat(written, is(instanceOf(Buffer.class)));
            assertThat(written, is(not(sameInstance(body))));
        }
    }

    @Test
    void retryExpectationFailedDoesNotRetryAfterPayloadBodySent() throws Exception {
        try (RawServer server = new RawServer();
             StreamingHttpClient client = newClientBuilderWithConfigs(serverHostAndPort(server.address()),
                     CLIENT_CTX, h1Default())
                     .expectContinueTimeout(CONTINUE_TIMEOUT)
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .retryExpectationFailed(true).build())
                     .buildStreaming()) {
            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            Future<StreamingHttpResponse> response = client.request(newRequest(client, true, false, payload))
                    .toFuture();
            RawConnection raw = server.accept();
            int contentLength = raw.readRequestHeaders();
            // Only the timeout releases the payload body, the server has not responded yet.
            sendRequestPayload(payload, client.executionContext().bufferAllocator());
            raw.readBody(contentLength);
            raw.write("HTTP/1.1 417 Expectation Failed\r\ncontent-length: 0\r\n\r\n");

            ExecutionException e = assertThrows(ExecutionException.class, response::get);
            assertThat(e.getCause(), is(instanceOf(ExpectationFailedException.class)));
        }
    }

    @Test
    void retryExpectationFailedRetriesEarlyRejectionOfAggregatedRequest() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(request.headers().contains(EXPECT) ?
                        factory.newResponse(EXPECTATION_FAILED) :
                        factory.ok().payloadBody(request.payloadBody())));
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .expectContinueTimeout(ofMinutes(1))
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .retryExpectationFailed(true).build())
                     .buildBlocking()) {
            HttpResponse response = client.request(client.post("/upload").setHeader(EXPECT, CONTINUE)
                    .payloadBody("body", textSerializerUtf8()));
            assertThat(response.status(), is(OK));
        }
    }

    private static HttpClient newRawServerClient(RawServer server, int maxPipelinedRequests,
                                                 Duration expectContinueTimeout) {
        return newClientBuilderWithConfigs(serverHostAndPort(server.address()), CLIENT_CTX,
                h1().maxPipelinedRequests(maxPipelinedRequests).build())
                .expectContinueTimeout(expectContinueTimeout)
                .build();
    }

    private static HttpRequest newRawServerRequest(HttpRequestFactory factory) {
        return factory.post("/")
                .setHeader(EXPECT, CONTINUE)
                .payloadBody(CLIENT_CTX.bufferAllocator().fromAscii(PAYLOAD + PAYLOAD));
    }

    private static void sendRawServerFollowUpRequest(HttpConnection connection, RawConnection raw) throws Exception {
        Future<HttpResponse> response = connection.request(newRawServerRequest(connection)).toFuture();
        int contentLength = raw.readRequestHeaders();
        raw.write(CONTINUE_RESPONSE);
        raw.respondWithBody(contentLength);
        assertRawServerResponse(response.get(), OK, PAYLOAD + PAYLOAD);
    }

    private static void assertRawServerResponse(HttpResponse response, HttpResponseStatus expectedStatus,
                                                String expectedPayload) {
        assertThat(response.status().code(), is(expectedStatus.code()));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo(expectedPayload));
    }

    private static String okResponse(String body) {
        return "HTTP/1.1 200 OK\r\ncontent-length: " + body.length() + "\r\n\r\n" + body;
    }

    private static final class RawServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<Socket> sockets = new ArrayList<>();

        RawServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
            // Fail instead of hanging: blocking socket operations ignore the test timeout's interrupt.
            serverSocket.setSoTimeout(RAW_SOCKET_TIMEOUT_MS);
        }

        SocketAddress address() {
            return serverSocket.getLocalSocketAddress();
        }

        RawConnection accept() throws IOException {
            final Socket socket = serverSocket.accept();
            sockets.add(socket);
            return new RawConnection(socket);
        }

        @Override
        public void close() throws IOException {
            try {
                for (Socket socket : sockets) {
                    socket.close();
                }
            } finally {
                serverSocket.close();
            }
        }
    }

    private static final class RawConnection {
        private final BufferedReader in;
        private final OutputStream out;
        private boolean lastRequestHadExpectContinue;

        RawConnection(Socket socket) throws IOException {
            socket.setSoTimeout(RAW_SOCKET_TIMEOUT_MS);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
            out = socket.getOutputStream();
        }

        int readRequestHeaders() throws IOException {
            int contentLength = 0;
            lastRequestHadExpectContinue = false;
            String line = in.readLine();
            assertThat(line, startsWith("POST "));
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                String name = line.substring(0, colon);
                String value = line.substring(colon + 1).trim();
                if (CONTENT_LENGTH.toString().equalsIgnoreCase(name)) {
                    contentLength = parseInt(value);
                } else if (EXPECT.toString().equalsIgnoreCase(name)) {
                    lastRequestHadExpectContinue = CONTINUE.toString().equalsIgnoreCase(value);
                }
            }
            return contentLength;
        }

        boolean lastRequestHadExpectContinue() {
            return lastRequestHadExpectContinue;
        }

        String readBody(int contentLength) throws IOException {
            char[] body = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int n = in.read(body, read, contentLength - read);
                assertThat("Unexpected end of stream", n, is(greaterThanOrEqualTo(0)));
                read += n;
            }
            return new String(body);
        }

        void respondWithBody(int contentLength) throws IOException {
            write(okResponse(readBody(contentLength)));
        }

        void write(String response) throws IOException {
            out.write(response.getBytes(US_ASCII));
            out.flush();
        }
    }

    private HttpServerContext startServer(HttpProtocol protocol, boolean useOtherHeadersFactory) throws Exception {
        return startServer(protocol, useOtherHeadersFactory, (ctx, request, response) -> {
                    requestReceived.countDown();
                    if (request.headers().containsIgnoreCase(EXPECT, CONTINUE) &&
                            request.headers().contains(FAIL, "true")) {
                        returnResponse.await();
                        response.status(EXPECTATION_FAILED).setHeader(CONTENT_LENGTH, ZERO);
                        response.sendMetaData().close();
                        return;
                    }
                    sendContinue.await();
                    StringBuilder sb = new StringBuilder();
                    request.payloadBody().forEach(chunk -> sb.append(chunk.toString(US_ASCII)));
                    returnResponse.await();
                    try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                        writer.write(ctx.executionContext().bufferAllocator().fromAscii(sb));
                    }
                });
    }

    private static HttpServerContext startServer(HttpProtocol protocol,
                                                 boolean useOtherHeadersFactory,
                                                 BlockingStreamingHttpService service) throws Exception {
        return newServerBuilderWithConfigs(SERVER_CTX,
                useOtherHeadersFactory ? protocol.configOtherHeadersFactory : protocol.config)
                .listenBlockingStreamingAndAwait(service);
    }

    private static StreamingHttpClient createClient(HttpServerContext serverContext, HttpProtocol protocol,
                                                    boolean useOtherHeadersFactory) {
        return createClient(serverContext, protocol, useOtherHeadersFactory, null);
    }

    private static StreamingHttpClient createClient(HttpServerContext serverContext,
                                                    HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                    @Nullable StreamingHttpClientFilterFactory filterFactory) {
        return createClient(serverContext, protocol, useOtherHeadersFactory, filterFactory, LONG_CONTINUE_TIMEOUT);
    }

    private static StreamingHttpClient createClientWithDefaultTimeout(HttpServerContext serverContext,
                                                                      HttpProtocol protocol,
                                                                      boolean useOtherHeadersFactory) {
        return createClient(serverContext, protocol, useOtherHeadersFactory, null, null);
    }

    private static StreamingHttpClient createClientWithTimeout(HttpServerContext serverContext,
                                                               HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                               Duration expectContinueTimeout) {
        return createClient(serverContext, protocol, useOtherHeadersFactory, null, expectContinueTimeout);
    }

    private static StreamingHttpClient createClient(HttpServerContext serverContext,
                                                    HttpProtocol protocol, boolean useOtherHeadersFactory,
                                                    @Nullable StreamingHttpClientFilterFactory filterFactory,
                                                    @Nullable Duration expectContinueTimeout) {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = newClientBuilderWithConfigs(
                serverContext, CLIENT_CTX,
                useOtherHeadersFactory ? protocol.configOtherHeadersFactory : protocol.config);
        if (filterFactory != null) {
            builder.appendClientFilter(filterFactory);
        }
        if (expectContinueTimeout != null) {
            builder.expectContinueTimeout(expectContinueTimeout);
        }
        return builder.buildStreaming();
    }

    private static StreamingHttpRequest newRequest(StreamingHttpRequestFactory factory,
                                                   boolean withCL, boolean fail,
                                                   Publisher<Buffer> payload) {
        return factory.post("/")
                .setHeader(EXPECT, CONTINUE)
                .setHeader(withCL ? CONTENT_LENGTH : TRANSFER_ENCODING,
                        withCL ? valueOf(PAYLOAD.length() * 2) : CHUNKED)
                .setHeader(FAIL, valueOf(fail))
                .payloadBody(payload);
    }

    private static HttpRequest newRequest(HttpRequestFactory factory,
                                          boolean withCL, boolean fail,
                                          String payload,
                                          BufferAllocator allocator) {
        return factory.post("/")
                .setHeader(EXPECT, CONTINUE)
                .setHeader(withCL ? CONTENT_LENGTH : TRANSFER_ENCODING,
                        withCL ? valueOf(PAYLOAD.length() * 2) : CHUNKED)
                .setHeader(FAIL, valueOf(fail))
                .payloadBody(allocator.fromAscii(payload));
    }

    /**
     * HTTP/1.1 can not skip a declared payload body that was not sent after an early final response, so it must close
     * the connection. HTTP/2 resets only the stream, so the connection can be reused.
     */
    private static StreamingHttpRequester followUpRequester(HttpProtocol protocol, StreamingHttpClient client,
                                                            StreamingHttpConnection connection) throws Exception {
        if (protocol == HTTP_1) {
            connection.onClose().toFuture().get();
            return client;
        }
        return connection;
    }

    private static void sendRequestPayload(TestPublisher<Buffer> payload, BufferAllocator alloc) {
        payload.awaitSubscribed();
        payload.onNext(alloc.fromAscii(PAYLOAD));
        payload.onNext(alloc.fromAscii(PAYLOAD));
        payload.onComplete();
    }

    private void sendFollowUpRequest(StreamingHttpRequester requester, boolean withCL,
                                     BufferAllocator alloc, HttpResponseStatus expectedStatus) throws Exception {
        TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
        requester.request(newRequest(requester, withCL, false, payload)).subscribe(responses::add);
        sendRequestPayload(payload, alloc);
        assertResponse(expectedStatus, PAYLOAD + PAYLOAD);
    }

    private void assertResponse(HttpResponseStatus expectedStatus, String expectedPayload) throws Exception {
        StreamingHttpResponse response = responses.take();
        assertThat(response.status(), is(expectedStatus));
        assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII), equalTo(expectedPayload));
    }
}
