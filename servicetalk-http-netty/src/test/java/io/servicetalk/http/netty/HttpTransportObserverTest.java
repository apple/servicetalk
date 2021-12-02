/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.NettyHttp2ExceptionUtils.H2StreamResetException;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HttpTransportObserverTest extends AbstractNettyHttpServerTest {

    private HttpProtocol protocol;

    private TransportObserver clientTransportObserver;
    private ConnectionObserver clientConnectionObserver;
    private DataObserver clientDataObserver;
    private MultiplexedObserver clientMultiplexedObserver;
    private StreamObserver clientStreamObserver;
    private ReadObserver clientReadObserver;
    private WriteObserver clientWriteObserver;

    private TransportObserver serverTransportObserver;
    private ConnectionObserver serverConnectionObserver;
    private DataObserver serverDataObserver;
    private MultiplexedObserver serverMultiplexedObserver;
    private StreamObserver serverStreamObserver;
    private ReadObserver serverReadObserver;
    private WriteObserver serverWriteObserver;

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch processRequest = new CountDownLatch(1);

    private void setUp(HttpProtocol protocol) {
        this.protocol = protocol;
        protocol(protocol.config);
        connectionAcceptor(ctx -> {
            ctx.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
            return completed();
        });
        serviceFilterFactory(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                requestReceived.countDown();
                try {
                    processRequest.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throwException(e);
                }
                return delegate().handle(ctx, request, responseFactory);
            }
        });

        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientDataObserver = mock(DataObserver.class, "clientDataObserver");
        clientMultiplexedObserver = mock(MultiplexedObserver.class, "clientMultiplexedObserver");
        clientStreamObserver = mock(StreamObserver.class, "clientStreamObserver");
        clientReadObserver = mock(ReadObserver.class, "clientReadObserver");
        clientWriteObserver = mock(WriteObserver.class, "clientWriteObserver");
        when(clientTransportObserver.onNewConnection()).thenReturn(clientConnectionObserver);
        lenient().when(clientConnectionObserver.connectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(clientDataObserver);
        lenient().when(clientConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(clientMultiplexedObserver);
        lenient().when(clientMultiplexedObserver.onNewStream()).thenReturn(clientStreamObserver);
        lenient().when(clientStreamObserver.streamEstablished()).thenReturn(clientDataObserver);
        lenient().when(clientDataObserver.onNewRead()).thenReturn(clientReadObserver);
        lenient().when(clientDataObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverDataObserver = mock(DataObserver.class, "serverDataObserver");
        serverMultiplexedObserver = mock(MultiplexedObserver.class, "serverMultiplexedObserver");
        serverStreamObserver = mock(StreamObserver.class, "serverStreamObserver");
        serverReadObserver = mock(ReadObserver.class, "serverReadObserver");
        serverWriteObserver = mock(WriteObserver.class, "serverWriteObserver");
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
        lenient().when(serverConnectionObserver.connectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(serverDataObserver);
        lenient().when(serverConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(serverMultiplexedObserver);
        lenient().when(serverMultiplexedObserver.onNewStream()).thenReturn(serverStreamObserver);
        lenient().when(serverStreamObserver.streamEstablished()).thenReturn(serverDataObserver);
        lenient().when(serverDataObserver.onNewRead()).thenReturn(serverReadObserver);
        lenient().when(serverDataObserver.onNewWrite()).thenReturn(serverWriteObserver);

        transportObserver(clientTransportObserver, serverTransportObserver);
        setUp(CACHED, CACHED_SERVER);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void connectionEstablished(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        processRequest.countDown();
        StreamingHttpConnection connection = streamingHttpConnection();

        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        verify(clientConnectionObserver).onTransportHandshakeComplete();
        verify(serverConnectionObserver, await()).onTransportHandshakeComplete();
        if (protocol == HTTP_1) {
            verify(clientConnectionObserver).connectionEstablished(any(ConnectionInfo.class));
            verify(serverConnectionObserver, await()).connectionEstablished(any(ConnectionInfo.class));

            verify(serverDataObserver, await()).onNewRead();
            verify(serverDataObserver, await()).onNewWrite();
        } else {
            verify(clientConnectionObserver).multiplexedConnectionEstablished(any(ConnectionInfo.class));
            verify(serverConnectionObserver, await()).multiplexedConnectionEstablished(any(ConnectionInfo.class));
        }

        connection.closeGracefully();
        assertConnectionClosed();
        verify(clientConnectionObserver).connectionClosed();
        verify(serverConnectionObserver, await()).connectionClosed();

        verifyNoMoreInteractions(clientTransportObserver, clientDataObserver, clientMultiplexedObserver,
                serverTransportObserver, serverDataObserver, serverMultiplexedObserver);
        if (protocol != HTTP_2) {
            // HTTP/2 coded adds additional write/flush events related to connection preface. Also, it may emit more
            // flush events on the pipeline after the connection is closed.
            verifyNoMoreInteractions(clientConnectionObserver, serverConnectionObserver);
        }
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void echoRequestResponse(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        String requestContent = "request_content";
        testRequestResponse(streamingHttpConnection().post(SVC_ECHO)
                .addHeader(CONTENT_LENGTH, String.valueOf(requestContent.length()))
                .payloadBody(getChunkPublisherFromStrings(requestContent)), OK, requestContent.length());
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void serverHandlerError(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        testRequestResponse(streamingHttpConnection().get(SVC_THROW_ERROR).addHeader(CONTENT_LENGTH, ZERO),
                INTERNAL_SERVER_ERROR, 0);
    }

    void testRequestResponse(StreamingHttpRequest request, HttpResponseStatus expectedStatus,
                             int expectedResponseLength) throws Exception {
        processRequest.countDown();
        assertResponse(makeRequest(request), protocol.version, expectedStatus, expectedResponseLength);

        verifyNewReadAndNewWrite(2);

        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeComplete();

        verify(serverReadObserver, await()).readComplete();
        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();

        if (protocol == HTTP_2) {
            // HTTP/1.x has a single write publisher across all requests that does not complete after each response
            verify(serverWriteObserver, await()).writeComplete();
        }
        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(serverWriteObserver, atLeastOnce()).itemReceived();
        verify(serverWriteObserver, atLeastOnce()).onFlushRequest();
        verify(serverWriteObserver, atLeastOnce()).itemWritten();

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver, atLeastOnce()).itemRead();
        verify(clientReadObserver).readComplete();

        if (protocol == HTTP_2) {
            verify(clientStreamObserver, await()).streamClosed();
            verify(serverStreamObserver, await()).streamClosed();
        }
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void serverFailsResponsePayloadBodyBeforeRead(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        testServerFailsResponsePayloadBody(SVC_ERROR_BEFORE_READ, true);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void serverFailsResponsePayloadBodyDuringRead(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        testServerFailsResponsePayloadBody(SVC_ERROR_DURING_READ, true);
    }

    void testServerFailsResponsePayloadBody(String path, boolean serverReadCompletes) throws Exception {
        processRequest.countDown();
        StreamingHttpConnection connection = streamingHttpConnection();
        StreamingHttpResponse response = makeRequest(connection.post(path)
                .addHeader(CONTENT_LENGTH, ZERO));
        assertThat(response.status(), is(OK));
        assertThat(response.version(), is(protocol.version));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> response.payloadBody().ignoreElements().toFuture().get());
        Class<? extends Throwable> causeType = protocol == HTTP_1 ?
                ClosedChannelException.class : H2StreamResetException.class;
        assertThat(e.getCause(), instanceOf(causeType));

        if (protocol == HTTP_2) {
            connection.closeGracefully();
        }
        assertConnectionClosed();
        verifyNewReadAndNewWrite(1);

        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeComplete();

        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();
        if (serverReadCompletes) {
            verify(serverReadObserver).readComplete();
        } else {
            verify(serverReadObserver, atMostOnce()).readCancelled();
            verify(serverReadObserver, atMostOnce()).readFailed(any(Throwable.class));
        }

        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(serverWriteObserver, atLeastOnce()).itemReceived();
        verify(serverWriteObserver, atLeastOnce()).onFlushRequest();
        verify(serverWriteObserver, atLeastOnce()).itemWritten();
        verify(serverWriteObserver, atLeastOnce()).writeFailed(DELIBERATE_EXCEPTION);

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver, atLeastOnce()).itemRead();
        verify(clientReadObserver).readFailed(any(causeType));

        if (protocol == HTTP_1) {
            verify(clientConnectionObserver).connectionClosed();    // FIXME should we see connection RST here?
            verify(serverConnectionObserver).connectionClosed(DELIBERATE_EXCEPTION);
        } else {
            verify(clientStreamObserver).streamClosed(any(causeType));
            verify(serverStreamObserver).streamClosed(DELIBERATE_EXCEPTION);
            verify(clientConnectionObserver).connectionClosed();
            verify(serverConnectionObserver).connectionClosed();
        }

        verifyNoMoreInteractions(
                clientDataObserver, clientMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverDataObserver, serverMultiplexedObserver, serverReadObserver, serverWriteObserver);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void clientFailsRequestPayloadBody(HttpProtocol httpProtocol) throws Exception {
        setUp(httpProtocol);
        StreamingHttpConnection connection = streamingHttpConnection();
        String requestContent = "request_content";
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> makeRequest(connection.post(SVC_ECHO)
                        .addHeader(CONTENT_LENGTH, String.valueOf(requestContent.length()))
                        .payloadBody(defer(() -> {
                            try {
                                requestReceived.await();
                            } catch (InterruptedException interruptedException) {
                                Thread.currentThread().interrupt();
                                throwException(interruptedException);
                            }
                            return failed(DELIBERATE_EXCEPTION);
                        }))));
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        processRequest.countDown();

        if (protocol == HTTP_2) {
            connection.closeGracefully();
        }
        assertConnectionClosed();
        verifyNewReadAndNewWrite(1);

        if (protocol == HTTP_1) {
            verify(clientConnectionObserver, await()).connectionClosed(DELIBERATE_EXCEPTION);
            verify(serverConnectionObserver, await()).connectionClosed(any(IOException.class));
        } else {
            verify(clientStreamObserver, await()).streamClosed(DELIBERATE_EXCEPTION);
            verify(serverStreamObserver, await()).streamClosed(any(H2StreamResetException.class));
            verify(clientConnectionObserver, await()).connectionClosed();
            verify(serverConnectionObserver, await()).connectionClosed();
        }

        // After all "closed" events have been received, verify all other events in between. Otherwise, there is a risk
        // of race between verification and events.
        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeFailed(DELIBERATE_EXCEPTION);

        if (protocol == HTTP_1) {
            verify(serverReadObserver, atMostOnce()).readFailed(any(IOException.class));
        } else {
            verify(serverReadObserver, await()).readFailed(any(H2StreamResetException.class));
        }
        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();
        verify(serverReadObserver, atMostOnce()).readCancelled();

        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        // WriteStreamSubscriber.close0(...) cancels subscription and then terminates the subscriber:
        verify(serverWriteObserver, await()).writeFailed(any(Exception.class));
        verify(serverWriteObserver, atMostOnce()).writeCancelled();

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver).readFailed(any(IOException.class));

        verifyNoMoreInteractions(
                clientDataObserver, clientMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverDataObserver, serverMultiplexedObserver);
    }

    private void verifyNewReadAndNewWrite(int nonMultiplexedTimes) {
        if (protocol == HTTP_1) {
            verify(clientDataObserver).onNewRead();
            verify(clientDataObserver).onNewWrite();
            verify(serverDataObserver, await().times(nonMultiplexedTimes)).onNewRead();
            verify(serverDataObserver, atLeastOnce()).onNewWrite();
        } else {
            verify(clientMultiplexedObserver).onNewStream();
            verify(serverMultiplexedObserver).onNewStream();

            verify(clientStreamObserver).streamEstablished();
            verify(serverStreamObserver).streamEstablished();

            verify(clientDataObserver).onNewRead();
            verify(clientDataObserver).onNewWrite();
            verify(serverDataObserver).onNewRead();
            verify(serverDataObserver).onNewWrite();
        }
    }

    @Override
    void assertConnectionClosed() throws Exception {
        super.assertConnectionClosed();
        serverConnectionClosed.await();
    }

    static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
