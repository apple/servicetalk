/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.JavaNetSoTimeoutHttpConnectionFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.ExecutorExtension.withTestExecutor;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavaNetSoTimeoutHttpConnectionFilterTest {

    @RegisterExtension
    static final ExecutorExtension<TestExecutor> testExecutorExtension = withTestExecutor().setClassLevel(true);

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String READ_REQUEST_DELAY_MS = "READ_REQUEST_DELAY_MS";
    private static final String RETURN_RESPONSE_DELAY_MS = "RETURN_RESPONSE_DELAY_MS";
    private static final String RESPONSE_PAYLOAD_DELAY_MS = "RESPONSE_PAYLOAD_DELAY_MS";

    private static final Duration READ_TIMEOUT_VALUE = Duration.ofMillis(CI ? 1000 : 100);
    private static final String SERVER_DELAY_VALUE = CI ? "2000" : "200";

    private static final Key<Duration> READ_TIMEOUT_KEY = newKey("READ_TIMEOUT_KEY", Duration.class);

    @Nullable
    private static ServerContext server;
    @Nullable
    private static BlockingHttpClient client;
    private static TestExecutor testExecutor;

    @BeforeAll
    static void setUp() throws Exception {
        server = newServerBuilder(SERVER_CTX).listenStreamingAndAwait((ctx, request, responseFactory) -> {
            Buffer hello = ctx.executionContext().bufferAllocator().fromAscii("Hello");

            Executor executor = ctx.executionContext().executor();
            Duration readRequestDelay = delay(request.headers().get(READ_REQUEST_DELAY_MS));
            Duration returnResponseDelay = delay(request.headers().get(RETURN_RESPONSE_DELAY_MS));
            Duration responsePayloadDelay = delay(request.headers().get(RESPONSE_PAYLOAD_DELAY_MS));

            Single<Buffer> requestBody = request.payloadBody()
                    .collect(() -> ctx.executionContext().bufferAllocator().newCompositeBuffer(),
                            CompositeBuffer::addBuffer).map(Function.identity());
            if (readRequestDelay != null) {
                requestBody = executor.timer(readRequestDelay).concat(requestBody);
            }

            return requestBody.flatMap(buffer -> {
                Publisher<Buffer> payload = responsePayloadDelay == null ? from(hello, buffer) :
                        from(hello).concat(executor.timer(responsePayloadDelay).concat(from(buffer)));

                Single<StreamingHttpResponse> responseSingle = Single.succeeded(responseFactory.ok()
                        .payloadBody(payload));
                if (returnResponseDelay != null) {
                    responseSingle = executor.timer(returnResponseDelay).concat(responseSingle);
                }
                return responseSingle;
            });
        });
        client = newClientBuilder(server, CLIENT_CTX)
                .appendConnectionFilter(new JavaNetSoTimeoutHttpConnectionFilter(
                        (metaData, timeSource) -> metaData.context().get(READ_TIMEOUT_KEY)))
                .buildBlocking();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    @BeforeEach
    void init() {
        testExecutor = testExecutorExtension.executor();
    }
    
    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0} withServerDelays={1} withZeroTimeout={2}")
    @CsvSource({
            "false,false,false",
            "false,false,true",
            "false,true,false",
            "false,true,true",
            "true,false,false",
            "true,false,true",
            "true,true,false",
            "true,true,true",
    })
    void noTimeout(boolean expectContinue, boolean withServerDelays, boolean withZeroTimeout) throws Exception {
        HttpRequest request = newRequest();
        if (expectContinue) {
            request.addHeader(EXPECT, CONTINUE);
        }
        if (withServerDelays) {
            request.addHeader(READ_REQUEST_DELAY_MS, "20")
                    .addHeader(RETURN_RESPONSE_DELAY_MS, "20")
                    .addHeader(RESPONSE_PAYLOAD_DELAY_MS, "20");
        }
        if (withZeroTimeout) {
            request.context().put(READ_TIMEOUT_KEY, Duration.ZERO);
        }
        HttpResponse response = client().request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.payloadBody().toString(US_ASCII), is(equalTo("HelloWorld")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0} withServerDelays={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void noTimeoutStreaming(boolean expectContinue, boolean withServerDelays) throws Exception {
        BlockingStreamingHttpClient client = client().asBlockingStreamingClient();
        BlockingStreamingHttpRequest request = newStreamingRequest();
        if (expectContinue) {
            request.addHeader(EXPECT, CONTINUE);
        }
        if (withServerDelays) {
            request.addHeader(READ_REQUEST_DELAY_MS, "20")
                    .addHeader(RETURN_RESPONSE_DELAY_MS, "20")
                    .addHeader(RESPONSE_PAYLOAD_DELAY_MS, "20");
        }
        StringBuilder sb = new StringBuilder();
        BlockingStreamingHttpResponse response = client.request(request);
        assertThat(response.status(), is(OK));
        for (Buffer chunk : response.payloadBody()) {
            sb.append(chunk.toString(US_ASCII));
        }
        assertThat(sb.toString(), is(equalTo("HelloWorld")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void metaDataTimeout(boolean expectContinue) {
        SocketTimeoutException e = assertThrows(SocketTimeoutException.class, () -> {
            HttpRequest request = newRequest()
                    .addHeader(RETURN_RESPONSE_DELAY_MS, SERVER_DELAY_VALUE);
            if (expectContinue) {
                request.addHeader(EXPECT, CONTINUE);
            }
            request.context().put(READ_TIMEOUT_KEY, READ_TIMEOUT_VALUE);
            client().request(request);
        });
        assertThat(e.getMessage(), endsWith("response meta-data"));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void metaDataTimeoutStreaming(boolean expectContinue) {
        SocketTimeoutException e = assertThrows(SocketTimeoutException.class, () -> {
            BlockingStreamingHttpClient client = client().asBlockingStreamingClient();
            BlockingStreamingHttpRequest request = newStreamingRequest()
                    .addHeader(RETURN_RESPONSE_DELAY_MS, SERVER_DELAY_VALUE);
            if (expectContinue) {
                request.addHeader(EXPECT, CONTINUE);
            }
            request.context().put(READ_TIMEOUT_KEY, READ_TIMEOUT_VALUE);
            client.request(request);
        });
        assertThat(e.getMessage(), endsWith("response meta-data"));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void responsePayloadTimeout(boolean expectContinue) {
        SocketTimeoutException e = assertThrows(SocketTimeoutException.class, () -> {
            HttpRequest request = newRequest()
                    .addHeader(RESPONSE_PAYLOAD_DELAY_MS, SERVER_DELAY_VALUE);
            if (expectContinue) {
                request.addHeader(EXPECT, CONTINUE);
            }
            request.context().put(READ_TIMEOUT_KEY, READ_TIMEOUT_VALUE);
            client().request(request);
        });
        assertThat(e.getMessage(), endsWith("next response payload body chunk"));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: expectContinue={0}")
    @ValueSource(booleans = {false, true})
    void responsePayloadTimeoutStreaming(boolean expectContinue) {
        SocketTimeoutException e = assertThrows(SocketTimeoutException.class, () -> {
            BlockingStreamingHttpClient client = client().asBlockingStreamingClient();
            BlockingStreamingHttpRequest request = newStreamingRequest()
                    .addHeader(RESPONSE_PAYLOAD_DELAY_MS, SERVER_DELAY_VALUE);
            if (expectContinue) {
                request.addHeader(EXPECT, CONTINUE);
            }
            request.context().put(READ_TIMEOUT_KEY, READ_TIMEOUT_VALUE);
            Iterator<Buffer> payload = client.request(request).payloadBody().iterator();
            assertThat(payload.hasNext(), is(true));
            assertThat(payload.next().toString(US_ASCII), is(equalTo("Hello")));
            payload.next();
        });
        assertThat(e.getMessage(), endsWith("next response payload body chunk"));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }

    @Test
    void continueTimeout() {
        SocketTimeoutException e = assertThrows(SocketTimeoutException.class, () -> {
            // Request payload body awaits 100 (Continue) only when streaming client is used.
            BlockingStreamingHttpClient client = client().asBlockingStreamingClient();
            BlockingStreamingHttpRequest request = newStreamingRequest()
                    .addHeader(EXPECT, CONTINUE)
                    .addHeader(READ_REQUEST_DELAY_MS, SERVER_DELAY_VALUE);
            request.context().put(READ_TIMEOUT_KEY, READ_TIMEOUT_VALUE);
            client.request(request);
        });
        assertThat(e.getMessage(), endsWith("100 (Continue) response"));
        assertThat(e.getCause(), is(nullValue()));
    }

    @Test
    void negativeTimeout() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            HttpRequest request = newRequest();
            request.context().put(READ_TIMEOUT_KEY, Duration.ofMillis(-1));
            client().request(request);
        });
        assertThat(e.getMessage(), startsWith("timeout"));
    }

    @Test
    void racingResponsesAreCleanedUp() {
        AtomicBoolean isCancelled = new AtomicBoolean();
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        Future<StreamingHttpResponse> result = applyFilter(READ_TIMEOUT_VALUE, responseSingle
                .whenCancel(() -> isCancelled.set(true))).toFuture();

        responseSingle.awaitSubscribed();
        testExecutor.advanceTimeBy(READ_TIMEOUT_VALUE.toMillis(), TimeUnit.MILLISECONDS);

        ExecutionException ex = assertThrows(ExecutionException.class, result::get);
        assertThat(ex.getCause(), is(instanceOf(SocketTimeoutException.class)));

        // the response should have been cancelled.
        assertThat(isCancelled.get(), is(true));

        // Now send a 'losing' response to simulate the race condition.
        AtomicBoolean responseDrained = new AtomicBoolean();
        StreamingHttpResponse response = responseRawWith(Publisher.<Buffer>empty()
                .afterFinally(() -> responseDrained.set(true)));
        responseSingle.onSuccess(response);
        assertThat(responseDrained.get(), is(true));
    }

    @Test
    void timerIsCancelledOnSuccessfulResponse() throws Exception {
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        Future<StreamingHttpResponse> responseFuture = applyFilter(READ_TIMEOUT_VALUE, responseSingle).toFuture();
        responseSingle.awaitSubscribed();

        assertThat(testExecutor.scheduledTasksPending(), is(1));
        StreamingHttpResponse response = responseRawWith(Publisher.empty());
        responseSingle.onSuccess(response);
        assertThat(responseFuture.get(), is(response));
        assertThat(testExecutor.scheduledTasksPending(), is(0));
    }

    @Test
    void timerLosingRaceDoesntTriggerRequestCancellation() throws Exception {
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        AtomicBoolean responseCancelled = new AtomicBoolean();
        Future<StreamingHttpResponse> responseFuture = applyFilter(READ_TIMEOUT_VALUE, responseSingle
                .whenCancel(() -> responseCancelled.set(true)), true).toFuture();
        responseSingle.awaitSubscribed();

        StreamingHttpResponse response = responseRawWith(Publisher.empty());
        responseSingle.onSuccess(response);
        assertThat(responseFuture.get(), is(response));
        // we use ignoreCancel == true for TestExecutor to simulate that timeout may race with response onSuccess
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        testExecutor.advanceTimeBy(READ_TIMEOUT_VALUE.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(testExecutor.scheduledTasksPending(), is(0));

        assertThat(responseCancelled.get(), is(false));
    }

    @Test
    void upstreamCancellationIsAlwaysPropagated() throws Exception {
        // Note that this behavior is subjective: it could be reasonable that we latch on the first result and so
        // if a response triggers before upstream cancellation, the upstream cancellation would not be propagated.
        AtomicBoolean isCancelled = new AtomicBoolean();
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        CountDownLatch responseReceived = new CountDownLatch(1);
        Cancellable cancellable = applyFilter(READ_TIMEOUT_VALUE, responseSingle)
                .whenOnSuccess(resp -> responseReceived.countDown())
                .toCompletable().subscribe();

        responseSingle.awaitSubscribed();
        responseSingle.onSubscribe(() -> isCancelled.set(true));
        responseSingle.onSuccess(responseRawWith(Publisher.empty()));
        responseReceived.await();
        cancellable.cancel();
        assertThat(isCancelled.get(), is(true));
    }

    private static Single<StreamingHttpResponse> applyFilter(Duration timeout, Single<StreamingHttpResponse> response) {
        return applyFilter(timeout, response, false);
    }

    private static Single<StreamingHttpResponse> applyFilter(Duration timeout, Single<StreamingHttpResponse> response,
                                                             boolean ignoreCancel) {
        FilterableStreamingHttpConnection connection = mock(FilterableStreamingHttpConnection.class);
        ArgumentCaptor<StreamingHttpRequest> requestCaptor = ArgumentCaptor.forClass(StreamingHttpRequest.class);
        when(connection.request(requestCaptor.capture())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                requestCaptor.getValue().messageBody().ignoreElements().toFuture().get();
                return response;
            }
        });

        Executor exec = ignoreCancel ? new DelegatingExecutor(testExecutor) {
            @Override
            public Cancellable schedule(Runnable task, long delay, TimeUnit unit) throws RejectedExecutionException {
                super.schedule(task, delay, unit);
                return Cancellable.IGNORE_CANCEL;
            }

            @Override
            public Cancellable schedule(Runnable task, Duration delay) throws RejectedExecutionException {
                super.schedule(task, delay);
                return Cancellable.IGNORE_CANCEL;
            }
        } : testExecutor;

        return new JavaNetSoTimeoutHttpConnectionFilter(timeout, exec).create(connection)
                .request(newRequest().toStreamingRequest());
    }

    private static BlockingHttpClient client() {
        assert client != null;
        return client;
    }

    private static HttpRequest newRequest() {
        return client().post("/")
                .payloadBody(client().executionContext().bufferAllocator().fromAscii("World"));
    }

    private static StreamingHttpResponse responseRawWith(Publisher<Buffer> payloadBody) {
        return newResponse(OK, HTTP_1_1, EmptyHttpHeaders.INSTANCE, DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).payloadBody(payloadBody);
    }

    private static BlockingStreamingHttpRequest newStreamingRequest() {
        final BlockingStreamingHttpClient client = client().asBlockingStreamingClient();
        return client.post("/")
                .payloadBody(Collections.singleton(client.executionContext().bufferAllocator().fromAscii("World")));
    }

    @Nullable
    private static Duration delay(@Nullable CharSequence value) {
        if (value == null) {
            return null;
        }
        return Duration.ofMillis(CharSequences.parseLong(value));
    }
}
