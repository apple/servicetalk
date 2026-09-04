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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpContextKeys.INTERRUPT_BLOCKING_SERVICE_ON_CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BlockingToStreamingServiceTest {

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
    @NullSource
    @ValueSource(booleans = {true, false})
    void cancelDuringHandle(@Nullable Boolean interruptOnCancel) throws Exception {
        boolean expectInterrupt = interruptOnCancel == null || interruptOnCancel;
        CountDownLatch handleLatch = new CountDownLatch(1);
        CountDownLatch cancelObserved = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();

        BlockingHttpService syncService = (ctx, request, responseFactory) -> {
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
            return responseFactory.ok();
        };
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService);
        StreamingHttpRequest request = reqRespFactory.get("/");
        if (interruptOnCancel != null) {
            request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, interruptOnCancel);
        }

        toSource(asyncService.handle(mockCtx, request, reqRespFactory)
                .afterCancel(cancelObserved::countDown)
                .subscribeOn(executorExtension.executor()))
                .subscribe(new NoopSubscriber(cancellableRef));
        handleLatch.await();
        Cancellable cancellable = cancellableRef.get();
        assertThat(cancellable, is(notNullValue()));
        cancellable.cancel();
        assertThat("cancellation must reach the adapter before the service thread is released, otherwise a missing "
                + "interrupt is indistinguishable from a late one", cancelObserved.await(30, SECONDS), is(true));
        cancelLatch.countDown();
        assertThat(doneLatch.await(30, SECONDS), is(true));

        assertThat("interrupt-on-cancel must follow the resolved value", interrupted.get(), is(expectInterrupt));
    }

    @Test
    void onSubscribeReceivesNonNullCancellableWhenNotInterrupting() {
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        BlockingHttpService syncService = (ctx, request, responseFactory) -> responseFactory.ok();
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService);
        StreamingHttpRequest request = reqRespFactory.get("/");
        request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, false);

        toSource(asyncService.handle(mockCtx, request, reqRespFactory))
                .subscribe(new NoopSubscriber(cancellableRef));

        Cancellable cancellable = cancellableRef.get();
        assertThat(cancellable, is(notNullValue()));
        cancellable.cancel();
    }

    @Test
    void staleInterruptFlagIsClearedWhenNotInterrupting() throws Exception {
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicBoolean interruptedOnError = new AtomicBoolean(true);
        AtomicReference<Thread> serviceThread = new AtomicReference<>();
        AtomicReference<Thread> onErrorThread = new AtomicReference<>();
        BlockingHttpService syncService = (ctx, request, responseFactory) -> {
            serviceThread.set(Thread.currentThread());
            Thread.currentThread().interrupt();
            throw new InterruptedException();
        };
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService);
        StreamingHttpRequest request = reqRespFactory.get("/");
        request.context().put(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL, false);

        toSource(asyncService.handle(mockCtx, request, reqRespFactory)
                .subscribeOn(executorExtension.executor()))
                .subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                    }

                    @Override
                    public void onError(final Throwable t) {
                        onErrorThread.set(Thread.currentThread());
                        interruptedOnError.set(Thread.currentThread().isInterrupted());
                        onErrorLatch.countDown();
                    }
                });
        assertThat(onErrorLatch.await(30, SECONDS), is(true));

        assertThat("the assertion below is only meaningful on the thread that ran the service",
                onErrorThread.get(), is(serviceThread.get()));
        assertThat("an InterruptedException from the service must not leave the flag set on the service thread",
                interruptedOnError.get(), is(false));
    }

    private static final class NoopSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {
        private final AtomicReference<Cancellable> cancellableRef;

        NoopSubscriber(final AtomicReference<Cancellable> cancellableRef) {
            this.cancellableRef = cancellableRef;
        }

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
    }
}
