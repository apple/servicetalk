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
package io.servicetalk.examples.http.httpsproxy;

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * Logs the outcome of both TLS handshakes performed when CONNECTing through a TLS proxy: the outer handshake with the
 * proxy itself ({@link ConnectionObserver#onProxySecurityHandshake}) and the inner handshake with the origin server
 * tunneled over the established CONNECT ({@link ConnectionObserver#onSecurityHandshake}).
 */
final class HandshakeLoggingTransportObserver implements TransportObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandshakeLoggingTransportObserver.class);

    @Override
    public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
        return new ConnectionObserver() {
            @Override
            public SecurityHandshakeObserver onProxySecurityHandshake(final SslConfig sslConfig) {
                LOGGER.info("Proxy TLS handshake starting against {}", remoteAddress);
                return new LoggingHandshakeObserver("proxy", remoteAddress);
            }

            @Override
            public SecurityHandshakeObserver onSecurityHandshake(final SslConfig sslConfig) {
                LOGGER.info("Origin TLS handshake starting (tunneled through {})", remoteAddress);
                return new LoggingHandshakeObserver("origin", remoteAddress);
            }
        };
    }

    private static final class LoggingHandshakeObserver implements ConnectionObserver.SecurityHandshakeObserver {

        private final String which;
        private final Object remoteAddress;

        LoggingHandshakeObserver(final String which, final Object remoteAddress) {
            this.which = which;
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
            LOGGER.info("{} TLS handshake complete against {}: protocol={}, cipher={}, peer={}",
                    which, remoteAddress, sslSession.getProtocol(), sslSession.getCipherSuite(),
                    peerPrincipal(sslSession));
        }

        @Override
        public void handshakeFailed(final Throwable cause) {
            LOGGER.warn("{} TLS handshake failed against {}", which, remoteAddress, cause);
        }

        private static String peerPrincipal(final SSLSession session) {
            try {
                return session.getPeerPrincipal().getName();
            } catch (Exception e) {
                return "<unavailable: " + e.getMessage() + ">";
            }
        }
    }
}
