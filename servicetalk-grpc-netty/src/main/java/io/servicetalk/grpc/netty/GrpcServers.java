/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.http.netty.HttpServers;

import java.net.SocketAddress;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> servers.
 */
public final class GrpcServers {

    private GrpcServers() {
        // No instances
    }

    /**
     * New {@link GrpcServerBuilder} instance.
     *
     * @param port the listen port for the server.
     * @return a new builder.
     */
    public static GrpcServerBuilder forPort(int port) {
        return new DefaultGrpcServerBuilder(() -> HttpServers.forPort(port));
    }

    /**
     * New {@link GrpcServerBuilder} instance.
     *
     * @param socketAddress the listen address for the server.
     * @return a new builder.
     */
    public static GrpcServerBuilder forAddress(SocketAddress socketAddress) {
        return new DefaultGrpcServerBuilder(() -> HttpServers.forAddress(socketAddress));
    }
}
