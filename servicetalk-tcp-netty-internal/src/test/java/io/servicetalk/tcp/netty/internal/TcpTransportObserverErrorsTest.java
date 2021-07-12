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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.IDLE_TIMEOUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

final class TcpTransportObserverErrorsTest extends AbstractTransportObserverTest {

    private enum ErrorSource {
        CONNECTION_ACCEPTOR,
        PIPELINE,
        CLIENT_WRITE,
        CLIENT_IDLE_TIMEOUT,
        SERVER_IDLE_TIMEOUT,
    }

    private final TcpClientConfig tcpClientConfig = super.getTcpClientConfig();
    private final TcpServerConfig tcpServerConfig = super.getTcpServerConfig();
    private ChannelInitializer channelInitializer = channel -> { };

    private void setUp(ErrorSource errorSource) throws Exception {
        switch (errorSource) {
            case CONNECTION_ACCEPTOR:
                connectionAcceptor(ctx -> failed(DELIBERATE_EXCEPTION));
                break;
            case PIPELINE:
                channelInitializer = channel -> channel.pipeline().addLast(new SimpleChannelInboundHandler<Buffer>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Buffer msg) {
                        throw DELIBERATE_EXCEPTION;
                    }
                });
                break;
            case CLIENT_WRITE:
                break;
            case CLIENT_IDLE_TIMEOUT:
                tcpClientConfig.socketOption(IDLE_TIMEOUT, 1L);
                break;
            case SERVER_IDLE_TIMEOUT:
                tcpServerConfig.socketOption(IDLE_TIMEOUT, 1L);
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }

        setUp();
    }

    @Override
    TcpServer createServer() {
        return new TcpServer(getTcpServerConfig()) {
            @Override
            ChannelInitializer getChannelInitializer(Function<NettyConnection<Buffer, Buffer>, Completable> service,
                                                     ExecutionContext executionContext) {
                return super.getChannelInitializer(service, executionContext).andThen(channelInitializer);
            }
        };
    }

    @Override
    TcpClientConfig getTcpClientConfig() {
        return tcpClientConfig;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        return tcpServerConfig;
    }

    @ParameterizedTest
    @EnumSource(ErrorSource.class)
    void testConnectionClosed(ErrorSource errorSource) throws Exception {
        setUp(errorSource);

        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        verify(clientConnectionObserver).connectionEstablished(any(ConnectionInfo.class));
        verify(serverConnectionObserver, await()).connectionEstablished(any(ConnectionInfo.class));
        switch (errorSource) {
            case CONNECTION_ACCEPTOR:
            case CLIENT_IDLE_TIMEOUT:
            case SERVER_IDLE_TIMEOUT:
                break;
            case PIPELINE:
                Buffer content = connection.executionContext().bufferAllocator().fromAscii("Hello");
                connection.write(from(content.duplicate())).toFuture().get();
                verify(clientConnectionObserver).onDataWrite(content.readableBytes());
                verify(clientConnectionObserver).onFlush();
                assertThrows(ExecutionException.class,
                        () -> connection.read().firstOrElse(() -> null).toFuture().get());
                verify(serverConnectionObserver).onDataRead(content.readableBytes());
                break;
            case CLIENT_WRITE:
                assertThrows(ExecutionException.class, () -> connection.write(
                        Publisher.failed(DELIBERATE_EXCEPTION)).toFuture().get());
                verify(clientDataObserver).onNewWrite();
                verify(clientWriteObserver).requestedToWrite(anyLong());
                ArgumentCaptor<Throwable> exceptionCaptor = forClass(Throwable.class);
                verify(clientWriteObserver).writeFailed(exceptionCaptor.capture());
                assertThat(exceptionCaptor.getValue(), instanceOf(RetryableException.class));
                assertThat(exceptionCaptor.getValue().getCause(), is(DELIBERATE_EXCEPTION));
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }
        connection.onClose().toFuture().get();
        switch (errorSource) {
            case CONNECTION_ACCEPTOR:
            case PIPELINE:
                verify(clientConnectionObserver).connectionClosed();
                verify(serverConnectionObserver, await()).connectionClosed(DELIBERATE_EXCEPTION);
                break;
            case CLIENT_WRITE:
                verify(clientConnectionObserver).connectionClosed(DELIBERATE_EXCEPTION);
                verify(serverConnectionObserver, await()).connectionClosed();
                break;
            case CLIENT_IDLE_TIMEOUT:
            case SERVER_IDLE_TIMEOUT:
                verify(clientConnectionObserver).connectionClosed();
                verify(serverConnectionObserver, await()).connectionClosed();
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorSource);
        }

        verifyNoMoreInteractions(clientTransportObserver, clientConnectionObserver,
                serverTransportObserver, serverConnectionObserver);
    }
}
