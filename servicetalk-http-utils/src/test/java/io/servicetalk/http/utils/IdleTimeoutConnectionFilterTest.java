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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdleTimeoutConnectionFilterTest {

    private static final StreamingHttpRequest REQUEST = mock(StreamingHttpRequest.class);
    private static final StreamingHttpRequest REQUEST_SUCCESS = mock(StreamingHttpRequest.class);
    private static final StreamingHttpRequest REQUEST_FAIL = mock(StreamingHttpRequest.class);
    private static final StreamingHttpResponse RESPONSE = newResponse();
    private static final Throwable DELIBERATE_EXCEPTION = new RuntimeException("DELIBERATE_EXCEPTION");
    private static final long TIMEOUT_MILLIS = 60_000;

    private final TestExecutor executor = new TestExecutor();
    private final FilterableStreamingHttpConnection connection = mock(FilterableStreamingHttpConnection.class);
    private final AtomicInteger closedTimes = new AtomicInteger();

    IdleTimeoutConnectionFilterTest() {
        CompletableSource.Processor onClose = newCompletableProcessor();
        when(connection.onClose()).thenReturn(fromSource(onClose));
        when(connection.closeAsync()).thenReturn(completed().whenFinally(() -> {
            onClose.onComplete();
            closedTimes.incrementAndGet();
        }));
        HttpExecutionContext ctx = mock(HttpExecutionContext.class);
        when(ctx.executor()).thenReturn(executor);
        when(connection.executionContext()).thenReturn(ctx);

        when(connection.request(REQUEST_SUCCESS)).thenReturn(succeeded(RESPONSE));
        when(connection.request(REQUEST_FAIL)).thenReturn(failed(DELIBERATE_EXCEPTION));
    }

    private StreamingHttpRequester applyTimeout() {
        return applyTimeout(ofMillis(TIMEOUT_MILLIS));
    }

    private StreamingHttpRequester applyTimeout(Duration timeout) {
        return new IdleTimeoutConnectionFilter(timeout, executor).create(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Test
    void negativeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new IdleTimeoutConnectionFilter(ofMillis(-1)));
    }

    @Test
    void zeroTimeout() {
        applyTimeout(ZERO);
        executor.advanceTimeBy(TIMEOUT_MILLIS, MILLISECONDS);
        assertNotClosed();
    }

    @Test
    void noRequests() {
        StreamingHttpRequester requester = applyTimeout();
        executor.advanceTimeBy(TIMEOUT_MILLIS, MILLISECONDS);
        assertClosedOnce();
        assertClosedChannelException(requester);
    }

    @Test
    void closedManually() {
        applyTimeout().closeAsync().subscribe();

        executor.advanceTimeBy(TIMEOUT_MILLIS, MILLISECONDS);
        assertClosedOnce();
    }

    @Test
    void hadSuccessfulResponse() throws Exception {
        StreamingHttpRequester requester = applyTimeout();
        executor.advanceTimeByNoExecuteTasks(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertSuccessfulResponse(requester);

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertNotClosed();

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertClosedOnce();

        assertClosedChannelException(requester);
    }

    @Test
    void hadFailedResponse() {
        StreamingHttpRequester requester = applyTimeout();
        executor.advanceTimeByNoExecuteTasks(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertFailedResponse(requester);

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertNotClosed();

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertClosedOnce();

        assertClosedChannelException(requester);
    }

    @Test
    void twoConcurrentRequests() throws Exception {
        StreamingHttpRequester requester = applyTimeout();
        executor.advanceTimeByNoExecuteTasks(TIMEOUT_MILLIS / 2, MILLISECONDS);

        // Send the 1st request that doesn't receive a response:
        Processor<StreamingHttpResponse, StreamingHttpResponse> firstResponseProcessor = newSingleProcessor();
        when(connection.request(REQUEST)).thenReturn(fromSource(firstResponseProcessor));
        TestSingleSubscriber<StreamingHttpResponse> responseSubscriber = new TestSingleSubscriber<>();
        toSource(requester.request(REQUEST)).subscribe(responseSubscriber);
        assertThat(responseSubscriber.pollTerminal(1, MILLISECONDS), is(nullValue()));

        // Send the 2nd request:
        assertSuccessfulResponse(requester);

        executor.advanceTimeBy(TIMEOUT_MILLIS, MILLISECONDS);
        assertNotClosed();  // we still have the 1st "in-flight" request

        // Complete the 1st request:
        StreamingHttpResponse firstResponse = newResponse();
        firstResponseProcessor.onSuccess(firstResponse);
        assertResponse(responseSubscriber, firstResponse);

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertNotClosed();  // timeout was reset after completion of the 1st request

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertClosedOnce();

        assertClosedChannelException(requester);
    }

    @Test
    void inFlightRequest() throws Exception {
        StreamingHttpRequester requester = applyTimeout();
        executor.advanceTimeByNoExecuteTasks(TIMEOUT_MILLIS / 2, MILLISECONDS);

        Processor<StreamingHttpResponse, StreamingHttpResponse> responseProcessor = newSingleProcessor();
        when(connection.request(REQUEST)).thenReturn(fromSource(responseProcessor));
        TestSingleSubscriber<StreamingHttpResponse> responseSubscriber = new TestSingleSubscriber<>();
        toSource(requester.request(REQUEST)).subscribe(responseSubscriber);
        assertThat(responseSubscriber.pollTerminal(1, MILLISECONDS), is(nullValue()));

        executor.advanceTimeBy(TIMEOUT_MILLIS, MILLISECONDS);
        assertNotClosed();

        StreamingHttpResponse response = newResponse();
        responseProcessor.onSuccess(response);
        assertResponse(responseSubscriber, response);

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertNotClosed();

        executor.advanceTimeBy(TIMEOUT_MILLIS / 2, MILLISECONDS);
        assertClosedOnce();

        assertClosedChannelException(requester);
    }

    private void assertClosedOnce() {
        assertThat(closedTimes.get(), is(1));
    }

    private void assertNotClosed() {
        assertThat(closedTimes.get(), is(0));
    }

    private static void assertSuccessfulResponse(StreamingHttpRequester requester) throws Exception {
        TestSingleSubscriber<StreamingHttpResponse> responseSubscriber = new TestSingleSubscriber<>();
        toSource(requester.request(REQUEST_SUCCESS)).subscribe(responseSubscriber);
        assertResponse(responseSubscriber, RESPONSE);
    }

    private static void assertResponse(TestSingleSubscriber<StreamingHttpResponse> responseSubscriber,
                                       StreamingHttpResponse expectedResponse) throws Exception {
        StreamingHttpResponse response = responseSubscriber.awaitOnSuccess();
        assertThat(response, is(notNullValue()));
        assertThat(response, is(expectedResponse));
        response.payloadBody().ignoreElements().toFuture().get();
    }

    private static void assertFailedResponse(StreamingHttpRequester requester) {
        TestSingleSubscriber<StreamingHttpResponse> responseSubscriber = new TestSingleSubscriber<>();
        toSource(requester.request(REQUEST_FAIL)).subscribe(responseSubscriber);
        assertThat(responseSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private static void assertClosedChannelException(StreamingHttpRequester requester) {
        TestSingleSubscriber<StreamingHttpResponse> responseSubscriber = new TestSingleSubscriber<>();
        toSource(requester.request(REQUEST)).subscribe(responseSubscriber);
        assertThat(responseSubscriber.awaitOnError(), instanceOf(ClosedChannelException.class));
    }

    private static StreamingHttpResponse newResponse() {
        return StreamingHttpResponses.newResponse(OK, HTTP_1_1, DefaultHttpHeadersFactory.INSTANCE.newHeaders(),
                DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE);
    }
}
