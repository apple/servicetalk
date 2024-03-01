/*
 * Copyright © 2018-2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslContext;

import java.util.function.Supplier;

import static io.servicetalk.transport.netty.internal.SslUtils.newServerSslHandler;
import static java.util.Objects.requireNonNull;

/**
 * SSL {@link ChannelInitializer} for servers.
 */
public final class SslServerChannelInitializer implements ChannelInitializer {
    private final SslContext sslContext;
    private final boolean acceptInsecureConnections;

    /**
     * New instance.
     * @param sslContext to use for default SSL configuration.
     * @param acceptInsecureConnections if non-TLS connections should also be accepted.
     */
    public SslServerChannelInitializer(final SslContext sslContext, final boolean acceptInsecureConnections) {
        this.sslContext = requireNonNull(sslContext);
        this.acceptInsecureConnections = acceptInsecureConnections;
    }

    @Override
    public void init(final Channel channel) {
        final Supplier<ChannelHandler> handlerSupplier = () -> newServerSslHandler(sslContext, channel);
        channel.pipeline().addLast(acceptInsecureConnections ?
                new OptionalSslHandler(handlerSupplier) : handlerSupplier.get());
    }
}
