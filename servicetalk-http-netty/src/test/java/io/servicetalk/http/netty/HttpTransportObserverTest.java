/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.H2ToStH1Utils.H2StreamResetException;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
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
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class HttpTransportObserverTest extends AbstractNettyHttpServerTest {

    private enum Protocol {
        HTTP_1(h1Default(), HTTP_1_1),
        HTTP_2(h2Default(), HTTP_2_0);

        private final HttpProtocolConfig config;
        private final HttpProtocolVersion version;

        Protocol(HttpProtocolConfig config, HttpProtocolVersion version) {
            this.config = config;
            this.version = version;
        }
    }

    private final Protocol protocol;

    private final TransportObserver clientTransportObserver;
    private final ConnectionObserver clientConnectionObserver;
    private final DataObserver clientDataObserver;
    private final MultiplexedObserver clientMultiplexedObserver;
    private final StreamObserver clientStreamObserver;
    private final ReadObserver clientReadObserver;
    private final WriteObserver clientWriteObserver;

    private final TransportObserver serverTransportObserver;
    private final ConnectionObserver serverConnectionObserver;
    private final DataObserver serverDataObserver;
    private final MultiplexedObserver serverMultiplexedObserver;
    private final StreamObserver serverStreamObserver;
    private final ReadObserver serverReadObserver;
    private final WriteObserver serverWriteObserver;

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch processRequest = new CountDownLatch(1);

    public HttpTransportObserverTest(Protocol protocol) {
        super(CACHED, CACHED_SERVER);
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
                    return throwException(e);
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
        when(clientConnectionObserver.established(any(ConnectionInfo.class))).thenReturn(clientDataObserver);
        when(clientConnectionObserver.establishedMultiplexed(any(ConnectionInfo.class)))
                .thenReturn(clientMultiplexedObserver);
        when(clientMultiplexedObserver.onNewStream()).thenReturn(clientStreamObserver);
        when(clientDataObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientDataObserver.onNewWrite()).thenReturn(clientWriteObserver);
        when(clientStreamObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientStreamObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverDataObserver = mock(DataObserver.class, "serverDataObserver");
        serverMultiplexedObserver = mock(MultiplexedObserver.class, "serverMultiplexedObserver");
        serverStreamObserver = mock(StreamObserver.class, "serverStreamObserver");
        serverReadObserver = mock(ReadObserver.class, "serverReadObserver");
        serverWriteObserver = mock(WriteObserver.class, "serverWriteObserver");
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.established(any(ConnectionInfo.class))).thenReturn(serverDataObserver);
        when(serverConnectionObserver.establishedMultiplexed(any(ConnectionInfo.class)))
                .thenReturn(serverMultiplexedObserver);
        when(serverMultiplexedObserver.onNewStream()).thenReturn(serverStreamObserver);
        when(serverDataObserver.onNewRead()).thenReturn(serverReadObserver);
        when(serverDataObserver.onNewWrite()).thenReturn(serverWriteObserver);
        when(serverStreamObserver.onNewRead()).thenReturn(serverReadObserver);
        when(serverStreamObserver.onNewWrite()).thenReturn(serverWriteObserver);

        transportObserver(clientTransportObserver, serverTransportObserver);
    }

    @Parameters(name = "protocol={0}")
    public static Protocol[] data() {
        return Protocol.values();
    }

    @Test
    public void connectionEstablished() throws Exception {
        processRequest.countDown();
        StreamingHttpConnection connection = streamingHttpConnection();

        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        if (protocol == Protocol.HTTP_1) {
            verify(clientConnectionObserver).established(any(ConnectionInfo.class));
            verify(serverConnectionObserver, await()).established(any(ConnectionInfo.class));

            verify(serverDataObserver, await()).onNewRead();
            verify(serverDataObserver, await()).onNewWrite();
        } else {
            verify(clientConnectionObserver).establishedMultiplexed(any(ConnectionInfo.class));
            verify(serverConnectionObserver, await()).establishedMultiplexed(any(ConnectionInfo.class));
        }

        connection.closeGracefully();
        assertConnectionClosed();
        verify(clientConnectionObserver).connectionClosed();
        verify(serverConnectionObserver, await()).connectionClosed();

        verifyNoMoreInteractions(clientTransportObserver, clientDataObserver, clientMultiplexedObserver,
                serverTransportObserver, serverDataObserver, serverMultiplexedObserver);
        if (protocol != Protocol.HTTP_2) {
            // HTTP/2 coded adds additional write/flush events related to connection preface. Also, it may emit more
            // flush events on the pipeline after the connection is closed.
            verifyNoMoreInteractions(clientConnectionObserver, serverConnectionObserver);
        }
    }

    @Test
    public void echoRequestResponse() throws Exception {
        String requestContent = "request_content";
        testRequestResponse(streamingHttpConnection().post(SVC_ECHO)
                .addHeader(CONTENT_LENGTH, String.valueOf(requestContent.length()))
                .payloadBody(getChunkPublisherFromStrings(requestContent)), OK, requestContent.length());
    }

    @Test
    public void serverHandlerError() throws Exception {
        testRequestResponse(streamingHttpConnection().get(SVC_THROW_ERROR).addHeader(CONTENT_LENGTH, ZERO),
                INTERNAL_SERVER_ERROR, 0);
    }

    public void testRequestResponse(StreamingHttpRequest request, HttpResponseStatus expectedStatus,
                                    int expectedResponseLength) throws Exception {
        processRequest.countDown();
        assertResponse(makeRequest(request), protocol.version, expectedStatus, expectedResponseLength);

        verifyNewReadAndNewWrite(2);

        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeComplete();

        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();
        verify(serverReadObserver, await()).readComplete();

        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(serverWriteObserver, atLeastOnce()).itemReceived();
        verify(serverWriteObserver, atLeastOnce()).onFlushRequest();
        verify(serverWriteObserver, atLeastOnce()).itemWritten();
        if (protocol == Protocol.HTTP_2) {
            // HTTP/1.x has a single write publisher across all requests that does not complete after each response
            verify(serverWriteObserver).writeComplete();
        }

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver, atLeastOnce()).itemRead();
        verify(clientReadObserver).readComplete();

        verifyNoMoreInteractions(
                clientDataObserver, clientMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverDataObserver, serverMultiplexedObserver, serverReadObserver, serverWriteObserver);
    }

    @Test
    public void serverFailsResponsePayloadBodyBeforeRead() throws Exception {
        testServerFailsResponsePayloadBody(SVC_ERROR_BEFORE_READ, false);
    }

    @Test
    public void serverFailsResponsePayloadBodyDuringRead() throws Exception {
        testServerFailsResponsePayloadBody(SVC_ERROR_DURING_READ, true);
    }

    public void testServerFailsResponsePayloadBody(String path, boolean serverReadCompletes) throws Exception {
        processRequest.countDown();
        StreamingHttpConnection connection = streamingHttpConnection();
        StreamingHttpResponse response = makeRequest(connection.post(path)
                .addHeader(CONTENT_LENGTH, ZERO));
        assertThat(response.status(), is(OK));
        assertThat(response.version(), is(protocol.version));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> response.payloadBody().ignoreElements().toFuture().get());
        Class<? extends Throwable> causeType = protocol == Protocol.HTTP_1 ?
                ClosedChannelException.class : H2StreamResetException.class;
        assertThat(e.getCause(), instanceOf(causeType));

        if (protocol == Protocol.HTTP_2) {
            connection.closeGracefully();
        }
        assertConnectionClosed();
        verifyNewReadAndNewWrite(1);

        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeComplete();
        // Failure of the read triggers cancellation of the write.
        verify(clientWriteObserver, await()).writeCancelled();

        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();
        if (serverReadCompletes) {
            verify(serverReadObserver).readComplete();
        } else {
            // FIXME: because nobody subscribes to the request payload publisher, the terminal signal is not delivered
            // verify(serverReadObserver).readFailed(any(ClosedChannelException.class));
        }

        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(serverWriteObserver, atLeastOnce()).itemReceived();
        verify(serverWriteObserver, atLeastOnce()).onFlushRequest();
        verify(serverWriteObserver, atLeastOnce()).itemWritten();
        verify(serverWriteObserver, atLeastOnce()).writeFailed(DELIBERATE_EXCEPTION);

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver, atLeastOnce()).itemRead();
        verify(clientReadObserver).readFailed(any(causeType));

        verifyNoMoreInteractions(
                clientDataObserver, clientMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverDataObserver, serverMultiplexedObserver, serverReadObserver, serverWriteObserver);
    }

    @Test
    public void clientFailsRequestPayloadBody() throws Exception {
        StreamingHttpConnection connection = streamingHttpConnection();
        String requestContent = "request_content";
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> makeRequest(connection.post(SVC_ECHO)
                        .addHeader(CONTENT_LENGTH, String.valueOf(requestContent.length()))
                        .payloadBody(defer(() -> {
                            try {
                                requestReceived.await();
                            } catch (InterruptedException interruptedException) {
                                return throwException(interruptedException);
                            }
                            return failed(DELIBERATE_EXCEPTION);
                        }))));
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        processRequest.countDown();

        if (protocol == Protocol.HTTP_2) {
            connection.closeGracefully();
        }
        assertConnectionClosed();
        verifyNewReadAndNewWrite(1);

        verify(clientWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        verify(clientWriteObserver, atLeastOnce()).itemReceived();
        verify(clientWriteObserver, atLeastOnce()).onFlushRequest();
        verify(clientWriteObserver, atLeastOnce()).itemWritten();
        verify(clientWriteObserver).writeFailed(DELIBERATE_EXCEPTION);

        verify(serverReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(serverReadObserver, atLeastOnce()).itemRead();
        verify(serverReadObserver, atMostOnce()).readCancelled();
        if (protocol == Protocol.HTTP_1) {
            verify(serverReadObserver, atMostOnce()).readFailed(any(IOException.class));
        } else {
            verify(serverReadObserver, await()).readFailed(any(H2StreamResetException.class));
        }

        verify(serverWriteObserver, atLeastOnce()).requestedToWrite(anyLong());
        // WriteStreamSubscriber.close0(...) cancels subscription and then terminates the subscriber:
        verify(serverWriteObserver, await()).writeFailed(any(Exception.class));
        verify(serverWriteObserver, atMostOnce()).writeCancelled();

        verify(clientReadObserver, atLeastOnce()).requestedToRead(anyLong());
        verify(clientReadObserver).readCancelled();

        verifyNoMoreInteractions(
                clientDataObserver, clientMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverDataObserver, serverMultiplexedObserver, serverReadObserver);
    }

    private void verifyNewReadAndNewWrite(int nonMultiplexedTimes) {
        if (protocol == Protocol.HTTP_1) {
            verify(clientDataObserver).onNewRead();
            verify(clientDataObserver).onNewWrite();
            verify(serverDataObserver, await().times(nonMultiplexedTimes)).onNewRead();
            verify(serverDataObserver, atLeastOnce()).onNewWrite();
        } else {
            verify(clientMultiplexedObserver).onNewStream();
            verify(serverMultiplexedObserver).onNewStream();

            verify(clientStreamObserver).onNewRead();
            verify(clientStreamObserver).onNewWrite();
            verify(clientStreamObserver).streamClosed();

            verify(serverStreamObserver).onNewRead();
            verify(serverStreamObserver).onNewWrite();
            verify(serverStreamObserver, await()).streamClosed();
        }
    }

    @Override
    void assertConnectionClosed() throws Exception {
        super.assertConnectionClosed();
        serverConnectionClosed.await();
    }

    private static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
