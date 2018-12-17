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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.jersey.resources.CancellableResources;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.core.Application;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.asFactory;
import static io.servicetalk.http.router.jersey.TestUtils.newLargePayload;
import static io.servicetalk.http.router.jersey.resources.CancellableResources.PATH;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// This test doesn't extend AbstractJerseyHttpServiceTest because we're not starting an actual HTTP server but only
// instantiates a Jersey router to have full control on the requests we pass to it, namely so we can cancel them.
public class CancellationTest {
    private static class TestCancellable implements Cancellable {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final StreamingHttpRequestResponseFactory HTTP_REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE);

    private static final CharSequence TEST_DATA = newLargePayload();

    @ClassRule
    public static final ExecutorRule EXEC = new ExecutorRule();

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    private HttpServiceContext ctx;

    @Mock
    private Executor exec;

    private CancellableResources cancellableResources;

    private StreamingHttpService jerseyRouter;

    @Before
    public void setup() {
        final ExecutionContext execCtx = mock(ExecutionContext.class);
        when(ctx.executionContext()).thenReturn(execCtx);
        when(ctx.localAddress()).thenReturn(createUnresolved("localhost", 8080));
        when(execCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(execCtx.executor()).thenReturn(exec);
        when(execCtx.ioExecutor()).thenReturn(mock(IoExecutor.class));

        cancellableResources = new CancellableResources();

        jerseyRouter = new HttpJerseyRouterBuilder()
                .routeExecutionStrategyFactory(asFactory(
                        singletonMap("test", defaultStrategy(EXEC.getExecutor()))))
                .build(new Application() {
                    @Override
                    public Set<Object> getSingletons() {
                        // Jersey logs a WARNING about this not being a provider, but it works anyway.
                        // See: https://github.com/eclipse-ee4j/jersey/issues/3700 for more context.
                        return singleton(cancellableResources);
                    }
                });
    }

    @Test
    public void cancelSuspended() throws Exception {
        final TestCancellable cancellable = new TestCancellable();
        when(exec.schedule(any(Runnable.class), eq(7L), eq(DAYS))).thenReturn(cancellable);

        testCancelResponseSingle(get("/suspended"));
        assertThat(cancellable.cancelled, is(true));
    }

    @Test
    public void cancelSingle() throws Exception {
        testCancelResponseSingle(get("/single"));
    }

    @Test
    public void cancelOffload() throws Exception {
        testCancelResponseSingle(get("/offload"));
    }

    @Test
    public void cancelOioStreams() throws Exception {
        testCancelResponsePayload(post("/oio-streams"));
        testCancelResponseSingle(post("/offload-oio-streams"));
        testCancelResponseSingle(post("/no-offloads-oio-streams"));
    }

    @Test
    public void cancelRsStreams() throws Exception {
        testCancelResponsePayload(post("/rs-streams"));
        testCancelResponsePayload(post("/rs-streams?subscribe=true"));
        testCancelResponseSingle(post("/offload-rs-streams"));
        testCancelResponseSingle(post("/offload-rs-streams?subscribe=true"));
        testCancelResponseSingle(post("/no-offloads-rs-streams"));
        testCancelResponseSingle(post("/no-offloads-rs-streams?subscribe=true"));
    }

    @Test
    public void cancelSse() throws Exception {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return EXEC.getExecutor().schedule((Runnable) args[0], (long) args[1], (TimeUnit) args[2]);
        }).when(exec).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        // Initial SSE request succeeds
        testCancelResponsePayload(get("/sse"));

        // Payload cancellation closes the SSE sink
        assertThat(cancellableResources.sseSinkClosedLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS), is(true));
    }

    private void testCancelResponsePayload(final StreamingHttpRequest req) throws Exception {
        final CompletableFuture<StreamingHttpResponse> futureResponse = new CompletableFuture<>();

        jerseyRouter.handle(ctx, req, HTTP_REQ_RES_FACTORY)
                .subscribe(futureResponse::complete)
                .cancel(); // This is a no-op because when subscribe returns the response single has been realized

        StreamingHttpResponse res = futureResponse.get();
        assertThat(res.status(), is(OK));

        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final CountDownLatch cancelledLatch = new CountDownLatch(1);

        res.payloadBody()
                .doBeforeError(errorRef::set)
                .doBeforeCancel(cancelledLatch::countDown)
                .ignoreElements().subscribe().cancel();

        cancelledLatch.await();

        assertThat(errorRef.get(), is(nullValue()));
    }

    private void testCancelResponseSingle(final StreamingHttpRequest req) throws Exception {
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final CountDownLatch cancelledLatch = new CountDownLatch(1);

        jerseyRouter.handle(ctx, req, HTTP_REQ_RES_FACTORY)
                .doBeforeError(errorRef::set)
                .doAfterCancel(cancelledLatch::countDown)
                .ignoreResult().subscribe().cancel();

        cancelledLatch.await();

        assertThat(errorRef.get(), is(nullValue()));
    }

    private static StreamingHttpRequest get(final String resourcePath) {
        return HTTP_REQ_RES_FACTORY.get(PATH + resourcePath);
    }

    private static StreamingHttpRequest post(final String resourcePath) {
        final StreamingHttpRequest req = HTTP_REQ_RES_FACTORY.post(PATH + resourcePath)
                .payloadBody(just(DEFAULT_ALLOCATOR.fromAscii(TEST_DATA)));
        req.headers().set(CONTENT_TYPE, TEXT_PLAIN);
        return req;
    }
}
