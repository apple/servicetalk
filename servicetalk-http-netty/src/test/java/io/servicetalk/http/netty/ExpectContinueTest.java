/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestPublisher;
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
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;
import javax.annotation.Nullable;

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
import static io.servicetalk.http.netty.BuilderUtils.newClientWithConfigs;
import static io.servicetalk.http.netty.BuilderUtils.newLocalServerWithConfigs;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
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

    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch sendContinue = new CountDownLatch(1);
    private final CountDownLatch returnResponse = new CountDownLatch(1);
    private final BlockingQueue<StreamingHttpResponse> responses = new LinkedBlockingDeque<>();

    private static Stream<Arguments> arguments() {
        return Stream.of(Arguments.of(HTTP_1, false),
                Arguments.of(HTTP_1, true),
                Arguments.of(HttpProtocol.HTTP_2, false),
                Arguments.of(HttpProtocol.HTTP_2, true));
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectContinue(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectContinueAggregated(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectContinueThenFailure(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
            requestReceived.countDown();
            sendContinue.await();
            StringBuilder sb = new StringBuilder();
            request.payloadBody().forEach(chunk -> sb.append(chunk.toString(US_ASCII)));
            returnResponse.await();
            try (HttpPayloadWriter<Buffer> writer = response.status(UNPROCESSABLE_ENTITY).sendMetaData()) {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii(sb));
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectContinueThenFailureThenRequestPayload(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
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
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectContinueConnectionClose(HttpProtocol protocol, boolean withCL) throws Exception {
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void serverRespondsWithSuccess(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
                requestReceived.countDown();
                returnResponse.await();
                response.status(ACCEPTED);
                try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                    for (Buffer chunk : request.payloadBody()) {
                        writer.write(chunk);
                    }
                }
            });
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void serverRespondsWithSuccessAggregated(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
            requestReceived.countDown();
            returnResponse.await();
            response.status(ACCEPTED);
            try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                for (Buffer chunk : request.payloadBody()) {
                    writer.write(chunk);
                }
            }
        });
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void serverRespondsWithRedirect(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
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
             StreamingHttpClient client = createClient(serverContext, protocol, new RedirectingHttpRequesterFilter(
                     new RedirectConfigBuilder()
                             .allowedMethods(POST)
                             .headersToRedirect(CONTENT_LENGTH, TRANSFER_ENCODING, EXPECT)
                             .redirectPayloadBody(true)
                             .build()));
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, false, payload).requestTarget("/redirect"))
                    .subscribe(responses::add);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectationFailed(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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
            sendFollowUpRequest(connection, withCL, connection.executionContext().bufferAllocator(), OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectationFailedAggregated(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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

            sendFollowUpRequest(connection.asStreamingConnection(), withCL, allocator, OK);
        }
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void expectationFailedConnectionClose(HttpProtocol protocol, boolean withCL) throws Exception {
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol);
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

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void serverError(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
                requestReceived.countDown();
                returnResponse.await();
                if (request.headers().contains(EXPECT, CONTINUE)) {
                    response.status(INTERNAL_SERVER_ERROR);
                }
                response.sendMetaData().close();
        });
             StreamingHttpClient client = createClient(serverContext, protocol);
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            connection.request(newRequest(connection, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            returnResponse.countDown();
            assertResponse(INTERNAL_SERVER_ERROR, "");

            // send a follow-up request on the same connection:
            connection.request(connection.get("/")).subscribe(responses::add);
            assertResponse(OK, "");
        }
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void serverErrorAggregated(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol, (ctx, request, response) -> {
            requestReceived.countDown();
            returnResponse.await();
            if (request.headers().contains(EXPECT, CONTINUE)) {
                response.status(INTERNAL_SERVER_ERROR);
            }
            response.sendMetaData().close();
        });
             StreamingHttpClient client = createClient(serverContext, protocol);
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get().asConnection()) {
            BufferAllocator allocator = connection.executionContext().bufferAllocator();

            Future<HttpResponse> responseFuture = connection.request(
                    newRequest(connection, withCL, false, PAYLOAD + PAYLOAD, allocator)).toFuture();
            requestReceived.await();
            returnResponse.countDown();
            HttpResponse response = responseFuture.get();
            assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(""));

            // send a follow-up request on the same connection:
            response = connection.request(connection.get("/")).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(""));
        }
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void retryExpectationFailed(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             StreamingHttpClient client = createClient(serverContext, protocol,
                     new RetryingHttpRequesterFilter.Builder().retryExpectationFailed(true).build())) {

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            client.request(newRequest(client, withCL, true, payload)).subscribe(responses::add);
            requestReceived.await();
            returnResponse.countDown();

            assertThat("Unexpected subscribe to payload body before 100 (Continue)",
                    payload.isSubscribed(), is(false));
            sendContinue.countDown();
            sendRequestPayload(payload, client.executionContext().bufferAllocator());
            assertResponse(OK, PAYLOAD + PAYLOAD);
        }
    }

    @ParameterizedTest(name = "protocol={0} withCL={1}")
    @MethodSource("arguments")
    void retryExpectationFailedAggregated(HttpProtocol protocol, boolean withCL) throws Exception {
        try (HttpServerContext serverContext = startServer(protocol);
             HttpClient client = createClient(serverContext, protocol,
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

    private HttpServerContext startServer(HttpProtocol protocol) throws Exception {
        return startServer(protocol, (ctx, request, response) -> {
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
                                                 BlockingStreamingHttpService service) throws Exception {
        return newLocalServerWithConfigs(SERVER_CTX,
                protocol == HTTP_2 ? protocol.configOtherHeaderFactory : protocol.config)
                .listenBlockingStreamingAndAwait(service);
    }

    private static StreamingHttpClient createClient(HttpServerContext serverContext, HttpProtocol protocol) {
        return createClient(serverContext, protocol, null);
    }

    private static StreamingHttpClient createClient(HttpServerContext serverContext,
                                                    HttpProtocol protocol,
                                                    @Nullable StreamingHttpClientFilterFactory filterFactory) {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = newClientWithConfigs(
                serverContext, CLIENT_CTX, protocol == HTTP_2 ? protocol.configOtherHeaderFactory : protocol.config);
        if (filterFactory != null) {
            builder.appendClientFilter(filterFactory);
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
