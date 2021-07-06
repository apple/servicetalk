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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.jersey.resources.CancellableResources;
import io.servicetalk.transport.api.IoExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.asFactory;
import static io.servicetalk.http.router.jersey.TestUtils.newLargePayload;
import static io.servicetalk.http.router.jersey.resources.CancellableResources.PATH;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// This test doesn't extend AbstractJerseyHttpServiceTest because we're not starting an actual HTTP server but only
// instantiates a Jersey router to have full control on the requests we pass to it, namely so we can cancel them.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancellationTest {
    private static class TestCancellable implements Cancellable {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final StreamingHttpRequestResponseFactory HTTP_REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    private static final CharSequence TEST_DATA = newLargePayload();

    @RegisterExtension
    final ExecutorExtension<Executor> execRule = ExecutorExtension.withCachedExecutor();

    @Mock
    private HttpServiceContext ctx;

    @Mock
    private Executor execMock;

    private CancellableResources cancellableResources;
    private StreamingHttpService jerseyRouter;

    @BeforeEach
    void setup() {
        final HttpExecutionContext execCtx = mock(HttpExecutionContext.class);
        when(ctx.executionContext()).thenReturn(execCtx);
        when(ctx.localAddress()).thenReturn(createUnresolved("localhost", 8080));
        when(execCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(execCtx.executor()).thenReturn(execMock);
        when(execCtx.ioExecutor()).thenReturn(mock(IoExecutor.class));
        when(execCtx.executionStrategy()).thenReturn(defaultStrategy(execMock));

        cancellableResources = new CancellableResources();

        jerseyRouter = new HttpJerseyRouterBuilder()
                .routeExecutionStrategyFactory(asFactory(
                        singletonMap("test", defaultStrategy(execRule.executor()))))
                .buildStreaming(new Application() {
                    @Override
                    public Set<Object> getSingletons() {
                        // Jersey logs a WARNING about this not being a provider, but it works anyway.
                        // See: https://github.com/eclipse-ee4j/jersey/issues/3700 for more context.
                        return singleton(cancellableResources);
                    }
                });
    }

    @Test
    void cancelSuspended() throws Exception {
        final TestCancellable cancellable = new TestCancellable();
        when(execMock.schedule(any(Runnable.class), eq(7L), eq(DAYS))).thenReturn(cancellable);

        testCancelResponseSingle(get("/suspended"), false);
        assertThat(cancellable.cancelled, is(true));
    }

    @Test
    void cancelSingle() throws Exception {
        testCancelResponseSingle(get("/single"));
    }

    @Test
    void cancelOffload() throws Exception {
        testCancelResponseSingle(get("/offload"));
    }

    @Test
    void cancelOioStreams() throws Exception {
        testCancelResponsePayload(post("/oio-streams"));
        testCancelResponseSingle(post("/offload-oio-streams"));
        testCancelResponseSingle(post("/no-offloads-oio-streams"));
    }

    @Test
    void cancelRsStreams() throws Exception {
        testCancelResponsePayload(post("/rs-streams"));
        testCancelResponsePayload(post("/rs-streams?subscribe=true"));
        testCancelResponseSingle(post("/offload-rs-streams"));
        testCancelResponseSingle(post("/offload-rs-streams?subscribe=true"));
        testCancelResponseSingle(post("/no-offloads-rs-streams"));
        testCancelResponseSingle(post("/no-offloads-rs-streams?subscribe=true"));
    }

    @Test
    void cancelSse() throws Exception {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return execRule.executor().schedule((Runnable) args[0], (long) args[1], (TimeUnit) args[2]);
        }).when(execMock).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        // Initial SSE request succeeds
        testCancelResponsePayload(get("/sse"));

        // Payload cancellation closes the SSE sink
        cancellableResources.sseSinkClosedLatch.await();
    }

    private void testCancelResponsePayload(final StreamingHttpRequest req) throws Exception {
        // The handler method uses OutputStream APIs which are blocking. So we need to call handle and subscribe on
        // different threads because the write operation will block on the Subscriber creating requestN demand.
        Single<StreamingHttpResponse> respSingle = execRule.executor().submit(() ->
                jerseyRouter.handle(ctx, req, HTTP_REQ_RES_FACTORY))
                .flatMap(identity());
        Future<StreamingHttpResponse> respFuture = respSingle.toFuture();

        StreamingHttpResponse res = respFuture.get();
        respFuture.cancel(true); // This is a no-op because when subscribe returns the response single has been realized
        assertThat(res.status(), is(OK));

        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final CountDownLatch cancelledLatch = new CountDownLatch(1);

        res.payloadBody()
                .beforeOnError(errorRef::set)
                .beforeCancel(cancelledLatch::countDown)
                .ignoreElements().subscribe().cancel();

        cancelledLatch.await();

        final Throwable error = errorRef.get();
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    private void testCancelResponseSingle(final StreamingHttpRequest req) throws Exception {
        testCancelResponseSingle(req, true);
    }

    private void testCancelResponseSingle(final StreamingHttpRequest req,
                                          boolean enableOffload) throws Exception {
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final CountDownLatch cancelledLatch = new CountDownLatch(1);

        // The handler method uses OutputStream APIs which are blocking. So we need to call handle and subscribe on
        // different threads because the write operation will block on the Subscriber creating requestN demand.
        if (enableOffload) {
            Single<StreamingHttpResponse> respSingle = execRule.executor().submit(() ->
                    jerseyRouter.handle(ctx, req, HTTP_REQ_RES_FACTORY)
            ).flatMap(identity())
                    .beforeOnError((err) -> {
                        // Ignore racy cancellation, it's ordered safely.
                        if (!(err instanceof IllegalStateException)) {
                            errorRef.compareAndSet(null, err);
                        }
                    })
                    .afterCancel(cancelledLatch::countDown);

            toSource(respSingle).subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    cancellable.cancel();
                }

                @Override
                public void onSuccess(@Nullable final StreamingHttpResponse result) {
                    if (result == null) {
                        errorRef.compareAndSet(null, new NullPointerException("result == null not expected."));
                        cancelledLatch.countDown();
                    } else {
                        result.messageBody().ignoreElements().afterFinally(cancelledLatch::countDown).subscribe();
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    // Ignore racy cancellation, it's ordered safely.
                    if (!(t instanceof IllegalStateException)) {
                        errorRef.compareAndSet(null, t);
                    }
                    cancelledLatch.countDown();
                }
            });
        } else {
            jerseyRouter.handle(ctx, req, HTTP_REQ_RES_FACTORY)
                    .beforeOnError(errorRef::set)
                    .afterCancel(cancelledLatch::countDown)
                    .ignoreElement().subscribe().cancel();
        }

        cancelledLatch.await();

        final Throwable error = errorRef.get();
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    private static StreamingHttpRequest get(final String resourcePath) {
        return HTTP_REQ_RES_FACTORY.get(PATH + resourcePath);
    }

    private static StreamingHttpRequest post(final String resourcePath) {
        final StreamingHttpRequest req = HTTP_REQ_RES_FACTORY.post(PATH + resourcePath)
                .payloadBody(from(DEFAULT_ALLOCATOR.fromAscii(TEST_DATA)));
        req.headers().set(CONTENT_TYPE, TEXT_PLAIN);
        return req;
    }
}
