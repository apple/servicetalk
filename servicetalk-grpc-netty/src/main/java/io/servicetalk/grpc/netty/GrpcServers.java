/*
 * Copyright © 2019, 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcProviders.GrpcServerBuilderProvider;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.http.netty.HttpServers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> servers.
 */
public final class GrpcServers {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcServers.class);

    private static final List<GrpcServerBuilderProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = GrpcServers.class.getClassLoader();
        PROVIDERS = loadProviders(GrpcServerBuilderProvider.class, classLoader, LOGGER);
    }

    private GrpcServers() {
        // No instances
    }

    private static GrpcServerBuilder applyProviders(final SocketAddress address, GrpcServerBuilder builder) {
        for (GrpcServerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(address, builder);
        }
        return builder;
    }

    /**
     * New {@link GrpcServerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link GrpcServerBuilderProvider}.
     *
     * @param port the listen port for the server
     * @return a new builder
     * @see GrpcServerBuilderProvider
     */
    public static GrpcServerBuilder forPort(final int port) {
        final InetSocketAddress address = new InetSocketAddress(port);
        return forAddress(address);
    }

    /**
     * New {@link GrpcServerBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link GrpcServerBuilderProvider}.
     *
     * @param address the listen {@link SocketAddress} for the server
     * @return a new builder
     * @see GrpcServerBuilderProvider
     */
    public static GrpcServerBuilder forAddress(final SocketAddress address) {
        return applyProviders(address, new DefaultGrpcServerBuilder(() -> HttpServers.forAddress(address)));
    }
}
