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
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.handler.ssl.NotSslRecordException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.api.SslClientAuthMode.REQUIRE;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

final class SecureTcpTransportObserverErrorsTest extends AbstractTransportObserverTest {

    private enum ErrorReason {
        SECURE_CLIENT_TO_PLAIN_SERVER,
        PLAIN_CLIENT_TO_SECURE_SERVER,
        WRONG_HOSTNAME_VERIFICATION,
        UNTRUSTED_SERVER_CERTIFICATE,
        UNTRUSTED_CLIENT_CERTIFICATE,
        MISSED_CLIENT_CERTIFICATE,
        NOT_MATCHING_PROTOCOLS,
        NOT_MATCHING_CIPHERS,
    }

    private final TcpClientConfig clientConfig = super.getTcpClientConfig();
    private final TcpServerConfig serverConfig = super.getTcpServerConfig();

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

    private void setUp(ErrorReason errorReason, SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        ClientSslConfigBuilder clientSslBuilder = defaultClientSslBuilder(clientProvider);
        ServerSslConfigBuilder serverSslBuilder = defaultServerSslBuilder(serverProvider);
        switch (errorReason) {
            case SECURE_CLIENT_TO_PLAIN_SERVER:
                clientConfig.sslConfig(clientSslBuilder.build());
                // In this scenario server may close the connection with or without an exception, depending on OS events
                // Using CountDownLatch to verify that any of these two methods was invoked:
                doAnswer(__ -> {
                    serverConnectionClosed.countDown();
                    return null;
                    // In most cases it closes with
                    // io.netty.channel.unix.Errors$NativeIoException: readAddress(..) failed: Connection reset by peer
                }).when(serverConnectionObserver).connectionClosed(any(IOException.class));
                doAnswer(__ -> {
                    serverConnectionClosed.countDown();
                    return null;
                    // But sometimes netty may close the connection before we generate StacklessClosedChannelException
                    // in io.servicetalk.transport.netty.internal.DefaultNettyConnection.channelInactive(...)
                }).when(serverConnectionObserver).connectionClosed();
                break;
            case PLAIN_CLIENT_TO_SECURE_SERVER:
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case WRONG_HOSTNAME_VERIFICATION:
                clientSslBuilder.hostnameVerificationAlgorithm("HTTPS");
                clientSslBuilder.peerHost("foo");
                clientConfig.sslConfig(clientSslBuilder.build());
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case UNTRUSTED_SERVER_CERTIFICATE:
                clientSslBuilder = defaultClientSslBuilder(clientProvider, () -> null);
                clientConfig.sslConfig(clientSslBuilder.build());
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case UNTRUSTED_CLIENT_CERTIFICATE:
                clientSslBuilder.keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey);
                clientConfig.sslConfig(clientSslBuilder.build());
                serverSslBuilder.clientAuthMode(REQUIRE);
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case MISSED_CLIENT_CERTIFICATE:
                clientConfig.sslConfig(clientSslBuilder.build());
                serverSslBuilder.clientAuthMode(REQUIRE);
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case NOT_MATCHING_PROTOCOLS:
                clientSslBuilder.sslProtocols("TLSv1.2");
                clientConfig.sslConfig(clientSslBuilder.build());
                serverSslBuilder.sslProtocols("TLSv1.3");
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            case NOT_MATCHING_CIPHERS:
                clientSslBuilder.ciphers(singletonList("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"));
                clientConfig.sslConfig(clientSslBuilder.build());
                serverSslBuilder.ciphers(singletonList("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"));
                serverConfig.sslConfig(serverSslBuilder.build());
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorReason);
        }
        setUp();
    }

    static Collection<Arguments> data() {
        Collection<Arguments> data = new ArrayList<>();
        for (ErrorReason reason : ErrorReason.values()) {
            data.add(Arguments.of(reason, JDK, JDK));
            data.add(Arguments.of(reason, JDK, OPENSSL));
            data.add(Arguments.of(reason, OPENSSL, JDK));
            data.add(Arguments.of(reason, OPENSSL, OPENSSL));
        }
        return data;
    }

    @Override
    TcpClientConfig getTcpClientConfig() {
        return clientConfig;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        return serverConfig;
    }

    @ParameterizedTest(name = "errorReason={0}, clientProvider={1}, serverProvider={2}")
    @MethodSource("data")
    void testSslErrors(ErrorReason errorReason,
                       SslProvider clientProvider,
                       SslProvider serverProvider) throws Exception {
        setUp(errorReason, clientProvider, serverProvider);
        CountDownLatch clientConnected = new CountDownLatch(1);
        AtomicReference<NettyConnection<Buffer, Buffer>> connection = new AtomicReference<>();
        client.connect(CLIENT_CTX, serverAddress).subscribe(c -> {
            connection.set(c);
            clientConnected.countDown();
        });
        verify(clientTransportObserver, await()).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        switch (errorReason) {
            case SECURE_CLIENT_TO_PLAIN_SERVER:
                verify(clientConnectionObserver, await()).onSecurityHandshake();
                if (clientProvider == JDK) {
                    verify(clientSecurityHandshakeObserver, await()).handshakeFailed(any(SSLProtocolException.class));
                    verify(clientConnectionObserver, await()).connectionClosed(any(SSLProtocolException.class));
                } else {
                    verify(clientSecurityHandshakeObserver, await()).handshakeFailed(any(SSLHandshakeException.class));
                    verify(clientConnectionObserver, await()).connectionClosed(any(SSLHandshakeException.class));
                }
                serverConnectionClosed.await();
                break;
            case PLAIN_CLIENT_TO_SECURE_SERVER:
                verify(serverConnectionObserver, await()).onSecurityHandshake();
                clientConnected.await();
                connection.get().write(from(DEFAULT_ALLOCATOR.fromAscii("Hello"))).toFuture().get();
                if (serverProvider == JDK) {
                    verify(serverSecurityHandshakeObserver, await()).handshakeFailed(any(NotSslRecordException.class));
                    verify(serverConnectionObserver, await()).connectionClosed(any(NotSslRecordException.class));
                } else {
                    verify(serverSecurityHandshakeObserver, await()).handshakeFailed(any(SSLHandshakeException.class));
                    verify(serverConnectionObserver, await()).connectionClosed(any(SSLHandshakeException.class));
                }
                verify(clientConnectionObserver, await()).connectionClosed();
                break;
            case WRONG_HOSTNAME_VERIFICATION:
            case UNTRUSTED_SERVER_CERTIFICATE:
            case UNTRUSTED_CLIENT_CERTIFICATE:
            case MISSED_CLIENT_CERTIFICATE:
            case NOT_MATCHING_PROTOCOLS:
            case NOT_MATCHING_CIPHERS:
                verify(clientConnectionObserver, await()).onSecurityHandshake();
                verify(serverConnectionObserver, await()).onSecurityHandshake();
                verify(clientSecurityHandshakeObserver, await()).handshakeFailed(any(SSLException.class));
                verify(clientConnectionObserver, await()).connectionClosed(any(SSLException.class));
                verify(serverSecurityHandshakeObserver, await()).handshakeFailed(any(SSLException.class));
                verify(serverConnectionObserver, await()).connectionClosed(any(SSLException.class));
                break;
            default:
                throw new IllegalArgumentException("Unsupported ErrorSource: " + errorReason);
        }
        verifyNoMoreInteractions(clientSecurityHandshakeObserver, serverSecurityHandshakeObserver);
    }
}
