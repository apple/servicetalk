/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.List;

/**
 * A <a href="https://www.grpc.io">gRPC</a> service context.
 */
public interface GrpcServiceContext extends ConnectionContext, GrpcMetadata {

    @Override
    GrpcExecutionContext executionContext();

    @Override
    GrpcProtocol protocol();

    /**
     * The {@link ContentCodec} codings available for this <a href="https://www.grpc.io">gRPC</a> call.
     * @deprecated Will be removed along with {@link ContentCodec}.
     * @return the {@link ContentCodec} codings available for this <a href="https://www.grpc.io">gRPC</a> call.
     */
    @Deprecated
    List<ContentCodec> supportedMessageCodings();

    interface GrpcProtocol extends Protocol {

        HttpProtocolVersion httpProtocol();
    }
}
