/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpExchangeObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpRequestObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpResponseObserver;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ConnectionInfo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpTransportObserverTest.await;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NEVER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NO_CONTENT;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_SINGLE_ERROR;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HttpLifecycleObserverTest extends AbstractNettyHttpServerTest {

    private static final Buffer CONTENT = DEFAULT_RO_ALLOCATOR.fromAscii("content");

    @Mock
    private HttpLifecycleObserver clientLifecycleObserver;
    @Mock
    private HttpExchangeObserver clientExchangeObserver;
    @Mock
    private HttpRequestObserver clientRequestObserver;
    @Mock
    private HttpResponseObserver clientResponseObserver;
    private InOrder clientInOrder;
    private InOrder clientRequestInOrder;

    @Mock
    private HttpLifecycleObserver serverLifecycleObserver;
    @Mock
    private HttpExchangeObserver serverExchangeObserver;
    @Mock
    private HttpRequestObserver serverRequestObserver;
    @Mock
    private HttpResponseObserver serverResponseObserver;
    private InOrder serverInOrder;
    private InOrder serverRequestInOrder;

    private void setUp(HttpProtocol protocol) {
        protocol(protocol.config);

        when(clientLifecycleObserver.onNewExchange()).thenReturn(clientExchangeObserver);
        when(clientExchangeObserver.onRequest(any())).thenReturn(clientRequestObserver);
        when(clientExchangeObserver.onResponse(any())).thenReturn(clientResponseObserver);
        clientInOrder = inOrder(clientLifecycleObserver, clientExchangeObserver, clientResponseObserver);
        clientRequestInOrder = inOrder(clientRequestObserver);

        when(serverLifecycleObserver.onNewExchange()).thenReturn(serverExchangeObserver);
        when(serverExchangeObserver.onRequest(any())).thenReturn(serverRequestObserver);
        when(serverExchangeObserver.onResponse(any())).thenReturn(serverResponseObserver);
        serverInOrder = inOrder(serverLifecycleObserver, serverExchangeObserver,
                serverRequestObserver, serverResponseObserver);
        serverRequestInOrder = inOrder(serverRequestObserver);

        lifecycleObserver(clientLifecycleObserver, serverLifecycleObserver);
        setUp(CACHED, CACHED_SERVER);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testCompleteEmptyMessageBody(HttpProtocol protocol) throws Exception {
        setUp(protocol);
        makeRequestAndAssertResponse(SVC_NO_CONTENT, protocol, NO_CONTENT, 0);

        verifyObservers(true, clientLifecycleObserver, clientExchangeObserver, clientRequestObserver,
                clientResponseObserver, clientInOrder, clientRequestInOrder, false);
        verifyObservers(false, serverLifecycleObserver, serverExchangeObserver, serverRequestObserver,
                serverResponseObserver, serverInOrder, serverRequestInOrder, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testCompleteWithPayloadBodyAndTrailers(HttpProtocol protocol) throws Exception {
        setUp(protocol);
        makeRequestAndAssertResponse(SVC_ECHO, protocol, OK, CONTENT.readableBytes());

        verifyObservers(true, clientLifecycleObserver, clientExchangeObserver, clientRequestObserver,
                clientResponseObserver, clientInOrder, clientRequestInOrder, true);
        verifyObservers(false, serverLifecycleObserver, serverExchangeObserver, serverRequestObserver,
                serverResponseObserver, serverInOrder, serverRequestInOrder, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testServerThrows(HttpProtocol protocol) throws Exception {
        setUp(protocol);
        makeRequestAndAssertResponse(SVC_THROW_ERROR, protocol, INTERNAL_SERVER_ERROR, 0);

        verifyObservers(true, clientLifecycleObserver, clientExchangeObserver, clientRequestObserver,
                clientResponseObserver, clientInOrder, clientRequestInOrder, false);
        verifyObservers(false, serverLifecycleObserver, serverExchangeObserver, serverRequestObserver,
                serverResponseObserver, serverInOrder, serverRequestInOrder, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testServerSingleFailed(HttpProtocol protocol) throws Exception {
        setUp(protocol);
        makeRequestAndAssertResponse(SVC_SINGLE_ERROR, protocol, INTERNAL_SERVER_ERROR, 0);

        verifyObservers(true, clientLifecycleObserver, clientExchangeObserver, clientRequestObserver,
                clientResponseObserver, clientInOrder, clientRequestInOrder, false);
        verifyObservers(false, serverLifecycleObserver, serverExchangeObserver, serverRequestObserver,
                serverResponseObserver, serverInOrder, serverRequestInOrder, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testServerPayloadBodyFailure(HttpProtocol protocol) {
        setUp(protocol);
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> makeRequestAndAssertResponse(SVC_ERROR_DURING_READ, protocol, OK, 0));
        assertThat(e.getCause(), instanceOf(protocol == HttpProtocol.HTTP_2 ?
                Http2Exception.class : ClosedChannelException.class));

        verifyError(true, clientLifecycleObserver, clientExchangeObserver, clientRequestObserver,
                clientResponseObserver, clientInOrder, clientRequestInOrder);
        verifyError(false, serverLifecycleObserver, serverExchangeObserver, serverRequestObserver,
                serverResponseObserver, serverInOrder, serverRequestInOrder);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testConnectionFailsRequestBeforeWrite(HttpProtocol protocol) {
        connectionFilterFactory(client -> new StreamingHttpConnectionFilter(client) {
            @Override
            public Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy, StreamingHttpRequest request) {
                return failed(DELIBERATE_EXCEPTION);
            }
        });
        setUp(protocol);

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> makeRequestAndAssertResponse(SVC_ECHO, protocol, OK, CONTENT.readableBytes()));
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));

        clientInOrder.verify(clientLifecycleObserver).onNewExchange();
        clientInOrder.verify(clientExchangeObserver).onRequest(any(StreamingHttpRequest.class));
        clientInOrder.verify(clientExchangeObserver).onConnectionSelected(any(ConnectionInfo.class));
        clientInOrder.verify(clientExchangeObserver).onResponseError(e.getCause());
        clientInOrder.verify(clientExchangeObserver).onExchangeFinally();

        verifyNoMoreInteractions(clientLifecycleObserver, clientExchangeObserver);
        verifyNoInteractions(clientRequestObserver, clientResponseObserver,
                serverLifecycleObserver, serverExchangeObserver, serverRequestObserver, serverResponseObserver);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testClientCancelsRequestBeforeResponse(HttpProtocol protocol) throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx,
                        request.transformMessageBody(mb -> mb.afterOnSubscribe(__ -> requestReceived.countDown())),
                        responseFactory);
            }
        });
        setUp(protocol);

        StreamingHttpClient client = streamingHttpClient();
        Future<StreamingHttpResponse> responseFuture = client.request(client.post(SVC_NEVER)
                .payloadBody(Publisher.never())).toFuture();
        requestReceived.await();
        responseFuture.cancel(true);

        // Verify both exchanges terminate before verifying order of events:
        verify(clientExchangeObserver, await()).onExchangeFinally();
        verify(serverExchangeObserver, await()).onExchangeFinally();

        clientInOrder.verify(clientLifecycleObserver).onNewExchange();
        clientInOrder.verify(clientExchangeObserver).onRequest(any(StreamingHttpRequest.class));
        clientInOrder.verify(clientExchangeObserver).onConnectionSelected(any(ConnectionInfo.class));
        clientInOrder.verify(clientExchangeObserver).onResponseCancel();
        verify(clientRequestObserver).onRequestCancel();
        verifyNoMoreInteractions(clientLifecycleObserver, clientExchangeObserver, clientRequestObserver);
        verifyNoInteractions(clientResponseObserver);

        serverInOrder.verify(serverLifecycleObserver).onNewExchange();
        serverInOrder.verify(serverExchangeObserver).onConnectionSelected(any(ConnectionInfo.class));
        serverInOrder.verify(serverExchangeObserver).onRequest(any(StreamingHttpRequest.class));
        serverInOrder.verify(serverRequestObserver).onRequestError(any(IOException.class));
        // because of offloading, cancel from the IO-thread may race with an error propagated through request publisher:
        verify(serverExchangeObserver, atMostOnce()).onResponseCancel();
        verify(serverExchangeObserver, atMostOnce()).onResponse(any(StreamingHttpResponse.class));
        verify(serverResponseObserver, atMostOnce()).onResponseComplete();
        verifyNoMoreInteractions(serverLifecycleObserver, serverExchangeObserver,
                serverRequestObserver, serverResponseObserver);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testClientCancelsRequestAfterResponse(HttpProtocol protocol) throws Exception {
        TestPublisher<Buffer> serverResponsePayload = new TestPublisher<>();
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                return request.payloadBody().ignoreElements()
                        .concat(succeeded(responseFactory.ok().payloadBody(serverResponsePayload)));
            }
        });
        setUp(protocol);

        StreamingHttpConnection connection = streamingHttpConnection();
        StreamingHttpRequest request = connection.post("/")
                .payloadBody(Publisher.from(CONTENT.duplicate()))
                .transform(new StatelessTrailersTransformer<>());   // adds empty trailers
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertResponse(response, protocol.version, OK);
        Future<Collection<Buffer>> payload = response.payloadBody().toFuture();
        payload.cancel(true);
        if (protocol == HttpProtocol.HTTP_1) {
            // wait for cancellation to close the connection:
            connection.onClose().toFuture().get();
        }
        // try to write server content to trigger write failure and close the server-side connection:
        serverResponsePayload.onNext(CONTENT.duplicate());

        // Verify both exchanges terminate before verifying order of events:
        verify(clientExchangeObserver, await()).onExchangeFinally();
        verify(serverExchangeObserver, await()).onExchangeFinally();

        clientInOrder.verify(clientLifecycleObserver).onNewExchange();
        clientInOrder.verify(clientExchangeObserver).onConnectionSelected(any(ConnectionInfo.class));
        clientInOrder.verify(clientExchangeObserver).onRequest(any(StreamingHttpRequest.class));
        clientInOrder.verify(clientExchangeObserver).onResponse(any(StreamingHttpResponse.class));
        clientInOrder.verify(clientResponseObserver).onResponseCancel();
        clientRequestInOrder.verify(clientRequestObserver).onRequestData(any(Buffer.class));
        clientRequestInOrder.verify(clientRequestObserver).onRequestTrailers(any(HttpHeaders.class));
        clientRequestInOrder.verify(clientRequestObserver).onRequestComplete();
        verifyNoMoreInteractions(clientLifecycleObserver, clientExchangeObserver,
                clientRequestObserver, clientResponseObserver);

        serverInOrder.verify(serverLifecycleObserver).onNewExchange();
        serverInOrder.verify(serverExchangeObserver).onConnectionSelected(any(ConnectionInfo.class));
        serverInOrder.verify(serverExchangeObserver).onRequest(any(StreamingHttpRequest.class));
        serverInOrder.verify(serverExchangeObserver).onResponse(any(StreamingHttpResponse.class));
        serverInOrder.verify(serverResponseObserver).onResponseData(any(Buffer.class));
        serverInOrder.verify(serverResponseObserver).onResponseCancel();
        serverRequestInOrder.verify(serverRequestObserver).onRequestData(any(Buffer.class));
        serverRequestInOrder.verify(serverRequestObserver).onRequestTrailers(any(HttpHeaders.class));
        serverRequestInOrder.verify(serverRequestObserver).onRequestComplete();
        verifyNoMoreInteractions(serverLifecycleObserver, serverExchangeObserver,
                serverRequestObserver, serverResponseObserver);
    }

    private void makeRequestAndAssertResponse(String path, HttpProtocol protocol,
                                              HttpResponseStatus status, int contentLength) throws Exception {
        StreamingHttpClient client = streamingHttpClient();
        StreamingHttpRequest request = contentLength == 0 ? client.get(path) : client.post(path)
                .payloadBody(Publisher.from(CONTENT.duplicate()))
                .transform(new StatelessTrailersTransformer<>());   // adds empty trailers
        StreamingHttpResponse response = client.request(request).toFuture().get();
        assertResponse(response, protocol.version, status, contentLength);
    }

    private static void verifyObservers(boolean client, HttpLifecycleObserver lifecycle, HttpExchangeObserver exchange,
                                        HttpRequestObserver request, HttpResponseObserver response,
                                        InOrder inOrder, InOrder requestInOrder, boolean hasMessageBody) {
        inOrder.verify(lifecycle).onNewExchange();
        if (client) {
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
        } else {
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
        }
        inOrder.verify(exchange).onResponse(any(StreamingHttpResponse.class));
        inOrder.verify(response, hasMessageBody ? times(1) : never()).onResponseData(any(Buffer.class));
        inOrder.verify(response, hasMessageBody || client ? times(1) : never())
                .onResponseTrailers(any(HttpHeaders.class));
        inOrder.verify(response).onResponseComplete();
        inOrder.verify(exchange).onExchangeFinally();

        requestInOrder.verify(request, hasMessageBody ? times(1) : never()).onRequestData(any(Buffer.class));
        requestInOrder.verify(request, hasMessageBody || !client ? times(1) : never())
                .onRequestTrailers(any(HttpHeaders.class));
        requestInOrder.verify(request).onRequestComplete();

        verifyNoMoreInteractions(request, response, exchange, lifecycle);
    }

    private static void verifyError(boolean client, HttpLifecycleObserver lifecycle, HttpExchangeObserver exchange,
                                    HttpRequestObserver request, HttpResponseObserver response,
                                    InOrder inOrder, InOrder requestInOrder) {
        inOrder.verify(lifecycle).onNewExchange();
        if (client) {
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
        } else {
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
        }
        inOrder.verify(exchange).onResponse(any(StreamingHttpResponse.class));
        inOrder.verify(response).onResponseError(!client ? DELIBERATE_EXCEPTION : any());
        inOrder.verify(exchange).onExchangeFinally();

        if (!client) {
            requestInOrder.verify(request).onRequestTrailers(any(HttpHeaders.class));
        }
        requestInOrder.verify(request).onRequestComplete();

        verifyNoMoreInteractions(request, response, exchange, lifecycle);
    }
}
