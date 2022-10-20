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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.Http2ErrorCode.CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerAsciiFixLen;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilderWithConfigs;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.GracefulConnectionClosureHandlingTest.RAW_STRING_SERIALIZER;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static java.lang.Long.MAX_VALUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test the following scenario:
 *  - Client sends 3 pipelined requests on the same connection;
 *  - Server returns response meta-data -> subscribes to request payload body (to prevent auto-draining) ->
 *    emits response payload body (can be empty) -> waits until client receives the response ->
 *    drains request payload body. Processing of the next request ensures the previous one completed.
 */
class ServerPipelineControlFlowTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> requestPayloadReceived = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responsePayloadReceived = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> processing = new LinkedBlockingQueue<>();

    @ParameterizedTest(name =
            "{displayName} [{index}] serverHasOffloading={0} drainRequestPayloadBody={1} responseHasPayload={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void testBlockingStreamingHttpService(boolean serverHasOffloading, boolean drainRequestPayloadBody,
                                          boolean responseHasPayload) throws Exception {
        test(builder -> builder.listenBlockingStreamingAndAwait((ctx, request, response) -> {
            final String currProcessing = processing.peek();
            if (currProcessing != null) {
                asyncErrors.add(new AssertionError("Server started processing " + request +
                        " on thread " + Thread.currentThread().getName() +
                        " before processing of the previous request " + currProcessing + " finished. Returning 500."));
                response.status(INTERNAL_SERVER_ERROR);
                response.sendMetaData().close();
                if (!drainRequestPayloadBody) {
                    request.payloadBody().forEach(buffer -> { /* noop */ });
                }
                return;
            }
            final String current = request + " on thread " + Thread.currentThread().getName();
            processing.add(current);
            response.status(responseHasPayload ? OK : NO_CONTENT);
            try (HttpPayloadWriter<String> writer = response.sendMetaData(RAW_STRING_SERIALIZER)) {
                // Subscribe to the request payload body before response writer closes
                BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                // Consume request payload body asynchronously:
                ctx.executionContext().executor().submit(() -> {
                    waitUntilClientReceivesResponsePayload();

                    StringBuilder sb = new StringBuilder();
                    while (iterator.hasNext()) {
                        Buffer chunk = iterator.next();
                        assert chunk != null;
                        sb.append(chunk.toString(US_ASCII));
                    }
                    requestPayloadReceived.add(sb.toString());
                }).beforeOnError(asyncErrors::add).subscribe();
                if (responseHasPayload) {
                    writer.write(request.requestTarget() + "_server_content");
                }
            } catch (Exception e) {
                asyncErrors.add(e);
                throw e;
            }
            processing.remove(current);
        }), serverHasOffloading, drainRequestPayloadBody, responseHasPayload);
    }

    @ParameterizedTest(name =
            "{displayName} [{index}] serverHasOffloading={0} drainRequestPayloadBody={1} responseHasPayload={2}")
    @CsvSource(value = {"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void testStreamingHttpService(boolean serverHasOffloading, boolean drainRequestPayloadBody,
                                  boolean responseHasPayload) throws Exception {
        test(builder -> builder.listenStreamingAndAwait((ctx, request, responseFactory) -> {
            final String currProcessing = processing.peek();
            if (currProcessing != null) {
                asyncErrors.add(new AssertionError("Server started processing " + request +
                        " on thread " + Thread.currentThread().getName() +
                        " before processing of the previous request " + currProcessing + " finished. Returning 500."));
                Single<StreamingHttpResponse> response = succeeded(responseFactory.internalServerError());
                return drainRequestPayloadBody ? response :
                        response.concat(request.payloadBody().ignoreElements());
            }
            final String current = request + " on thread " + Thread.currentThread().getName();
            processing.add(current);
            return succeeded(responseFactory
                    .newResponse(responseHasPayload ? OK : NO_CONTENT)
                    .payloadBody(responseHasPayload ?
                            from(request.requestTarget() + "_server_content") : empty(),
                            RAW_STRING_SERIALIZER)
                    .transformPayloadBody(payload -> defer(() -> {
                        AtomicReference<Subscription> requestSubscription = new AtomicReference<>();
                        CompletableSource.Processor requestSubscriptionReceived = newCompletableProcessor();
                        // Subscribe to the request payload body before response payload body starts, but request
                        // items only after response payload body completes.
                        toSource(request.payloadBody()).subscribe(new Subscriber<Buffer>() {
                            private final StringBuilder sb = new StringBuilder();

                            @Override
                            public void onSubscribe(Subscription subscription) {
                                requestSubscription.set(subscription);
                                requestSubscriptionReceived.onComplete();
                            }

                            @Override
                            public void onNext(@Nullable Buffer buffer) {
                                if (buffer != null) {
                                    sb.append(buffer.toString(US_ASCII));
                                } else {
                                    asyncErrors.add(new IllegalArgumentException(
                                            "Request payload body received a null Buffer"));
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                asyncErrors.add(t);
                            }

                            @Override
                            public void onComplete() {
                                requestPayloadReceived.add(sb.toString());
                            }
                        });
                        return fromSource(requestSubscriptionReceived).concat(payload)
                                .beforeOnComplete(() -> {
                                    processing.remove(current);
                                    // Execute on a different thread to allow response payload to complete.
                                    ctx.executionContext().executor().execute(() -> {
                                        waitUntilClientReceivesResponsePayload();
                                        requestSubscription.get().request(MAX_VALUE);
                                        // Do not wait for requestPayloadReceived, NettyHttpServer should wait.
                                    });
                                });
                    }).beforeOnError(asyncErrors::add)));
        }), serverHasOffloading, drainRequestPayloadBody, responseHasPayload);
    }

    private void test(HttpServerFactory serverFactory, boolean serverHasOffloading, boolean drainRequestPayloadBody,
                      boolean responseHasPayload) throws Exception {
        try (HttpServerContext serverContext = serverFactory.create(newServerBuilder(SERVER_CTX)
                .executionStrategy(serverHasOffloading ? defaultStrategy() : offloadNone())
                .drainRequestPayloadBody(drainRequestPayloadBody));
             StreamingHttpClient client = newClientBuilderWithConfigs(serverContext, CLIENT_CTX,
                     new H1ProtocolConfigBuilder().maxPipelinedRequests(3).build())
                     .buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            Future<StreamingHttpResponse> first = requestFuture(connection, "first");
            Future<StreamingHttpResponse> second = requestFuture(connection, "second");
            Future<StreamingHttpResponse> third = requestFuture(connection, "third");

            assertResponse("first", first.get(), responseHasPayload);
            assertResponse("second", second.get(), responseHasPayload);
            assertResponse("third", third.get(), responseHasPayload);
        } catch (Throwable t) {
            for (Throwable async : asyncErrors) {
                t.addSuppressed(async);
            }
            throw t;
        }
        assertNoAsyncErrors(asyncErrors);
    }

    @Timeout(2)
    @ParameterizedTest(name = "{displayName} [{index}] protocol={0} serviceApi={1} serverHasOffloading={2}")
    @MethodSource("data")
    void testMetaDataError(HttpProtocol protocol, ServiceApi serviceApi,
                           boolean serverHasOffloading) throws Exception {
        try (HttpServerContext serverContext = serviceApi.create(newServerBuilder(SERVER_CTX, protocol)
                .executionStrategy(serverHasOffloading ? defaultStrategy() : offloadNone()));
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol).buildBlocking()) {
            IOException e = assertThrows(IOException.class, () -> {
                switch (protocol) {
                    case HTTP_1:
                        // \r\n is illegal inside header values
                        client.request(client.get("/").setHeader("some-header", "invalid\r\nvalue"));
                        break;
                    case HTTP_2:
                        // TRACE methods can not have content-length header
                        client.request(client.trace("/").setHeader(CONTENT_LENGTH, ZERO));
                        break;
                    default:
                        throw new AssertionError("Unexpected protocol: " + protocol);
                }
            });
            switch (protocol) {
                case HTTP_1:
                    if (e instanceof CloseEventObservedException) {
                        assertThat(((CloseEventObservedException) e).event(), is(CHANNEL_CLOSED_INBOUND));
                    } else {
                        assertThat(e, instanceOf(IOException.class));
                    }
                    break;
                case HTTP_2:
                    assertThat(e, instanceOf(Http2Exception.class));
                    assertThat(((Http2Exception) e).errorCode(), is(CANCEL));
                    break;
                default:
                    throw new AssertionError("Unexpected protocol: " + protocol);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0} serviceApi={1} serverHasOffloading={2}")
    @MethodSource("data")
    void testPayloadBodyError(HttpProtocol protocol, ServiceApi serviceApi,
                              boolean serverHasOffloading) throws Exception {
        assumeTrue(serviceApi != ServiceApi.BLOCKING_STREAMING || serverHasOffloading,
                "BLOCKING_STREAMING service can deadlock without offloading");
        try (HttpServerContext serverContext = serviceApi.create(newServerBuilder(SERVER_CTX, protocol)
                .executionStrategy(serverHasOffloading ? defaultStrategy() : offloadNone())
                .appendServiceFilter(new StreamingHttpServiceFilterFactory() {
                    @Override
                    public StreamingHttpServiceFilter create(StreamingHttpService service) {
                        return new StreamingHttpServiceFilter(service) {
                            @Override
                            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                                        StreamingHttpRequest request,
                                                                        StreamingHttpResponseFactory responseFactory) {
                                return delegate().handle(ctx, request.transformMessageBody(publisher -> publisher
                                        .map(item -> {
                                            throw DELIBERATE_EXCEPTION;
                                        })), responseFactory);
                            }
                        };
                    }

                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return offloadNone();
                    }
                }));
             StreamingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol).buildStreaming()) {
            Throwable e = assertThrows(Throwable.class, () -> {
                StreamingHttpResponse response = client.request(client.post("/")
                                .payloadBody(from("content"), appSerializerAsciiFixLen()))
                        .toFuture().get();
                response.payloadBody().toFuture().get();
                // Aggregated API can return 500
                throw new ExecutionException(new HttpResponseException("Response complete", response));
            });
            assertThat(e, instanceOf(ExecutionException.class));
            e = e.getCause();
            switch (protocol) {
                case HTTP_1:
                    assertThat(e, anyOf(instanceOf(CloseEventObservedException.class), instanceOf(IOException.class),
                            instanceOf(HttpResponseException.class)));
                    if (e instanceof CloseEventObservedException) {
                        assertThat(((CloseEventObservedException) e).event(), is(CHANNEL_CLOSED_INBOUND));
                    }
                    break;
                case HTTP_2:
                    assertThat(e, anyOf(instanceOf(ClosedChannelException.class), instanceOf(Http2Exception.class),
                            instanceOf(HttpResponseException.class)));
                    if (e instanceof Http2Exception) {
                        assertThat(((Http2Exception) e).errorCode(), is(CANCEL));
                    }
                    break;
                default:
                    throw new AssertionError("Unexpected protocol: " + protocol);
            }
            if (e instanceof HttpResponseException) {
                assertThat(((HttpResponseException) e).metaData().status(), is(INTERNAL_SERVER_ERROR));
            }
        }
    }

    private static List<Arguments> data() {
        List<Arguments> data = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (ServiceApi api : ServiceApi.values()) {
                data.add(Arguments.of(protocol, api, true));
                data.add(Arguments.of(protocol, api, false));
            }
        }
        return data;
    }

    private static Future<StreamingHttpResponse> requestFuture(StreamingHttpConnection connection, String name) {
        return connection.request(connection.post('/' + name)
                        .payloadBody(connection.executionContext().executor().timer(ofMillis(50))
                                .concat(from("request_content")), RAW_STRING_SERIALIZER)).toFuture();
    }

    private void assertResponse(String name, StreamingHttpResponse response, boolean responseHasPayload)
            throws Exception {
        assertThat(response.status(), is(responseHasPayload ? OK : NO_CONTENT));
        String responsePayload = response.payloadBody()
                .collect(StringBuilder::new, (sb, chunk) -> sb.append(chunk.toString(US_ASCII)))
                .toFuture().get().toString();
        assertThat(responsePayload, is(equalTo(responseHasPayload ? '/' + name + "_server_content" : "")));
        responsePayloadReceived.add(responsePayload);
        assertThat(requestPayloadReceived.take(), is(equalTo("request_content")));
    }

    private void waitUntilClientReceivesResponsePayload() {
        try {
            if (responsePayloadReceived.poll(DEFAULT_TIMEOUT_SECONDS, SECONDS) == null) {
                throw new AssertionError("Client didn't receive response payload body");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface HttpServerFactory {
        HttpServerContext create(HttpServerBuilder builder) throws Exception;
    }

    private enum ServiceApi implements HttpServerFactory {
        ASYNC_AGGREGATED {
            @Override
            public HttpServerContext create(HttpServerBuilder builder) throws Exception {
                return builder.listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(request.payloadBody())));
            }
        },
        ASYNC_STREAMING {
            @Override
            public HttpServerContext create(HttpServerBuilder builder) throws Exception {
                return builder.listenStreamingAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(request.payloadBody())));
            }
        },
        BLOCKING_AGGREGATED {
            @Override
            public HttpServerContext create(HttpServerBuilder builder) throws Exception {
                return builder.listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(request.payloadBody()));
            }
        },
        BLOCKING_STREAMING {
            @Override
            public HttpServerContext create(HttpServerBuilder builder) throws Exception {
                return builder.listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                        for (Buffer chunk : request.payloadBody()) {
                            writer.write(chunk);
                        }
                    }
                });
            }
        }
    }
}
