/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.transport.api.ConnectionContext;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcServiceContext extends DefaultGrpcMetadata implements GrpcServiceContext {

    private final ConnectionContext connectionContext;
    private final GrpcExecutionContext executionContext;
    private final GrpcProtocol protocol;
    @Deprecated
    private final List<ContentCodec> supportedMessageCodings;

    DefaultGrpcServiceContext(final String path, final HttpServiceContext httpServiceContext) {
        super(path);
        connectionContext = requireNonNull(httpServiceContext);
        executionContext = new DefaultGrpcExecutionContext(httpServiceContext.executionContext());
        protocol = new DefaultGrpcProtocol(httpServiceContext.protocol());
        this.supportedMessageCodings = emptyList();
    }

    @Override
    public SocketAddress localAddress() {
        return connectionContext.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return connectionContext.remoteAddress();
    }

    @Override
    @Nullable
    public SSLSession sslSession() {
        return connectionContext.sslSession();
    }

    @Override
    public GrpcExecutionContext executionContext() {
        return executionContext;
    }

    @Deprecated
    @Override
    public List<ContentCodec> supportedMessageCodings() {
        return supportedMessageCodings;
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return connectionContext.socketOption(option);
    }

    @Override
    public GrpcProtocol protocol() {
        return protocol;
    }

    @Override
    public Completable onClose() {
        return connectionContext.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connectionContext.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connectionContext.closeAsyncGracefully();
    }

    private static final class DefaultGrpcProtocol implements GrpcProtocol {
        private final HttpProtocolVersion httpProtocol;

        private DefaultGrpcProtocol(final HttpProtocolVersion httpProtocol) {
            this.httpProtocol = requireNonNull(httpProtocol);
        }

        @Override
        public String name() {
            return "gRPC";
        }

        @Override
        public HttpProtocolVersion httpProtocol() {
            return httpProtocol;
        }
    }
}
