/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpProviders.HttpServerBuilderProvider;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create {@link HttpServerContext HTTP servers}.
 */
public final class HttpServers {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServers.class);

    private static final List<HttpServerBuilderProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = HttpServers.class.getClassLoader();
        PROVIDERS = loadProviders(HttpServerBuilderProvider.class, classLoader, LOGGER);
    }

    private HttpServers() {
        // No instances
    }

    private static HttpServerBuilder applyProviders(final SocketAddress address, HttpServerBuilder builder) {
        for (HttpServerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(address, builder);
        }
        return builder;
    }

    /**
     * New {@link HttpServerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link HttpServerBuilderProvider}.
     *
     * @param port The listen port for the server
     * @return a new builder
     * @see HttpServerBuilderProvider
     */
    public static HttpServerBuilder forPort(final int port) {
        final InetSocketAddress address = new InetSocketAddress(port);
        return applyProviders(address, new DefaultHttpServerBuilder(address));
    }

    /**
     * New {@link HttpServerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link HttpServerBuilderProvider}.
     *
     * @param address The listen {@link SocketAddress} for the server
     * @return a new builder
     * @see HttpServerBuilderProvider
     */
    public static HttpServerBuilder forAddress(final SocketAddress address) {
        return applyProviders(address, new DefaultHttpServerBuilder(address));
    }
}
