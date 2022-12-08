/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import java.net.InetSocketAddress;

import static io.servicetalk.http.netty.HttpProtocol.toConfigs;
import static io.servicetalk.http.utils.HttpLifecycleObservers.logging;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;

final class BuilderUtils {

    private BuilderUtils() {
        // No instances
    }

    static HttpServerBuilder newServerBuilder(ExecutionContext<? extends ExecutionStrategy> ctx,
                                              HttpProtocol... protocols) {
        return newServerBuilderWithConfigs(ctx, toConfigs(protocols));
    }

    static HttpServerBuilder newServerBuilderWithConfigs(ExecutionContext<? extends ExecutionStrategy> ctx,
                                                         HttpProtocolConfig... protocols) {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .ioExecutor(ctx.ioExecutor())
                .executor(ctx.executor())
                .bufferAllocator(ctx.bufferAllocator())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                .lifecycleObserver(logging("servicetalk-tests-lifecycle-observer-logger", TRACE));
        if (protocols.length > 0) {
            builder.protocols(protocols);
        }
        return builder;
    }

    static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder(
            ServerContext serverContext,
            ExecutionContext<? extends ExecutionStrategy> ctx,
            HttpProtocol... protocols) {
        return newClientBuilder(serverHostAndPort(serverContext), ctx, protocols);
    }

    static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder(
            HostAndPort serverHostAndPort,
            ExecutionContext<? extends ExecutionStrategy> ctx,
            HttpProtocol... protocols) {
        return newClientBuilderWithConfigs(serverHostAndPort, ctx, toConfigs(protocols));
    }

    static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilderWithConfigs(
            ServerContext serverContext,
            ExecutionContext<? extends ExecutionStrategy> ctx,
            HttpProtocolConfig... protocols) {
        return newClientBuilderWithConfigs(serverHostAndPort(serverContext), ctx, protocols);
    }

    static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilderWithConfigs(
            HostAndPort serverHostAndPort,
            ExecutionContext<? extends ExecutionStrategy> ctx,
            HttpProtocolConfig... protocols) {

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort)
                        .ioExecutor(ctx.ioExecutor())
                        .executor(ctx.executor())
                        .bufferAllocator(ctx.bufferAllocator())
                        .enableWireLogging("servicetalk-tests-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                        .appendClientFilter(new HttpLifecycleObserverRequesterFilter(
                                logging("servicetalk-tests-lifecycle-observer-logger", TRACE)));
        if (protocols.length > 0) {
            builder.protocols(protocols);
        }
        return builder;
    }
}
