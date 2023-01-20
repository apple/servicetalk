/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.CertificateCompressionAlgorithms;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SslCertificateCompressionTest {

    /**
     * Compares the bytes written and read when certificate compression is enabled vs. when it is not.
     */
    @Test
    void negotiatesServerCertCompression() throws Exception {
        final HttpService service = (ctx, request, responseFactory) ->
            succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));

        long readWithoutCompression;
        long readWithCompression;
        long writtenWithoutCompression;
        long writtenWithCompression;

        try (ServerContext server = serverBuilder(false).listenAndAwait(service)) {
            SslBytesReadTransportObserver observer = new SslBytesReadTransportObserver();
            try (BlockingHttpClient client = clientBuilder(server, false, observer).buildBlocking()) {
                client.request(client.get("/sayHello"));
                readWithoutCompression = observer.handshakeBytesRead;
                writtenWithoutCompression = observer.handshakeBytesWritten;
            }
        }

        try (ServerContext server = serverBuilder(true).listenAndAwait(service)) {
            SslBytesReadTransportObserver observer = new SslBytesReadTransportObserver();
            try (BlockingHttpClient client = clientBuilder(server, true, observer).buildBlocking()) {
                client.request(client.get("/sayHello"));
                readWithCompression = observer.handshakeBytesRead;
                writtenWithCompression = observer.handshakeBytesWritten;
            }
        }

        // We cannot assert "smaller than" with compression since depending on the certificate and the compression
        // algorithm chosen the result might not actually be smaller - but it is certainly different.
        assertNotEquals(readWithCompression, readWithoutCompression);
        assertNotEquals(writtenWithCompression, writtenWithoutCompression);
    }

    private static HttpServerBuilder serverBuilder(final boolean withCompression) {
        ServerSslConfigBuilder sslConfig = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey).provider(SslProvider.OPENSSL);
        if (withCompression) {
            sslConfig.certificateCompressionAlgorithms(
                    Collections.singletonList(CertificateCompressionAlgorithms.zlibDefault()));
        }
        return HttpServers.forAddress(localAddress(0)).sslConfig(sslConfig.build());
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder(
            ServerContext ctx, boolean withCompression, SslBytesReadTransportObserver observer) {
        ClientSslConfigBuilder sslConfig = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                .peerHost(serverPemHostname())
                .provider(SslProvider.OPENSSL);
        if (withCompression) {
            sslConfig.certificateCompressionAlgorithms(
                    Collections.singletonList(CertificateCompressionAlgorithms.zlibDefault()));
        }
        return HttpClients
                .forSingleAddress(serverHostAndPort(ctx))
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(observer))
                .sslConfig(sslConfig.build());
    }

    static class SslBytesReadTransportObserver implements TransportObserver {
        long handshakeBytesRead;
        long handshakeBytesWritten;
        boolean inHandshake;

        @Override
        public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
            return new ConnectionObserver() {
                @Override
                public void onDataRead(final int size) {
                    if (inHandshake) {
                        handshakeBytesRead += size;
                    }
                }

                @Override
                public void onDataWrite(final int size) {
                    if (inHandshake) {
                        handshakeBytesWritten += size;
                    }
                }

                @Override
                public SecurityHandshakeObserver onSecurityHandshake() {
                    inHandshake = true;
                    return new SecurityHandshakeObserver() {
                        @Override
                        public void handshakeFailed(final Throwable cause) {
                            inHandshake = false;
                        }

                        @Override
                        public void handshakeComplete(final SSLSession sslSession) {
                            inHandshake = false;
                        }
                    };
                }

                @Override
                public void onFlush() {
                }

                @Override
                public void onTransportHandshakeComplete() {
                }

                @Override
                public DataObserver connectionEstablished(final ConnectionInfo info) {
                    return NoopTransportObserver.NoopDataObserver.INSTANCE;
                }

                @Override
                public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
                    return NoopTransportObserver.NoopMultiplexedObserver.INSTANCE;
                }

                @Override
                public void connectionClosed(final Throwable error) {
                }

                @Override
                public void connectionClosed() {
                }
            };
        }
    }
}
