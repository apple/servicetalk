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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

final class NettyPipelineSslUtilsTest {

    @Test
    void returnsNullWhenNoSslHandlerInPipeline() {
        final EmbeddedChannel ch = new EmbeddedChannel();
        try {
            assertThat(NettyPipelineSslUtils.applicationSslHandler(ch.pipeline()), is(nullValue()));
        } finally {
            ch.close();
        }
    }

    @Test
    void returnsSingleSslHandler() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel();
        try {
            final SslHandler only = newClientSslHandler(ch);
            ch.pipeline().addLast(only);
            assertThat(NettyPipelineSslUtils.applicationSslHandler(ch.pipeline()), is(sameInstance(only)));
        } finally {
            ch.close();
        }
    }

    @Test
    void returnsTailMostWhenMultipleSslHandlersExist() throws Exception {
        // Layered TLS shape: outer (proxy) SslHandler added first, inner (origin) added later.
        // innermost lookup must return the inner one — that's the application-visible session.
        final EmbeddedChannel ch = new EmbeddedChannel();
        try {
            final SslHandler outer = newClientSslHandler(ch);
            final SslHandler inner = newClientSslHandler(ch);
            ch.pipeline().addLast(NettyPipelineSslUtils.PROXY_SSL_HANDLER_NAME, outer);
            ch.pipeline().addLast(inner);

            final SslHandler found = NettyPipelineSslUtils.applicationSslHandler(ch.pipeline());
            assertThat(found, is(notNullValue()));
            assertThat(found, is(sameInstance(inner)));
            // Sanity check: Netty's pipeline.get(SslHandler.class) returns the head-most (the proxy) — the very
            // bug the helper exists to work around.
            assertThat(ch.pipeline().get(SslHandler.class), is(sameInstance(outer)));
        } finally {
            ch.close();
        }
    }

    @Test
    void installProxyTlsStageAddsHandlerThenIsolator() {
        final EmbeddedChannel ch = new EmbeddedChannel();
        try {
            final ClientSslConfig config = new ClientSslConfigBuilder().build();
            final SslContext sslCtx = SslContextFactory.forClient(config);

            NettyPipelineSslUtils.installProxyTlsStage(ch, sslCtx, config);

            // Check the names
            assertThat(ch.pipeline().get(NettyPipelineSslUtils.PROXY_SSL_HANDLER_NAME),
                    is(instanceOf(SslHandler.class)));
            assertThat(ch.pipeline().get(ProxySslHandlerEventsIsolatorHandler.HANDLER_NAME),
                    is(sameInstance(ProxySslHandlerEventsIsolatorHandler.INSTANCE)));
            // check the order
            Iterator<Map.Entry<String, ChannelHandler>> iterator = ch.pipeline().iterator();
            assertThat(iterator.next().getValue(), instanceOf(SslHandler.class));
            assertThat(iterator.next().getValue(), sameInstance(ProxySslHandlerEventsIsolatorHandler.INSTANCE));
        } finally {
            ch.close();
        }
    }

    private static SslHandler newClientSslHandler(final EmbeddedChannel channel) throws Exception {
        // Use Netty's insecure trust manager — handshake never runs in these tests, we just need real handler
        // instances in the pipeline.
        final SslContext ctx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        return ctx.newHandler(channel.alloc(), "host", 443);
    }
}
