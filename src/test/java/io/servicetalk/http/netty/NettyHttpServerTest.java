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

import io.servicetalk.concurrent.api.BlockingIterator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpPayloadChunks;
import io.servicetalk.http.api.HttpProtocolVersions;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatuses;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.NettyIoExecutors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
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
import static io.servicetalk.http.netty.TestService.SVC_COUNTER;
import static io.servicetalk.http.netty.TestService.SVC_COUNTER_NO_LAST_CHUNK;
import static io.servicetalk.http.netty.TestService.SVC_ECHO;
import static io.servicetalk.http.netty.TestService.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestService.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestService.SVC_LARGE_LAST;
import static io.servicetalk.http.netty.TestService.SVC_NO_CONTENT;
import static io.servicetalk.http.netty.TestService.SVC_ROT13;
import static io.servicetalk.http.netty.TestService.SVC_SINGLE_ERROR;
import static io.servicetalk.http.netty.TestService.SVC_THROW_ERROR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class NettyHttpServerTest extends AbstractNettyHttpServerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerTest.class);

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private static IoExecutor ioExecutor;
    private final Executor executor;

    private HttpConnection httpConnection;

    public NettyHttpServerTest(final Executor executor) {
        this.executor = executor;
    }

    @BeforeClass
    public static void createClientIoExecutor() {
        ioExecutor = NettyIoExecutors.createIoExecutor(2, new IoThreadFactory("client-io-executor"));
    }

    @Parameters
    public static Collection<Executor> clientExecutors() {
        // TODO: Using immediate() here is required until a deadlock in the client is fixed.
        return Arrays.asList(Executors.immediate()/*,
                Executors.newCachedThreadExecutor(new DefaultThreadFactory("client-executor", true, NORM_PRIORITY))*/);
    }

    @Before
    public void beforeTest() throws Exception {
        httpConnection = awaitIndefinitelyNonNull(new DefaultHttpConnectionBuilder<>()
                .build(new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor, executor),
                        getServerSocketAddress()));
    }

    @After
    public void afterTest() throws Exception {
        try {
            awaitIndefinitely(httpConnection.closeAsync());
        } finally {
            awaitIndefinitely(executor.closeAsync());
        }
    }

    @AfterClass
    public static void shutdownClientIoExecutor() throws Exception {
        awaitIndefinitely(ioExecutor.closeAsync());
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
        final HttpRequest<HttpPayloadChunk> request = newRequest(GET, SVC_ERROR_BEFORE_READ,
                getChunkPublisherFromStrings("Goodbye", "cruel", "world!"));
        request.getHeaders().set(TRANSFER_ENCODING, CHUNKED); // TODO: Eventually, this won't be necessary.
        final HttpResponse<HttpPayloadChunk> response = makeRequest(request);

        assertEquals(OK, response.getStatus());
        assertEquals(HTTP_1_1, response.getVersion());

        final BlockingIterator<HttpPayloadChunk> httpPayloadChunks = response.getPayloadBody().toIterable().iterator();

        thrown.expect(RuntimeException.class);
        thrown.expectCause(instanceOf(ClosedChannelException.class));
        try {
            httpPayloadChunks.next();
        } finally {
            assertConnectionClosed();
        }
    }

    @Test
    public void testErrorDuringRead() throws Exception {
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
        thrown.expectCause(instanceOf(ClosedChannelException.class));
        try {
            httpPayloadChunks.next();
        } finally {
            assertConnectionClosed();
        }
    }

    private HttpResponse<HttpPayloadChunk> makeRequest(final HttpRequest<HttpPayloadChunk> request) throws Exception {
        final Single<HttpResponse<HttpPayloadChunk>> responseSingle = httpConnection.request(request);
        return awaitIndefinitelyNonNull(responseSingle);
    }

    private void assertResponse(final HttpResponse<HttpPayloadChunk> response, final HttpProtocolVersions version,
                                final HttpResponseStatuses status, final int expectedSize)
            throws ExecutionException, InterruptedException {
        assertEquals(status, response.getStatus());
        assertEquals(version, response.getVersion());

        final int size = awaitIndefinitelyNonNull(
                response.getPayloadBody().reduce(() -> 0, (is, c) -> is + c.getContent().getReadableBytes()));
        assertEquals(expectedSize, size);
    }

    private void assertResponse(final HttpResponse<HttpPayloadChunk> response, final HttpProtocolVersions version,
                                final HttpResponseStatuses status, final List<String> expectedPayloadChunksAsStrings)
            throws ExecutionException, InterruptedException {
        assertEquals(status, response.getStatus());
        assertEquals(version, response.getVersion());
        final List<String> bodyAsListOfStrings = getBodyAsListOfStrings(response);
        assertEquals(expectedPayloadChunksAsStrings, bodyAsListOfStrings);
        assertEquals(expectedPayloadChunksAsStrings.size(), bodyAsListOfStrings.size());
    }

    private Publisher<HttpPayloadChunk> getChunkPublisherFromStrings(final String... texts) {
        final List<HttpPayloadChunk> chunks = new ArrayList<>(texts.length);
        final int end = texts.length - 1;
        for (int i = 0; i < end; ++i) {
            chunks.add(HttpPayloadChunks.newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii(texts[i])));
        }
        chunks.add(HttpPayloadChunks.newLastPayloadChunk(DEFAULT_ALLOCATOR.fromAscii(texts[end]), EmptyHttpHeaders.INSTANCE));
        return Publisher.from(chunks);
    }

    private static List<String> getBodyAsListOfStrings(final HttpResponse<HttpPayloadChunk> response)
            throws ExecutionException, InterruptedException {
        return awaitIndefinitelyNonNull(response.getPayloadBody()
                .reduce(ArrayList::new, (ArrayList<String> list, HttpPayloadChunk payloadChunk) -> {
                    list.add(payloadChunk.getContent().toString(US_ASCII));
                    return list;
                }));
    }

    private void assertConnectionClosed() throws InterruptedException, ExecutionException, TimeoutException {
        await(httpConnection.onClose(), 1000, TimeUnit.MILLISECONDS);
    }
}
