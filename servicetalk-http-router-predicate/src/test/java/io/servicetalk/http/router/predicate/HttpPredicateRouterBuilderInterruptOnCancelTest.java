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
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
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
import static io.servicetalk.http.api.HttpContextKeys.INTERRUPT_BLOCKING_SERVICE_ON_CANCEL;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;

/**
 * The router must pass the request context through to the blocking service it routes to, so that
 * {@code INTERRUPT_BLOCKING_SERVICE_ON_CANCEL} still applies.
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
    void routedServiceRespectsRequestContext(boolean interruptOnCancel) throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        CountDownLatch cancelObserved = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();

        BlockingStreamingHttpService blockingService = (ctx, request, response) -> {
            handleLatch.countDown();
            try {
                cancelLatch.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            if (Thread.interrupted()) {
                interrupted.set(true);
            }
            doneLatch.countDown();
        };

        StreamingHttpService routed = new HttpPredicateRouterBuilder()
                .whenPathEquals("/")
                .thenRouteTo(blockingService)
                .buildStreaming();
        StreamingHttpRequest request = reqRespFactory.get("/");
        request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, interruptOnCancel);

        toSource(routed.handle(mockCtx, request, reqRespFactory)
                .afterCancel(cancelObserved::countDown)
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
        assertThat("cancellation must reach the adapter before the service thread is released, otherwise a missing "
                + "interrupt is indistinguishable from a late one", cancelObserved.await(30, SECONDS), is(true));
        cancelLatch.countDown();
        assertThat(doneLatch.await(30, SECONDS), is(true));

        assertThat("a routed blocking service must follow the request context",
                interrupted.get(), is(interruptOnCancel));
    }
}
