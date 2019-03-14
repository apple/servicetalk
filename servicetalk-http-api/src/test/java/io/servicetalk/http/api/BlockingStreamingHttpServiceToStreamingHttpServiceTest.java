/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.ExecutorRule.newRule;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BlockingStreamingHttpServiceToStreamingHttpServiceTest {

    private static final String X_TOTAL_LENGTH = "x-total-length";
    private static final String HELLO_WORLD = "Hello\nWorld\n";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExecutorRule<Executor> executorRule = newRule();

    @Mock
    private ExecutionContext mockExecutionCtx;

    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE);
    private HttpServiceContext mockCtx;

    @Before
    public void setup() {
        when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);

        mockCtx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE, reqRespFactory, mockExecutionCtx);
    }

    @Test
    public void defaultResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.sendMetaData().close();
            }
        };

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(OK, response);
        assertPayloadBody("", response);
        assertEmptyTrailers(response);
    }

    @Test
    public void customResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.status(NO_CONTENT).sendMetaData().close();
            }
        };

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(NO_CONTENT, response);
        assertPayloadBody("", response);
        assertEmptyTrailers(response);
    }

    @Test
    public void receivePayloadBody() throws Exception {
        StringBuilder receivedPayload = new StringBuilder();
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                request.payloadBody().forEach(chunk -> receivedPayload.append(chunk.toString(US_ASCII)));
                response.sendMetaData().close();
            }
        };

        List<Object> response = invokeService(syncService, reqRespFactory.post("/")
                .payloadBody(from("Hello\n", "World\n"), textSerializer()));
        assertMetaData(OK, response);
        assertPayloadBody("", response);
        assertEmptyTrailers(response);

        assertEquals(HELLO_WORLD, receivedPayload.toString());
    }

    @Test
    public void respondWithPayloadBody() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                try (PayloadWriter<Buffer> pw = response.sendMetaData()) {
                    pw.write(ctx.executionContext().bufferAllocator().fromAscii("Hello\n"));
                    pw.write(ctx.executionContext().bufferAllocator().fromAscii("World\n"));
                }
            }
        };

        List<Object> response = invokeService(syncService, reqRespFactory.get("/"));
        assertMetaData(OK, response);
        assertPayloadBody(HELLO_WORLD, response);
        assertEmptyTrailers(response);
    }

    @Test
    public void echoServiceUsingPayloadWriterWithTrailers() throws Exception {
        echoService(new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
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
            }
        });
    }

    @Test
    public void echoServiceUsingPayloadWriterWithSerializerWithTrailers() throws Exception {
        echoService(new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.setHeader(TRAILER, X_TOTAL_LENGTH);
                try (HttpPayloadWriter<String> pw = response.sendMetaData(textSerializer())) {
                    AtomicInteger totalLength = new AtomicInteger();
                    request.payloadBody(textDeserializer()).forEach(chunk -> {
                        try {
                            totalLength.addAndGet(chunk.length());
                            pw.write(chunk);
                        } catch (IOException e) {
                            throwException(e);
                        }
                    });
                    pw.setTrailer(X_TOTAL_LENGTH, totalLength.toString());
                }
            }
        });
    }

    @Test
    public void echoServiceUsingInputOutputStreamWithTrailers() throws Exception {
        echoService(new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
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
            }
        });
    }

    private void echoService(BlockingStreamingHttpService syncService) throws Exception {
        List<Object> response = invokeService(syncService, reqRespFactory.post("/")
                .payloadBody(from("Hello\n", "World\n"), textSerializer()));
        assertMetaData(OK, response);
        assertHeader(TRAILER, X_TOTAL_LENGTH, response);
        assertPayloadBody(HELLO_WORLD, response);
        assertTrailer(X_TOTAL_LENGTH, String.valueOf(HELLO_WORLD.length()), response);
    }

    @Test
    public void closeAsync() throws Exception {
        final AtomicBoolean closedCalled = new AtomicBoolean();
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                throw new IllegalStateException("shouldn't be called!");
            }

            @Override
            public void close() {
                closedCalled.set(true);
            }
        };
        StreamingHttpService asyncService = syncService.asStreamingService();
        asyncService.closeAsync().toFuture().get();
        assertTrue(closedCalled.get());
    }

    @Test
    public void cancelBeforeSendMetaDataPropagated() throws Exception {
        CountDownLatch handleLatch = new CountDownLatch(1);
        AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
        CountDownLatch onErrorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                handleLatch.countDown();
                Thread.sleep(10000);
            }
        };
        StreamingHttpService asyncService = syncService.asStreamingService();
        toSource(asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorRule.executor()))
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
                        throwableRef.set(t);
                        onErrorLatch.countDown();
                    }
                });
        handleLatch.await();
        assertNotNull(cancellableRef.get());
        cancellableRef.get().cancel();
        onErrorLatch.await();
        assertTrue(throwableRef.get() instanceof InterruptedException);
    }

    @Test
    public void cancelAfterSendMetaDataPropagated() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.sendMetaData();
                Thread.sleep(10000);
            }
        };
        StreamingHttpService asyncService = syncService.asStreamingService();
        StreamingHttpResponse asyncResponse = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorRule.executor()).toFuture().get();
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        toSource(asyncResponse.payloadBody()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                latch.countDown();
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
        latch.await();
    }

    @Test
    public void sendMetaDataTwice() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.sendMetaData();
                response.sendMetaData();
            }
        };
        StreamingHttpService asyncService = syncService.asStreamingService();
        StreamingHttpResponse asyncResponse = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorRule.executor()).toFuture().get();
        try {
            assertEquals("", asyncResponse.payloadBody()
                    .reduce(() -> "", (acc, next) -> acc + next.toString(US_ASCII)).toFuture().get());
            fail("Payload body should complete with an error");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void modifyMetaDataAfterSend() throws Exception {
        BlockingStreamingHttpService syncService = new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                response.sendMetaData();
                response.status(NO_CONTENT);
            }
        };
        StreamingHttpService asyncService = syncService.asStreamingService();
        StreamingHttpResponse asyncResponse = asyncService.handle(mockCtx, reqRespFactory.get("/"), reqRespFactory)
                .subscribeOn(executorRule.executor()).toFuture().get();
        try {
            assertEquals("", asyncResponse.payloadBody()
                    .reduce(() -> "", (acc, next) -> acc + next.toString(US_ASCII)).toFuture().get());
            fail("Payload body should complete with an error");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertEquals("Response meta-data is already sent", e.getCause().getMessage());
        }
    }

    private List<Object> invokeService(BlockingStreamingHttpService syncService,
                                       StreamingHttpRequest request) throws Exception {
        StreamingHttpService asyncService = syncService.asStreamingService();

        Collection<Object> responseCollection = asyncService.executionStrategy().invokeService(executorRule.executor(),
                request, req -> asyncService.handle(mockCtx, req, reqRespFactory), (t, e) -> error(t))
                .toFuture().get();

        return new ArrayList<>(responseCollection);
    }

    private static void assertMetaData(HttpResponseStatus expectedStatus, List<Object> response) {
        HttpResponseMetaData metaData = (HttpResponseMetaData) response.get(0);
        assertEquals(HTTP_1_1, metaData.version());
        assertEquals(expectedStatus, metaData.status());
    }

    private static void assertHeader(CharSequence expectedHeader, CharSequence expectedValue, List<Object> response) {
        HttpResponseMetaData metaData = (HttpResponseMetaData) response.get(0);
        assertNotNull(metaData);
        assertTrue(metaData.headers().contains(expectedHeader, expectedValue));
    }

    private static void assertPayloadBody(String expectedPayloadBody, List<Object> response) {
        String payloadBody = response.stream()
                .filter(obj -> obj instanceof Buffer)
                .map(obj -> ((Buffer) obj).toString(US_ASCII))
                .collect(Collectors.joining());
        assertEquals(expectedPayloadBody, payloadBody);
    }

    private static void assertEmptyTrailers(List<Object> response) {
        HttpHeaders trailers = (HttpHeaders) response.get(response.size() - 1);
        assertNotNull(trailers);
        assertTrue(trailers.isEmpty());
    }

    private static void assertTrailer(CharSequence expectedTrailer, CharSequence expectedValue, List<Object> response) {
        HttpHeaders trailers = (HttpHeaders) response.get(response.size() - 1);
        assertNotNull(trailers);
        assertTrue(trailers.contains(expectedTrailer, expectedValue));
    }
}
