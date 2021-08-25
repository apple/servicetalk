/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.EnforceSequentialModeRequesterFilter;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FullDuplexAndSequentialModeTest extends AbstractNettyHttpServerTest {

    private static final int CHUNK_SIZE = 1024;
    private static final int SIZE = 2 * CHUNK_SIZE;

    @Test
    void defaultFullDuplex() throws Exception {
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpConnection connection = streamingHttpConnection();
        CountDownLatch continueRequest = new CountDownLatch(1);
        StreamingHttpResponse response;
        try (InputStream payload = payload()) {
            Future<StreamingHttpResponse> responseFuture = sendRequest(connection, continueRequest, payload);
            response = responseFuture.get();    // response meta-data received before request completes
            assertResponse(response, HTTP_1_1, OK);
        }
        continueRequest.countDown();

        ExecutionException e = assertThrows(ExecutionException.class, () -> response.payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(IOException.class));
        assertThat(e.getCause().getMessage(), containsString("Stream closed"));
    }

    @Test
    void defaultFullDuplexWithDelayOfCurrentThread() throws Exception {
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpConnection connection = streamingHttpConnection();
        CountDownLatch continueRequest = new CountDownLatch(1);
        StreamingHttpResponse response;
        try (InputStream payload = payload()) {
            Future<StreamingHttpResponse> responseFuture = sendRequest(connection, continueRequest, payload);
            // Delay completion of the request payload body:
            Thread.sleep(100);
            assertThat(responseFuture.isDone(), is(true));  // response meta-data received before request completes
            continueRequest.countDown();
            response = responseFuture.get();
            assertResponse(response, HTTP_1_1, OK);
        }

        ExecutionException e = assertThrows(ExecutionException.class, () -> response.payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(IOException.class));
        assertThat(e.getCause().getMessage(), containsString("Stream closed"));
    }

    @Test
    void deferResponseUntilAfterRequestSent() throws Exception {
        clientFilterFactory(EnforceSequentialModeRequesterFilter.INSTANCE);
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpConnection connection = streamingHttpConnection();
        CountDownLatch continueRequest = new CountDownLatch(1);
        try (InputStream payload = payload()) {
            Future<StreamingHttpResponse> responseFuture = sendRequest(connection, continueRequest, payload);
            // Delay completion of the request payload body:
            Thread.sleep(100);
            assertThat(responseFuture.isDone(), is(false)); // response meta-data completes only after request is sent
            continueRequest.countDown();
            assertResponse(responseFuture.get(), HTTP_1_1, OK, SIZE);
        }
    }

    private static InputStream payload() {
        byte[] array = new byte[SIZE];
        ThreadLocalRandom.current().nextBytes(array);
        return new BufferedInputStream(new ByteArrayInputStream(array));
    }

    private static Future<StreamingHttpResponse> sendRequest(StreamingHttpConnection connection,
                                                             CountDownLatch continueRequest,
                                                             InputStream payload) {
        return connection.request(connection.post(SVC_ECHO).payloadBody(fromInputStream(payload, CHUNK_SIZE)
                .map(chunk -> {
                    try {
                        continueRequest.await();    // wait until the InputStream is closed
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throwException(ie);
                    }
                    return connection.executionContext().bufferAllocator().wrap(chunk);
                }))).toFuture();
    }
}
