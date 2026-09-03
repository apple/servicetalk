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
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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

    @Test
    void handlesRequestNormally() throws Exception {
        BlockingHttpService syncService = (ctx, request, responseFactory) ->
                responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator().fromAscii("hello"));
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService);

        StreamingHttpResponse response = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .toFuture().get();
        assertThat(response.status(), is(OK));
        assertThat(response.payloadBody().collect(StringBuilder::new,
                (sb, chunk) -> sb.append(chunk.toString(US_ASCII))).toFuture().get().toString(), is("hello"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void cancelDuringHandle(boolean interruptOnCancel) throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<StreamingHttpResponse> responseRef = new AtomicReference<>();

        BlockingHttpService syncService = (ctx, request, responseFactory) -> {
            handleLatch.countDown();
            if (interruptOnCancel) {
                // Propagates as a checked InterruptedException out of handle(), matching how the pre-existing
                // BlockingStreamingHttpService behavior surfaces cancellation-via-interrupt today.
                Thread.sleep(Long.MAX_VALUE);
                return responseFactory.ok();
            }
            // Unrelated blocking work that has nothing to do with the (to-be-)cancelled response, mirroring the
            // CountDownLatch#await() from the downstream bug report.
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
            return responseFactory.ok();
        };
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService, interruptOnCancel);
        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorExtension.executor()))
                .subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {

                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        cancellableRef.set(cancellable);
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                        responseRef.set(result);
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
            assertThat("cancellation must not interrupt the handling thread when disabled",
                    interrupted.get(), is(false));
            // Since the handler was never notified of the cancellation (BlockingHttpService has no cooperative
            // ServiceTalk construct to observe it through, unlike BlockingStreamingHttpService), it runs to
            // completion and the response still completes normally -- the documented trade-off.
            assertThat(responseRef.get().status(), is(OK));
        }
    }

    @Test
    void onSubscribeReceivesNonNullNoOpCancellableWhenDisabled() throws Exception {
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        BlockingHttpService syncService = (ctx, request, responseFactory) -> responseFactory.ok();
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService, false);

        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory))
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

        Cancellable cancellable = cancellableRef.get();
        assertThat(cancellable, is(notNullValue()));
        cancellable.cancel(); // must not throw
    }

    @Test
    void unrelatedInterruptedExceptionClearsInterruptFlagWhenDisabled() throws Exception {
        // Simulates a thread interrupted for a reason unrelated to response cancellation (e.g. executor shutdown)
        // while interruptOnCancel is disabled, so there is no ThreadInterruptingCancellable to defensively clear
        // the flag the way it always would have prior to this toggle existing.
        BlockingHttpService syncService = (ctx, request, responseFactory) -> {
            Thread.currentThread().interrupt();
            throw new InterruptedException("unrelated to cancellation");
        };
        StreamingHttpService asyncService = toStreamingHttpService(offloadNone(), syncService, false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory))
                .subscribe(new SingleSource.Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                    }

                    @Override
                    public void onError(final Throwable t) {
                        errorRef.set(t);
                    }
                });

        assertThat(errorRef.get(), instanceOf(InterruptedException.class));
        assertThat("a stale interrupt flag must not leak back to a (likely pooled) thread just because " +
                        "interruptOnCancel is disabled", Thread.currentThread().isInterrupted(), is(false));
    }
}
