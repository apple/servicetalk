/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.transport.api.ServerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Factory methods for building HTTP Servers backed by {@link ServerContext}.
 */
public final class HttpServers {

    private HttpServers() {
        // No instances
    }

    /**
     * New {@link HttpServerBuilder} instance.
     *
     * @param port The listen port for the server.
     * @return a new builder.
     * @see HttpServerBuilder#port(int)
     */
    public static HttpServerBuilder newHttpServerBuilder(int port) {
        return new DefaultHttpServerBuilder(new InetSocketAddress(port));
    }

    /**
     * New {@link HttpServerBuilder} instance.
     *
     * @param socketAddress The listen address for the server.
     * @return a new builder.
     * @see HttpServerBuilder#address(SocketAddress)
     */
    public static HttpServerBuilder newHttpServerBuilder(SocketAddress socketAddress) {
        return new DefaultHttpServerBuilder(socketAddress);
    }
}
