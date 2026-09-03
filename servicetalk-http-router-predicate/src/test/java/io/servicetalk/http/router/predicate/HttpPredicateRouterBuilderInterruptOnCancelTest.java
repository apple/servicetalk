/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.TestHttpServiceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;

/**
 * Verifies that {@link HttpPredicateRouterBuilder#interruptBlockingServiceOnCancel(boolean)} actually reaches
 * routes registered via {@link io.servicetalk.http.router.predicate.dsl.RouteContinuation#thenRouteTo}, since
 * routes built by this router are converted to {@link StreamingHttpService} internally, before any
 * {@link io.servicetalk.http.api.HttpServerBuilder} ever sees them (so that builder's own toggle can't apply).
 */
@ExtendWith(MockitoExtension.class)
class HttpPredicateRouterBuilderInterruptOnCancelTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor()
            .setClassLevel(true);

    @Mock
    private HttpExecutionContext mockExecutionCtx;

    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private HttpServiceContext mockCtx;

    @BeforeEach
    void setup() {
        lenient().when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        mockCtx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE, reqRespFactory, mockExecutionCtx);
    }

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void routedBlockingStreamingServiceRespectsToggle(boolean interruptOnCancel) throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        BlockingStreamingHttpService blockingService = (ctx, request, response) -> {
            handleLatch.countDown();
            if (interruptOnCancel) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                } finally {
                    doneLatch.countDown();
                }
                return;
            }
            // Unrelated blocking work that has nothing to do with the (to-be-)cancelled response -- there is no
            // ServiceTalk construct here for cancellation to cooperatively wake this up through, so use a bounded
            // wait instead of sleeping forever.
            sleepLoopUnrelatedToCancellation(interrupted);
            doneLatch.countDown();
        };

        StreamingHttpService routed = new HttpPredicateRouterBuilder()
                .interruptBlockingServiceOnCancel(interruptOnCancel)
                .whenPathEquals("/")
                .thenRouteTo(blockingService)
                .buildStreaming();

        toSource(routed.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorExtension.executor()))
                .subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        cancellableRef.set(cancellable);
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                    }

                    @Override
                    public void onError(final Throwable t) {
                    }
                });
        handleLatch.await();
        Cancellable cancellable = cancellableRef.get();
        assertThat(cancellable, is(notNullValue()));
        cancellable.cancel();
        doneLatch.await();

        assertThat("interruptBlockingServiceOnCancel(" + interruptOnCancel + ") must control whether a route " +
                "registered via thenRouteTo(BlockingStreamingHttpService) is interrupted on cancel",
                interrupted.get(), is(interruptOnCancel));
    }

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void routedBlockingServiceRespectsToggle(boolean interruptOnCancel) throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        BlockingHttpService blockingService = (ctx, request, responseFactory) -> {
            handleLatch.countDown();
            if (interruptOnCancel) {
                Thread.sleep(Long.MAX_VALUE);
                return responseFactory.ok();
            }
            // BlockingHttpService (aggregated) has no cooperative construct at all to observe cancellation
            // through, so with the toggle disabled the handler simply runs to completion -- the documented
            // trade-off.
            sleepLoopUnrelatedToCancellation(interrupted);
            return responseFactory.ok();
        };

        StreamingHttpService routed = new HttpPredicateRouterBuilder()
                .interruptBlockingServiceOnCancel(interruptOnCancel)
                .whenPathEquals("/")
                .thenRouteTo(blockingService)
                .buildStreaming();

        toSource(routed.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorExtension.executor()))
                .subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        cancellableRef.set(cancellable);
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                        doneLatch.countDown();
                    }

                    @Override
                    public void onError(final Throwable t) {
                        errorRef.set(t);
                        doneLatch.countDown();
                    }
                });
        handleLatch.await();
        Cancellable cancellable = cancellableRef.get();
        assertThat(cancellable, is(notNullValue()));
        cancellable.cancel();
        doneLatch.await();

        if (interruptOnCancel) {
            assertThat(errorRef.get(), instanceOf(InterruptedException.class));
        } else {
            assertThat("interruptBlockingServiceOnCancel(false) must not interrupt a route registered via " +
                    "thenRouteTo(BlockingHttpService)", interrupted.get(), is(false));
        }
    }

    private static void sleepLoopUnrelatedToCancellation(AtomicBoolean interrupted) {
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            if (Thread.interrupted()) {
                interrupted.set(true);
            }
        }
    }
}
