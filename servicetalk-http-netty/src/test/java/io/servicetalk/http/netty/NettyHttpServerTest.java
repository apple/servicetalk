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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.IMMEDIATE;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_COUNTER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_COUNTER_NO_LAST_CHUNK;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_LARGE_LAST;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NO_CONTENT;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_PUBLISHER_RULE;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ROT13;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_SINGLE_ERROR;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class NettyHttpServerTest extends AbstractNettyHttpServerTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Rule
    public final PublisherRule<Buffer> publisherRule = new PublisherRule<>();
    @Rule
    public final MockedCompletableListenerRule completableListenerRule = new MockedCompletableListenerRule();
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE);

    public NettyHttpServerTest(final ExecutorSupplier clientExecutorSupplier,
                               final ExecutorSupplier serverExecutorSupplier) {
        super(clientExecutorSupplier, serverExecutorSupplier);
    }

    @Parameterized.Parameters(name = "client={0} server={1}")
    public static Collection<ExecutorSupplier[]> clientExecutors() {
        return asList(
                new ExecutorSupplier[]{IMMEDIATE, IMMEDIATE},
                new ExecutorSupplier[]{IMMEDIATE, CACHED},
                new ExecutorSupplier[]{CACHED, IMMEDIATE},
                new ExecutorSupplier[]{CACHED, CACHED}
        );
    }

    @Test
    public void testGetNoRequestPayloadWithoutResponseLastChunk() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("Testing1\n"));
    }

    @Test
    public void testGetNoRequestPayload() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("Testing1\n"));
    }

    @Test
    public void testGetEchoPayloadContentLength() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request.transformPayloadBody(payload -> {
            payload.ignoreElements().subscribe();
            return getChunkPublisherFromStrings("hello");
        });
        request.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testGetEchoPayloadChunked() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testGetRot13Payload() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ROT13).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("uryyb"));
    }

    @Test
    public void testGetIgnoreRequestPayload() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("Testing1\n"));
    }

    @Test
    public void testGetNoRequestPayloadNoResponsePayload() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_NO_CONTENT);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, NO_CONTENT, emptyList());
    }

    @Test
    public void testMultipleGetsNoRequestPayloadWithoutResponseLastChunk() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("Testing1\n"));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("Testing2\n"));
    }

    @Test
    public void testMultipleGetsNoRequestPayload() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("Testing1\n"));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("Testing2\n"));
    }

    @Test
    public void testMultipleGetsEchoPayloadContentLength() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request1.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("hello"));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request2.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testMultipleGetsEchoPayloadChunked() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request1.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("hello"));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_ECHO).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request2.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testMultipleGetsIgnoreRequestPayload() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request1.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("Testing1\n"));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER).payloadBody(
                getChunkPublisherFromStrings("hello"));
        request2.headers().set(CONTENT_LENGTH, "5");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("Testing2\n"));
    }

    @Test
    public void testHttp10CloseConnection() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_0, OK, singletonList("Testing1\n"));
        assertFalse(response.headers().contains(CONNECTION));

        assertConnectionClosed();
    }

    @Test
    public void testHttp10KeepAliveConnection() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        request1.headers().set("connection", "keep-alive");
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_0, OK, singletonList("Testing1\n"));
        assertTrue(response1.headers().contains(CONNECTION, KEEP_ALIVE));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER).version(HTTP_1_0);
        request2.headers().set("connection", "keep-alive");
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_0, OK, singletonList("Testing2\n"));
        assertTrue(response1.headers().contains(CONNECTION, KEEP_ALIVE));
    }

    @Test
    public void testHttp11CloseConnection() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_COUNTER);
        request.headers().set("connection", "close");
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("Testing1\n"));
        assertTrue(response.headers().contains(CONNECTION, CLOSE));

        assertConnectionClosed();
    }

    @Test
    public void testHttp11KeepAliveConnection() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("Testing1\n"));
        assertFalse(response1.headers().contains(CONNECTION));

        final StreamingHttpRequest request2 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("Testing2\n"));
        assertFalse(response2.headers().contains(CONNECTION));
    }

    @Test
    public void testGracefulShutdownWhileIdle() throws Exception {
        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_COUNTER);
        final StreamingHttpResponse response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("Testing1\n"));
        assertFalse(response1.headers().contains(CONNECTION));

        // Use a very high timeout for the graceful close. It should happen quite quickly because there are no
        // active requests/responses.
        awaitIndefinitely(closeAsyncGracefully(getServerContext(), 1000, SECONDS));
        assertConnectionClosed();
    }

    @Test
    public void testGracefulShutdownWhileReadingPayload() throws Exception {
        ignoreTestWhen(IMMEDIATE, IMMEDIATE);

        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        final StreamingHttpResponse response1 = makeRequest(request1);

        closeAsyncGracefully(getServerContext(), 1000, SECONDS).subscribe();
        publisherRule.sendItems(getChunkFromString("Hello"));
        publisherRule.complete();

        assertResponse(response1, HTTP_1_1, OK, singletonList("Hello"));
        assertFalse(response1.headers().contains(CONNECTION)); // Eventually this should be assertTrue

        assertConnectionClosed();
    }

    @Test
    public void testImmediateShutdownWhileReadingPayload() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        awaitIndefinitely(getServerContext().closeAsync());

        assertConnectionClosed();
    }

    @Test
    public void testCancelGracefulShutdownWhileReadingPayloadAndThenGracefulShutdownAgain() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());
        MockedCompletableListenerRule onCloseListener = completableListenerRule.listen(getServerContext().onClose());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        // cancelling the Completable while in the timeout cancels the forceful shutdown.
        closeAsyncGracefully(getServerContext(), 1000, SECONDS).doAfterSubscribe(Cancellable::cancel).subscribe();

        onCloseListener.verifyNoEmissions();

        awaitIndefinitely(closeAsyncGracefully(getServerContext(), 10, MILLISECONDS));

        onCloseListener.verifyCompletion();

        assertConnectionClosed();
    }

    @Test
    public void testCancelGracefulShutdownWhileReadingPayloadAndThenShutdown() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());
        MockedCompletableListenerRule onCloseListener = completableListenerRule.listen(getServerContext().onClose());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        // cancelling the Completable while in the timeout cancels the forceful shutdown.
        closeAsyncGracefully(getServerContext(), 1000, SECONDS).doAfterSubscribe(Cancellable::cancel).subscribe();

        onCloseListener.verifyNoEmissions();

        awaitIndefinitely(getServerContext().closeAsync());

        onCloseListener.verifyCompletion();

        assertConnectionClosed();
    }

    @Test
    public void testGracefulShutdownTimesOutWhileReadingPayload() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        awaitIndefinitely(closeAsyncGracefully(getServerContext(), 500, MILLISECONDS));

        assertConnectionClosed();
    }

    @Test
    public void testImmediateCloseAfterGracefulShutdownWhileReadingPayload() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final StreamingHttpRequest request1 = reqRespFactory.newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        closeAsyncGracefully(getServerContext(), 1000, SECONDS).subscribe();
        // Wait 500 millis for the "immediate" close to happen, since there are multiple threads involved.
        // If it takes any longer than that, it probably didn't work, but the graceful close would make the test pass.
        awaitIndefinitely(getServerContext().closeAsync());

        assertConnectionClosed();
    }

    @Test
    public void testDeferCloseConnection() throws Exception {
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

    @Test
    public void testSynchronousError() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_THROW_ERROR);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, emptyList());
        assertTrue(response.headers().contains(CONTENT_LENGTH, ZERO));
    }

    @Test
    public void testSingleError() throws Exception {
        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_SINGLE_ERROR);
        final StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, emptyList());
        assertTrue(response.headers().contains(CONTENT_LENGTH, ZERO));
    }

    @Test
    public void testErrorBeforeRead() throws Exception {
        ignoreTestWhen(IMMEDIATE, IMMEDIATE);
        ignoreTestWhen(IMMEDIATE, CACHED);
        ignoreTestWhen(CACHED, IMMEDIATE);
        ignoreTestWhen(CACHED, CACHED);

        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ERROR_BEFORE_READ).payloadBody(
                getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        request.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response = makeRequest(request);

        assertEquals(OK, response.status());
        assertEquals(HTTP_1_1, response.version());

        final BlockingIterator<Buffer> httpPayloadChunks = response.payloadBody().toIterable().iterator();

        thrown.expect(RuntimeException.class);
        // Due to a race condition, the exception cause here can vary.
        // If the socket closure is delayed slightly (for example, by delaying the Publisher.error(...) on the server)
        // then the client throws ClosedChannelException. However if the socket closure happens quickly enough,
        // the client throws NativeIoException (KQueue) or IOException (NIO).
        thrown.expectCause(instanceOf(IOException.class));
        try {
            httpPayloadChunks.next();
        } finally {
            assertConnectionClosed();
        }
    }

    @Test
    public void testErrorDuringRead() throws Exception {
        ignoreTestWhen(CACHED, IMMEDIATE);

        final StreamingHttpRequest request = reqRespFactory.newRequest(GET, SVC_ERROR_DURING_READ).payloadBody(
                getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        request.headers().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final StreamingHttpResponse response = makeRequest(request);

        assertEquals(OK, response.status());
        assertEquals(HTTP_1_1, response.version());

        final BlockingIterator<Buffer> httpPayloadChunks = response.payloadBody().toIterable().iterator();
        assertEquals("Goodbye", httpPayloadChunks.next().toString(US_ASCII));
        assertEquals("cruel", httpPayloadChunks.next().toString(US_ASCII));
        assertEquals("world!", httpPayloadChunks.next().toString(US_ASCII));

        thrown.expect(RuntimeException.class);
        // Due to a race condition, the exception cause here can vary.
        // If the socket closure is delayed slightly (for example, by delaying the Publisher.error(...) on the server)
        // then the client throws ClosedChannelException. However if the socket closure happens quickly enough,
        // the client throws NativeIoException (KQueue) or IOException (NIO).
        thrown.expectCause(instanceOf(IOException.class));
        try {
            httpPayloadChunks.next();
        } finally {
            assertConnectionClosed();
        }
    }
}
