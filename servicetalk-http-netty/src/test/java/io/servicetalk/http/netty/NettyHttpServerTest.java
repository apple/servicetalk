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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
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
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.IMMEDIATE;
import static io.servicetalk.http.netty.TestService.SVC_COUNTER;
import static io.servicetalk.http.netty.TestService.SVC_COUNTER_NO_LAST_CHUNK;
import static io.servicetalk.http.netty.TestService.SVC_ECHO;
import static io.servicetalk.http.netty.TestService.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestService.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestService.SVC_LARGE_LAST;
import static io.servicetalk.http.netty.TestService.SVC_NO_CONTENT;
import static io.servicetalk.http.netty.TestService.SVC_PUBLISHER_RULE;
import static io.servicetalk.http.netty.TestService.SVC_ROT13;
import static io.servicetalk.http.netty.TestService.SVC_SINGLE_ERROR;
import static io.servicetalk.http.netty.TestService.SVC_THROW_ERROR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
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
    public final PublisherRule<HttpPayloadChunk> publisherRule = new PublisherRule<>();
    @Rule
    public final MockedCompletableListenerRule completableListenerRule = new MockedCompletableListenerRule();

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
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("Testing1\n", ""));
    }

    @Test
    public void testGetNoRequestPayload() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("Testing1\n", ""));
    }

    @Test
    public void testGetEchoPayloadContentLength() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testGetEchoPayloadChunked() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("hello", ""));
    }

    @Test
    public void testGetRot13Payload() throws Exception {
        final Publisher<HttpPayloadChunk> payload = getChunkPublisherFromStrings("hello");
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ROT13, payload);
        request.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("uryyb", ""));
    }

    @Test
    public void testGetIgnoreRequestPayload() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_COUNTER,
                getChunkPublisherFromStrings("hello"));
        request.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("Testing1\n", ""));
    }

    @Test
    public void testGetNoRequestPayloadNoResponsePayload() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_NO_CONTENT);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, NO_CONTENT, singletonList(""));
    }

    @Test
    public void testMultipleGetsNoRequestPayloadWithoutResponseLastChunk() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("Testing1\n", ""));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_COUNTER_NO_LAST_CHUNK);
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, asList("Testing2\n", ""));
    }

    @Test
    public void testMultipleGetsNoRequestPayload() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("Testing1\n", ""));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, asList("Testing2\n", ""));
    }

    @Test
    public void testMultipleGetsEchoPayloadContentLength() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request1.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, singletonList("hello"));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request2.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, singletonList("hello"));
    }

    @Test
    public void testMultipleGetsEchoPayloadChunked() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request1.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("hello", ""));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_ECHO,
                getChunkPublisherFromStrings("hello"));
        request2.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, asList("hello", ""));
    }

    @Test
    public void testMultipleGetsIgnoreRequestPayload() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_COUNTER,
                getChunkPublisherFromStrings("hello"));
        request1.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("Testing1\n", ""));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_COUNTER,
                getChunkPublisherFromStrings("hello"));
        request2.getHeaders().set(CONTENT_LENGTH, "5");
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, asList("Testing2\n", ""));
    }

    @Test
    public void testHttp10CloseConnection() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(HTTP_1_0, GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_0, OK, asList("Testing1\n", ""));
        assertFalse(response.getHeaders().contains(CONNECTION));

        assertConnectionClosed();
    }

    @Test
    public void testHttp10KeepAliveConnection() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(HTTP_1_0, GET, SVC_COUNTER);
        request1.getHeaders().set("connection", "keep-alive");
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_0, OK, asList("Testing1\n", ""));
        assertTrue(response1.getHeaders().contains(CONNECTION, KEEP_ALIVE));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(HTTP_1_0, GET, SVC_COUNTER);
        request2.getHeaders().set("connection", "keep-alive");
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_0, OK, asList("Testing2\n", ""));
        assertTrue(response1.getHeaders().contains(CONNECTION, KEEP_ALIVE));
    }

    @Test
    public void testHttp11CloseConnection() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_COUNTER);
        request.getHeaders().set("connection", "close");
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, asList("Testing1\n", ""));
        assertTrue(response.getHeaders().contains(CONNECTION, CLOSE));

        assertConnectionClosed();
    }

    @Test
    public void testHttp11KeepAliveConnection() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("Testing1\n", ""));
        assertFalse(response1.getHeaders().contains(CONNECTION));

        final HttpRequest<HttpPayloadChunk> request2 = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response2 = makeRequest(request2);
        assertResponse(response2, HTTP_1_1, OK, asList("Testing2\n", ""));
        assertFalse(response2.getHeaders().contains(CONNECTION));
    }

    @Test
    public void testGracefulShutdownWhileIdle() throws Exception {
        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_COUNTER);
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);
        assertResponse(response1, HTTP_1_1, OK, asList("Testing1\n", ""));
        assertFalse(response1.getHeaders().contains(CONNECTION));

        // Use a very high timeout for the graceful close. It should happen quite quickly because there are no
        // active requests/responses.
        awaitIndefinitely(closeAsyncGracefully(getServerContext(), 1000, SECONDS));
        assertConnectionClosed();
    }

    @Test
    public void testGracefulShutdownWhileReadingPayload() throws Exception {
        ignoreTestWhen(IMMEDIATE, IMMEDIATE);

        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
        final HttpResponse<HttpPayloadChunk> response1 = makeRequest(request1);

        closeAsyncGracefully(getServerContext(), 1000, SECONDS).subscribe();
        publisherRule.sendItems(getChunkFromString("Hello"));
        publisherRule.complete();

        assertResponse(response1, HTTP_1_1, OK, asList("Hello", ""));
        assertFalse(response1.getHeaders().contains(CONNECTION)); // Eventually this should be assertTrue

        assertConnectionClosed();
    }

    @Test
    public void testImmediateShutdownWhileReadingPayload() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        awaitIndefinitely(getServerContext().closeAsync());

        assertConnectionClosed();
    }

    @Test
    public void testCancelGracefulShutdownWhileReadingPayloadAndThenGracefulShutdownAgain() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());
        MockedCompletableListenerRule onCloseListener = completableListenerRule.listen(getServerContext().onClose());

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
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

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
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

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
        makeRequest(request1);

        awaitIndefinitely(closeAsyncGracefully(getServerContext(), 500, MILLISECONDS));

        assertConnectionClosed();
    }

    @Test
    public void testImmediateCloseAfterGracefulShutdownWhileReadingPayload() throws Exception {
        when(publisherSupplier.apply(any())).thenReturn(publisherRule.getPublisher());

        final HttpRequest<HttpPayloadChunk> request1 = newRequest(GET, SVC_PUBLISHER_RULE);
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
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_LARGE_LAST);
        request.getHeaders().set("connection", "close");
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, OK, 1024 + 6144);
        assertTrue(response.getHeaders().contains(CONNECTION, CLOSE));

        assertConnectionClosed();
    }

    @Test
    public void testSynchronousError() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_THROW_ERROR);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, singletonList(""));
        assertTrue(response.getHeaders().contains(CONTENT_LENGTH, ZERO));
    }

    @Test
    public void testSingleError() throws Exception {
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_SINGLE_ERROR);
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);
        assertResponse(response, HTTP_1_1, INTERNAL_SERVER_ERROR, singletonList(""));
        assertTrue(response.getHeaders().contains(CONTENT_LENGTH, ZERO));
    }

    @Test
    public void testErrorBeforeRead() throws Exception {
        ignoreTestWhen(CACHED, IMMEDIATE);

        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ERROR_BEFORE_READ,
                getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        request.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);

        assertEquals(OK, response.getStatus());
        assertEquals(HTTP_1_1, response.getVersion());

        final BlockingIterator<HttpPayloadChunk> httpPayloadChunks = response.getPayloadBody().toIterable().iterator();

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

        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ERROR_DURING_READ,
                getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        request.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);

        assertEquals(OK, response.getStatus());
        assertEquals(HTTP_1_1, response.getVersion());

        final BlockingIterator<HttpPayloadChunk> httpPayloadChunks = response.getPayloadBody().toIterable().iterator();
        assertEquals("Goodbye", httpPayloadChunks.next().getContent().toString(US_ASCII));
        assertEquals("cruel", httpPayloadChunks.next().getContent().toString(US_ASCII));
        assertEquals("world!", httpPayloadChunks.next().getContent().toString(US_ASCII));

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
