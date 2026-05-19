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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NettyPipelineSslUtils;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

/**
 * Asserts the pipeline shape produced by {@link TcpClientChannelInitializer} under the layered-TLS configurations
 * the proxy-TLS support introduces. Detailed verification of the proxy-stage install (proxy SslHandler + event
 * isolator) lives in {@code transport-netty-internal}'s tests for {@code NettyPipelineSslUtils#installProxyTlsStage};
 * here we only confirm that {@link TcpClientChannelInitializer} invokes that install at the right time and that the
 * deferred origin {@link SslHandler} ends up in the expected position relative to the proxy stage.
 */
final class TcpClientChannelInitializerLayeredTlsTest {

    // mirrored from NettyPipelineSslUtils
    private static final String PROXY_SSL_HANDLER_NAME = "proxySsl";

    @Test
    void layeredTlsHasProxyAndDeferredOrigin() {
        final TcpClientConfig cfg = new TcpClientConfig();
        cfg.proxySslConfig(new ClientSslConfigBuilder().build());
        cfg.sslConfig(new ClientSslConfigBuilder().build());

        final EmbeddedChannel ch = runInitializer(cfg, true);

        assertThat(ch.pipeline().get(PROXY_SSL_HANDLER_NAME), is(instanceOf(SslHandler.class)));
        assertThat(ch.pipeline().get(DeferSslHandler.class), is(notNullValue()));
    }

    @Test
    void proxyOnlyTlsHasProxyButNoDeferredOrigin() {
        // Proxy SSL set, origin SSL not set. innermostSslHandler must return the proxy stage,
        // which is what RequestResponseCloseHandler / KeepAliveManager rely on for graceful close.
        final TcpClientConfig cfg = new TcpClientConfig();
        cfg.proxySslConfig(new ClientSslConfigBuilder().build());

        final EmbeddedChannel ch = runInitializer(cfg, false);

        assertThat(ch.pipeline().get(PROXY_SSL_HANDLER_NAME),
                is(instanceOf(SslHandler.class)));
        assertThat(ch.pipeline().get(DeferSslHandler.class), is(nullValue()));
        assertThat(NettyPipelineSslUtils.innermostSslHandler(ch.pipeline()),
                is(ch.pipeline().get(PROXY_SSL_HANDLER_NAME)));
    }

    private static EmbeddedChannel runInitializer(final TcpClientConfig cfg, final boolean deferSslHandler) {
        final EmbeddedChannel ch = new EmbeddedChannel();
        final ExecutionContext<?> execCtx = mock(ExecutionContext.class);
        new TcpClientChannelInitializer(cfg.asReadOnly(), NoopConnectionObserver.INSTANCE, execCtx, deferSslHandler)
                .init(ch);
        return ch;
    }
}
