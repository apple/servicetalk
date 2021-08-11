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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.DelegatingHttpServiceContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.IMMEDIATE;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_COUNTER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_COUNTER_NO_LAST_CHUNK;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_LARGE_LAST;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NO_CONTENT;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ROT13;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_SINGLE_ERROR;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_TEST_PUBLISHER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.CombinableMatcher.either;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class NettyHttpServerTest extends AbstractNettyHttpServerTest {

    private final TestCompletableSubscriber completableListenerRule = new TestCompletableSubscriber();
    private final StreamingHttpRequestResponseFactory reqRespFactory =
        new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);

    private final TestPublisher<Buffer> publisher = new TestPublisher<>();
    private final CountDownLatch serviceHandleLatch = new CountDownLatch(1);
    private final AtomicReference<Single<Throwable>> capturedServiceTransportErrorRef = new AtomicReference<>();

    @SuppressWarnings("unused")
    private static Stream<Arguments> clientExecutors() {
        return Stream.of(
            Arguments.of(IMMEDIATE, IMMEDIATE),
            Arguments.of(IMMEDIATE, CACHED),
            Arguments.of(CACHED, IMMEDIATE),
            Arguments.of(CACHED, CACHED));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetNoRequestPayloadWithoutResponseLastChunk(ExecutorSupplier clientExecutorSupplier,
                                                         ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "Testing1\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetNoRequestPayload(ExecutorSupplier clientExecutorSupplier,
                                 ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "Testing1\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetEchoPayloadContentLength(ExecutorSupplier clientExecutorSupplier,
                                         ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request.transformPayloadBody(payload -> {
            payload.ignoreElements().subscribe();
            return getChunkPublisherFromStrings("hello");
        });
        request.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "hello");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetEchoPayloadChunked(ExecutorSupplier clientExecutorSupplier,
                                   ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "hello");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetRot13Payload(ExecutorSupplier clientExecutorSupplier,
                             ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ROT13).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "uryyb");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetIgnoreRequestPayload(ExecutorSupplier clientExecutorSupplier,
                                     ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
            getChunkPublisherFromStrings("hello"));
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "Testing1\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGetNoRequestPayloadNoResponsePayload(ExecutorSupplier clientExecutorSupplier,
                                                  ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_NO_CONTENT);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, NO_CONTENT, "");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testMultipleGetsNoRequestPayloadWithoutResponseLastChunk(ExecutorSupplier clientExecutorSupplier,
                                                                  ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "Testing1\n");

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "Testing2\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testMultipleGetsNoRequestPayload(ExecutorSupplier clientExecutorSupplier,
                                          ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "Testing1\n");

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "Testing2\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testMultipleGetsEchoPayloadContentLength(ExecutorSupplier clientExecutorSupplier,
                                                  ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request1.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "hello");

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request2.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "hello");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testMultipleGetsEchoPayloadChunked(ExecutorSupplier clientExecutorSupplier,
                                            ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "hello");

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
            getChunkPublisherFromStrings("hello"));
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "hello");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testMultipleGetsIgnoreRequestPayload(ExecutorSupplier clientExecutorSupplier,
                                              ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request1.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "Testing1\n");

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
            getChunkPublisherFromStrings("hello"));
        request2.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "Testing2\n");
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testHttp10CloseConnection(ExecutorSupplier clientExecutorSupplier,
                                   ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_0, OK, "Testing1\n");
        assertFalse(response.headers().contains(CONNECTION));

        assertConnectionClosed();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testHttp10KeepAliveConnection(ExecutorSupplier clientExecutorSupplier,
                                       ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        request1.headers().set("connection", "keep-alive");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_0, OK, "Testing1\n");
        assertTrue(response1.headers().contains(CONNECTION, KEEP_ALIVE));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        request2.headers().set("connection", "keep-alive");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_0, OK, "Testing2\n");
        assertTrue(response1.headers().contains(CONNECTION, KEEP_ALIVE));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testHttp11CloseConnection(ExecutorSupplier clientExecutorSupplier,
                                   ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER);
        request.headers().set("connection", "close");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, "Testing1\n");
        assertTrue(response.headers().contains(CONNECTION, CLOSE));

        assertConnectionClosed();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testHttp11KeepAliveConnection(ExecutorSupplier clientExecutorSupplier,
                                       ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "Testing1\n");
        assertFalse(response1.headers().contains(CONNECTION));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, "Testing2\n");
        assertFalse(response2.headers().contains(CONNECTION));
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGracefulShutdownWhileIdle(ExecutorSupplier clientExecutorSupplier,
                                       ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, "Testing1\n");
        assertFalse(response1.headers().contains(CONNECTION));

        // Use a very high timeout for the graceful close. It should happen quite quickly because there are no
        // active requests/responses.
        closeAsyncGracefully(serverContext(), 1000, SECONDS).toFuture().get();
        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGracefulShutdownWhileReadingPayload(ExecutorSupplier clientExecutorSupplier,
                                                 ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        ignoreTestWhen(IMMEDIATE, IMMEDIATE);

        when(publisherSupplier.apply(any())).thenReturn(publisher);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        final StreamingHttpResponse response1 = makeRequest(request1);

        serviceHandleLatch.await();
        closeAsyncGracefully(serverContext(), 1000, SECONDS).subscribe();
        publisher.onNext(getChunkFromString("Hello"));
        publisher.onComplete();

        assertResponse(response1, HTTP_1_1, OK, "Hello");
        assertFalse(response1.headers().contains(CONNECTION)); // Eventually this should be assertTrue

        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testImmediateShutdownWhileReadingPayload(ExecutorSupplier clientExecutorSupplier,
                                                  ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        when(publisherSupplier.apply(any())).thenReturn(publisher);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        makeRequest(request1);

        serverContext().closeAsync().toFuture().get();

        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testCancelGracefulShutdownWhileReadingPayloadAndThenGracefulShutdownAgain(
            ExecutorSupplier clientExecutorSupplier,
            ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        when(publisherSupplier.apply(any())).thenReturn(publisher);
        toSource(serverContext().onClose()).subscribe(completableListenerRule);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        makeRequest(request1);

        // cancelling the Completable while in the timeout cancels the forceful shutdown.
        closeAsyncGracefully(serverContext(), 1000, SECONDS).afterOnSubscribe(Cancellable::cancel).subscribe();

        assertThat(completableListenerRule.pollTerminal(10, MILLISECONDS), is(nullValue()));

        closeAsyncGracefully(serverContext(), 10, MILLISECONDS).toFuture().get();

        completableListenerRule.awaitOnComplete();

        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testCancelGracefulShutdownWhileReadingPayloadAndThenShutdown(ExecutorSupplier clientExecutorSupplier,
                                                                      ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        when(publisherSupplier.apply(any())).thenReturn(publisher);
        toSource(serverContext().onClose()).subscribe(completableListenerRule);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        makeRequest(request1);

        // cancelling the Completable while in the timeout cancels the forceful shutdown.
        closeAsyncGracefully(serverContext(), 1000, SECONDS).afterOnSubscribe(Cancellable::cancel).subscribe();

        assertThat(completableListenerRule.pollTerminal(10, MILLISECONDS), is(nullValue()));

        serverContext().closeAsync().toFuture().get();

        completableListenerRule.awaitOnComplete();

        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testGracefulShutdownTimesOutWhileReadingPayload(ExecutorSupplier clientExecutorSupplier,
                                                         ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        when(publisherSupplier.apply(any())).thenReturn(publisher);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        makeRequest(request1);

        closeAsyncGracefully(serverContext(), 500, MILLISECONDS).toFuture().get();

        assertConnectionClosed();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testImmediateCloseAfterGracefulShutdownWhileReadingPayload(ExecutorSupplier clientExecutorSupplier,
                                                                    ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        when(publisherSupplier.apply(any())).thenReturn(publisher);

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_TEST_PUBLISHER);
        makeRequest(request1);

        closeAsyncGracefully(serverContext(), 1000, SECONDS).subscribe();
        // Wait 500 millis for the "immediate" close to happen, since there are multiple threads involved.
        // If it takes any longer than that, it probably didn't work, but the graceful close would make the test pass.
        serverContext().closeAsync().toFuture().get();

        assertConnectionClosed();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testDeferCloseConnection(ExecutorSupplier clientExecutorSupplier,
                                  ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        /*
        TODO: This test is not quite as robust as it could be.
        If deferring the close is not working properly, it's possible for this test to pass, when it should fail.
        We should change the test to configure the client's RecvByteBufAllocator to allocate single-byte buffers, so
        that netty only reads one byte at a time.
         */
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_LARGE_LAST);
        request.headers().set("connection", "close");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, 1024 + 6144);
        assertTrue(response.headers().contains(CONNECTION, CLOSE));

        assertConnectionClosed();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testSynchronousError(ExecutorSupplier clientExecutorSupplier,
                              ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_THROW_ERROR);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, "");
        assertTrue(response.headers().contains(CONTENT_LENGTH, ZERO));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testSingleError(ExecutorSupplier clientExecutorSupplier,
                         ExecutorSupplier serverExecutorSupplier)
        throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_SINGLE_ERROR);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, "");
        assertTrue(response.headers().contains(CONTENT_LENGTH, ZERO));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testErrorBeforeRead(ExecutorSupplier clientExecutorSupplier,
                             ExecutorSupplier serverExecutorSupplier) throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        // Flaky test: https://github.com/apple/servicetalk/issues/245
        ignoreTestWhen(IMMEDIATE, IMMEDIATE);
        ignoreTestWhen(IMMEDIATE, CACHED);
        ignoreTestWhen(CACHED, IMMEDIATE);
        ignoreTestWhen(CACHED, CACHED);

        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ERROR_BEFORE_READ).payloadBody(
            getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));

        try {
            final StreamingHttpResponse response = makeRequest(request);

            assertEquals(OK, response.status());
            assertEquals(HTTP_1_1, response.version());

            final BlockingIterator<Buffer> httpPayloadChunks = response.payloadBody().toIterable().iterator();

            Exception e = assertThrows(Exception.class, () -> httpPayloadChunks.next());
            assertThat(e, either(instanceOf(RuntimeException.class)).or(instanceOf(ExecutionException.class)));
            // Due to a race condition, the exception cause here can vary.
            // If the socket closure is delayed slightly
            // (for example, by delaying the Publisher.error(...) on the server)
            // then the client throws ClosedChannelException. However if the socket closure happens quickly enough,
            // the client throws NativeIoException (KQueue) or IOException (NIO).
            assertThat(e.getCause(), instanceOf(IOException.class));
        } finally {
            assertConnectionClosed();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0} server={1}")
    @MethodSource("clientExecutors")
    void testErrorDuringRead(ExecutorSupplier clientExecutorSupplier,
                             ExecutorSupplier serverExecutorSupplier) throws Exception {
        setUp(clientExecutorSupplier, serverExecutorSupplier);
        ignoreTestWhen(CACHED, IMMEDIATE);
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ERROR_DURING_READ).payloadBody(
            getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        final StreamingHttpResponse response = makeRequest(request);

        assertEquals(OK, response.status());
        assertEquals(HTTP_1_1, response.version());

        final BlockingIterator<Buffer> httpPayloadChunks = response.payloadBody().toIterable().iterator();
        StringBuilder sb = new StringBuilder();
        // Due to a race condition, the exception cause here can vary.
        // If the socket closure is delayed slightly (for example, by delaying the Publisher.error(...) on the server)
        // then the client throws ClosedChannelException. However if the socket closure happens quickly enough,
        // the client throws NativeIoException (KQueue) or IOException (NIO).
        try {
            while (httpPayloadChunks.hasNext()) {
                sb.append(httpPayloadChunks.next().toString(US_ASCII));
            }
            fail("Server should close upon receiving the request");
        } catch (RuntimeException wrapped) { // BlockingIterator wraps
            assertClientTransportInboundClosed(wrapped.getCause());
        }
        assertEquals("Goodbyecruelworld!", sb.toString());
        assertConnectionClosed();
        // Client inbound channel closed - should be same exception as above
        Throwable clientThrowable = ((NettyConnectionContext) streamingHttpConnection().connectionContext())
            .transportError().toFuture().get();
        assertClientTransportInboundClosed(clientThrowable);
        // Server outbound channel force closed (reset)
        Throwable serverThrowable = capturedServiceTransportErrorRef.get().toFuture().get();
        assertThat(serverThrowable, is(DELIBERATE_EXCEPTION));
    }

    private void assertClientTransportInboundClosed(final Throwable clientThrowable) {
        if (clientThrowable instanceof ClosedChannelException) {
            assertThat(clientThrowable.getMessage(), startsWith(
                "CHANNEL_CLOSED_INBOUND(The transport backing this connection has been shutdown (read)) [id: 0x"));
        } else if (clientThrowable instanceof IOException) {
            // connection reset - unlikely, but possible due to races (no standard way to assert)
        } else {
            throw new AssertionError("Unexpected", clientThrowable);
        }
    }

    @Override
    void service(final StreamingHttpService service) {
        super.service(new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                HttpServiceContext checkCtx = ctx instanceof DelegatingHttpServiceContext ?
                    ((DelegatingHttpServiceContext) ctx).delegate() : ctx;
                // Capture for future assertions on the transport errors
                NettyConnectionContext asNCC;
                if (checkCtx instanceof NettyConnectionContext) {
                    asNCC = (NettyConnectionContext) checkCtx;
                    capturedServiceTransportErrorRef.set(asNCC.transportError());
                }
                return delegate().handle(ctx, request, responseFactory)
                    .afterOnSubscribe(c -> serviceHandleLatch.countDown());
            }
        });
    }
}
