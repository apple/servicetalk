/*
 * Copyright © 2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.net.ssl.SSLEngine;

import static io.servicetalk.transport.netty.internal.SslUtils.newServerSslHandler;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;

/**
 * SNI {@link ChannelInitializer} for servers.
 */
public final class SniServerChannelInitializer implements ChannelInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SniServerChannelInitializer.class);

    private static final Mapping<String, SslContext> NULL_MAPPING = input -> null;
    private static final boolean CAN_SET_ALL_SETTINGS;

    static {
        MethodHandle newCtor;
        try {
            // Find a new constructor that exists only in Netty starting from 4.1.94.Final:
            newCtor = MethodHandles.publicLookup().findConstructor(SniHandler.class,
                    methodType(void.class, Mapping.class, int.class, long.class));
            // Verify the new constructor is working as expected:
            if (!(newCtor.invoke(NULL_MAPPING, 0, 0L) instanceof SniHandler)) {
                throw new IllegalStateException("MethodHandle did not return an instance of SniHandler");
            }
        } catch (Throwable cause) {
            LOGGER.debug("SniHandler(Mapping, int, long) constructor is available only starting from " +
                            "Netty 4.1.94.Final. Detected Netty version: {}",
                    SniHandler.class.getPackage().getImplementationVersion(), cause);
            newCtor = null;
        }
        CAN_SET_ALL_SETTINGS = newCtor != null;
    }

    private final Mapping<String, SslContext> sniMapping;
    private final int maxClientHelloLength;
    private final long clientHelloTimeoutMillis;

    /**
     * Create a new instance.
     *
     * @param sniMapping to use for SNI configuration.
     * @deprecated Use {@link #SniServerChannelInitializer(Mapping, int, long)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated constructor
    public SniServerChannelInitializer(final Mapping<String, SslContext> sniMapping) {
        this(sniMapping, 0, 0L);
    }

    /**
     * Create a new instance.
     *
     * @param sniMapping to use for SNI configuration.
     * @param maxClientHelloLength The maximum length of a
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message in bytes, up to
     * {@code 2^24 - 1} bytes. Zero ({@code 0}) disables validation.
     * @param clientHelloTimeoutMillis The timeout in milliseconds for waiting until
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message is received.
     * Zero ({@code 0}) disables timeout.
     *
     */
    public SniServerChannelInitializer(final Mapping<String, SslContext> sniMapping,
                                       final int maxClientHelloLength,
                                       final long clientHelloTimeoutMillis) {
        this.sniMapping = requireNonNull(sniMapping);
        this.maxClientHelloLength = maxClientHelloLength;
        this.clientHelloTimeoutMillis = clientHelloTimeoutMillis;
    }

    @Override
    public void init(final Channel channel) {
        channel.pipeline().addLast(CAN_SET_ALL_SETTINGS ?
                new SniHandlerWithAllSettings(sniMapping, maxClientHelloLength, clientHelloTimeoutMillis) :
                new SniHandlerWithPooledAllocator(sniMapping));
    }

    /**
     * Overrides the {@link ByteBufAllocator} used by {@link SslHandler} when it needs to copy data to direct memory if
     * required by {@link SSLEngine}. {@link SslHandler} releases allocated direct {@link ByteBuf}s after processing.
     */
    private static final class SniHandlerWithPooledAllocator extends SniHandler {
        SniHandlerWithPooledAllocator(final Mapping<String, SslContext> mapping) {
            super(mapping);
        }

        @Override
        protected SslHandler newSslHandler(final SslContext context, final ByteBufAllocator ignore) {
            return newServerSslHandler(context);
        }
    }

    private static final class SniHandlerWithAllSettings extends SniHandler {
        SniHandlerWithAllSettings(final Mapping<String, SslContext> mapping,
                                  final int maxClientHelloLength,
                                  final long clientHelloTimeoutMillis) {
            super(mapping, maxClientHelloLength, clientHelloTimeoutMillis);
        }

        @Override
        protected SslHandler newSslHandler(final SslContext context, final ByteBufAllocator ignore) {
            return newServerSslHandler(context);
        }
    }
}
