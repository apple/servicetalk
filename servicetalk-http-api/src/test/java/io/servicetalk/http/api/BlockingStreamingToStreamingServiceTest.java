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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;
import io.servicetalk.oio.api.PayloadWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockingStreamingToStreamingServiceTest {

    private static final String X_TOTAL_LENGTH = "x-total-length";
    private static final String HELLO_WORLD = "Hello\nWorld\n";

    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();

    @Mock
    private HttpExecutionContext mockExecutionCtx;

    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private HttpServiceContext mockCtx;

    @BeforeEach
    void setup() {
        when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        mockCtx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE, reqRespFactory, mockExecutionCtx);
    }

    @Test
    void defaultResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpService syncService = (ctx, request, response) -> response.sendMetaData().close();

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(OK, response);
        assertPayloadBody("", response, false);
        assertEmptyTrailers(response);
    }

    @Test
    void customResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpService syncService = (ctx, request, response) ->
                response.status(NO_CONTENT).sendMetaData().close();

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(NO_CONTENT, response);
        assertPayloadBody("", response, false);
        assertEmptyTrailers(response);
    }

    @Test
    void receivePayloadBody() throws Exception {
        StringBuilder receivedPayload = new StringBuilder();
        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            request.payloadBody().forEach(chunk -> receivedPayload.append(chunk.toString(
                    chunk.readerIndex() + 4, chunk.readableBytes() - 4, UTF_8)));
            response.sendMetaData().close();
        };

        List<Object> response = invokeService(syncService, reqRespFactory.post("/")
                .payloadBody(from("Hello\n", "World\n"), appSerializerUtf8FixLen()));
        assertMetaData(OK, response);
        assertPayloadBody("", response, true);
        assertEmptyTrailers(response);

        assertThat(receivedPayload.toString(), is(HELLO_WORLD));
    }

    @Test
    void respondWithPayloadBody() throws Exception {
        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            try (PayloadWriter<Buffer> pw = response.sendMetaData()) {
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("Hello\n"));
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("World\n"));
            }
        };

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(OK, response);
        assertPayloadBody(HELLO_WORLD, response, false);
        assertEmptyTrailers(response);
    }

    @Test
    void echoServiceUsingPayloadWriterWithTrailers() throws Exception {
        echoService((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpPayloadWriter<Buffer> pw = response.sendMetaData()) {
                AtomicInteger totalLength = new AtomicInteger();
                request.payloadBody().forEach(chunk -> {
                    try {
                        totalLength.addAndGet(chunk.readableBytes());
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
                pw.setTrailer(X_TOTAL_LENGTH, totalLength.toString());
            }
        });
    }

    @Test
    void echoServiceUsingPayloadWriterWithSerializerWithTrailers() throws Exception {
        echoService((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpPayloadWriter<String> pw = response.sendMetaData(appSerializerUtf8FixLen())) {
                AtomicInteger totalLength = new AtomicInteger();
                request.payloadBody(appSerializerUtf8FixLen()).forEach(chunk -> {
                    try {
                        totalLength.addAndGet(chunk.length());
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
                pw.setTrailer(X_TOTAL_LENGTH, String.valueOf(addFixedLengthFramingOverhead(totalLength.get(), 2)));
            }
        });
    }

    @Test
    void echoServiceUsingInputOutputStreamWithTrailers() throws Exception {
        echoService((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpOutputStream out = response.sendMetaDataOutputStream();
                 InputStream in = request.payloadBodyInputStream()) {
                AtomicInteger totalLength = new AtomicInteger();
                int ch;
                while ((ch = in.read()) != -1) {
                    totalLength.incrementAndGet();
                    out.write(ch);
                }
                out.setTrailer(X_TOTAL_LENGTH, totalLength.toString());
            }
        });
    }

    private void echoService(BlockingStreamingHttpService syncService) throws Exception {
        List<Object> response = invokeService(syncService, reqRespFactory.post("/")
                .payloadBody(from("Hello\n", "World\n"), appSerializerUtf8FixLen()));
        assertMetaData(OK, response);
        assertHeader(TRAILER, X_TOTAL_LENGTH, response);
        assertPayloadBody(HELLO_WORLD, response, true);
        assertTrailer(X_TOTAL_LENGTH, String.valueOf(addFixedLengthFramingOverhead(HELLO_WORLD.length(), 2)), response);
    }

    @Test
    void closeAsync() throws Exception {
        lenient().when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        final AtomicBoolean closedCalled = new AtomicBoolean();
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) {
                throw new IllegalStateException("shouldn't be called!");
            }

            @Override
            public void close() {
                closedCalled.set(true);
            }
        };
        StreamingHttpService asyncService = toStreamingHttpService(syncService, strategy -> strategy).adaptor();
        asyncService.closeAsync().toFuture().get();
        assertThat(closedCalled.get(), is(true));
    }

    @Test
    void cancelBeforeSendMetaDataPropagated() throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            handleLatch.countDown();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (Throwable t) {
                throwableRef.set(t);
                onErrorLatch.countDown();
            }
        };
        StreamingHttpService asyncService = toStreamingHttpService(syncService, strategy -> strategy).adaptor();
        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                // Use subscribeOn(Executor) instead of HttpExecutionStrategy#invokeService which returns a flatten
                // Publisher<Object> to verify that cancellation of Single<StreamingHttpResponse> interrupts the thread
                // of handle method
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
        onErrorLatch.await();
        assertThat(throwableRef.get(), instanceOf(InterruptedException.class));
    }

    @Test
    void cancelAfterSendMetaDataPropagated() throws Exception {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch serviceTerminationLatch = new CountDownLatch(1);
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            response.sendMetaData();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (Throwable t) {
                throwableRef.set(t);
                onErrorLatch.countDown();
            } finally {
                serviceTerminationLatch.countDown();
            }
        };
        StreamingHttpService asyncService = toStreamingHttpService(syncService, strategy -> strategy).adaptor();
        StreamingHttpResponse asyncResponse = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                // Use subscribeOn(Executor) instead of HttpExecutionStrategy#invokeService which returns a flatten
                // Publisher<Object> to verify that cancellation of Publisher<Buffer> interrupts the thread of handle
                // method
                .subscribeOn(executorExtension.executor()).toFuture().get();
        assertMetaData(OK, asyncResponse);
        toSource(asyncResponse.payloadBody()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                cancelLatch.countDown();
            }

            @Override
            public void onNext(final Buffer s) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        cancelLatch.await();
        onErrorLatch.await();
        assertThat(throwableRef.get(), instanceOf(InterruptedException.class));
        serviceTerminationLatch.await();
    }

    @Test
    void sendMetaDataTwice() throws Exception {
        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            response.sendMetaData();
            response.sendMetaData();
        };

        try {
            invokeService(syncService, reqRespFactory.get("/"));
            fail("Payload body should complete with an error");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(IllegalStateException.class));
        }
    }

    @Test
    void modifyMetaDataAfterSend() throws Exception {
        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            response.sendMetaData();
            response.status(NO_CONTENT);
        };

        try {
            invokeService(syncService, reqRespFactory.get("/"));
            fail("Payload body should complete with an error");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(IllegalStateException.class));
            assertThat(e.getCause().getMessage(), is("Response meta-data is already sent"));
        }
    }

    @Test
    void throwBeforeSendMetaData() throws Exception {
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            throw DELIBERATE_EXCEPTION;
        };
        StreamingHttpService asyncService = toStreamingHttpService(syncService, strategy -> strategy).adaptor();
        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                // Use subscribeOn(Executor) instead of HttpExecutionStrategy#invokeService which returns a flatten
                // Publisher<Object> to verify that the Single<StreamingHttpResponse> of response meta-data terminates
                // with an error
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
                        throwableRef.set(t);
                        onErrorLatch.countDown();
                    }
                });
        onErrorLatch.await();
        assertThat(throwableRef.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void throwAfterSendMetaData() throws Exception {
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            response.sendMetaData();
            throw DELIBERATE_EXCEPTION;
        };
        StreamingHttpService asyncService = toStreamingHttpService(syncService, strategy -> strategy).adaptor();
        StreamingHttpResponse asyncResponse = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                // Use subscribeOn(Executor) instead of HttpExecutionStrategy#invokeService which returns a flatten
                // Publisher<Object> to verify that the Publisher<Buffer> of payload body terminates with an error
                .subscribeOn(executorExtension.executor()).toFuture().get();
        assertMetaData(OK, asyncResponse);
        toSource(asyncResponse.payloadBody()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
            }

            @Override
            public void onNext(final Buffer s) {
            }

            @Override
            public void onError(final Throwable t) {
                throwableRef.set(t);
                onErrorLatch.countDown();
            }

            @Override
            public void onComplete() {
            }
        });
        onErrorLatch.await();
        assertThat(throwableRef.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void throwAfterPayloadWriterClosed() {
        BlockingStreamingHttpService syncService = (ctx, request, response) -> {
            response.sendMetaData().close();
            throw DELIBERATE_EXCEPTION;
        };

        assertThat(assertThrows(ExecutionException.class, () -> invokeService(syncService, reqRespFactory.get("/")))
                        .getCause(), is(DELIBERATE_EXCEPTION));
    }

    private List<Object> invokeService(BlockingStreamingHttpService syncService,
                                       StreamingHttpRequest request) throws Exception {
        ServiceAdapterHolder holder = toStreamingHttpService(syncService, strategy -> strategy);

        Collection<Object> responseCollection =
                invokeService(holder.serviceInvocationStrategy(), executorExtension.executor(), request,
                        req -> holder.adaptor().handle(mockCtx, req, reqRespFactory)
                                .flatMapPublisher(response -> Publisher.<Object>from(response)
                                        .concat(response.messageBody())), (t, e) -> failed(t))
                .toFuture().get();

        return new ArrayList<>(responseCollection);
    }

    private static Publisher<Object> invokeService(
            HttpExecutionStrategy strategy,
            final Executor fallback, StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Publisher<Object>> service,
            final BiFunction<Throwable, Executor, Publisher<Object>> errorHandler) {
        final Executor se = strategy.executor();
        final Executor e = null == se ? fallback : se;
        if (strategy.isDataReceiveOffloaded()) {
            request = request.transformMessageBody(payload -> payload.publishOn(e));
        }
        Publisher<Object> resp;
        if (strategy.isMetadataReceiveOffloaded()) {
            final StreamingHttpRequest r = request;
            resp = e.submit(() -> service.apply(r).subscribeShareContext())
                    .onErrorReturn(cause -> errorHandler.apply(cause, e))
                    // exec.submit() returns a Single<Publisher<Object>>, so flatten the nested Publisher.
                    .flatMapPublisher(identity());
        } else {
            resp = service.apply(request);
        }
        if (strategy.isSendOffloaded()) {
            resp = resp.subscribeOn(e);
        }
        return resp;
    }

    private static void assertMetaData(HttpResponseStatus expectedStatus, HttpResponseMetaData metaData) {
        assertThat(metaData, is(notNullValue()));
        assertThat(metaData.version(), is(HTTP_1_1));
        assertThat(metaData.status(), is(expectedStatus));
    }

    private static void assertMetaData(HttpResponseStatus expectedStatus, List<Object> response) {
        HttpResponseMetaData metaData = (HttpResponseMetaData) response.get(0);
        assertThat(metaData.version(), is(HTTP_1_1));
        assertThat(metaData.status(), is(expectedStatus));
    }

    private static void assertHeader(CharSequence expectedHeader, CharSequence expectedValue, List<Object> response) {
        HttpResponseMetaData metaData = (HttpResponseMetaData) response.get(0);
        assertThat(metaData, is(notNullValue()));
        assertThat(metaData.headers().contains(expectedHeader, expectedValue), is(true));
    }

    private static void assertPayloadBody(String expectedPayloadBody, List<Object> response,
                                          boolean stripFixedLength) {
        String payloadBody;
        if (stripFixedLength) {
            StringBuilder sb = new StringBuilder();
            Buffer aggregate = DEFAULT_ALLOCATOR.newBuffer();
            int toRead = -1;
            for (Object o : response) {
                if (o instanceof Buffer) {
                    aggregate.writeBytes(((Buffer) o));
                    while (aggregate.readableBytes() >= toRead) {
                        if (toRead < 0) {
                            if (aggregate.readableBytes() >= 4) {
                                toRead = aggregate.readInt();
                            } else {
                                break;
                            }
                        }
                        if (aggregate.readableBytes() >= toRead) {
                            sb.append(aggregate.readBytes(toRead).toString(UTF_8));
                            toRead = -1;
                        }
                    }
                }
            }
            payloadBody = sb.toString();
        } else {
            payloadBody = response.stream()
                    .filter(obj -> obj instanceof Buffer)
                    .map(obj -> ((Buffer) obj).toString(UTF_8))
                    .collect(Collectors.joining());
        }
        assertThat(payloadBody, is(expectedPayloadBody));
    }

    private static void assertEmptyTrailers(List<Object> response) {
        HttpHeaders trailers = (HttpHeaders) response.get(response.size() - 1);
        assertThat(trailers, is(notNullValue()));
        assertThat(trailers.isEmpty(), is(true));
    }

    private static void assertTrailer(CharSequence expectedTrailer, CharSequence expectedValue, List<Object> response) {
        Object lastItem = response.get(response.size() - 1);
        assertThat("Unexpected item in the flattened response.", lastItem, is(instanceOf(HttpHeaders.class)));
        HttpHeaders trailers = (HttpHeaders) lastItem;
        assertThat(trailers, is(notNullValue()));
        assertThat(trailers.contains(expectedTrailer, expectedValue), is(true));
    }

    private static int addFixedLengthFramingOverhead(int length, int chunks) {
        return length == 0 ? 0 : length + Integer.BYTES * chunks;
    }
}
