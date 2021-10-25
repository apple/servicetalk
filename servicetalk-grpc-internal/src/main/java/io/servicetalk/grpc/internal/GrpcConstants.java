/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.internal;

import io.servicetalk.http.api.HttpHeaderNames;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Constants that have to be shared between {@code servicetalk-grpc-api} and {@code servicetalk-grpc-netty}.
 */
public final class GrpcConstants {
    /**
     * {@code application/grpc} prefix for the content-type.
     */
    public static final String GRPC_CONTENT_TYPE_PREFIX = "application/grpc";

    /**
     * {@code +proto} suffix for the content-type.
     */
    public static final String GRPC_PROTO_CONTENT_TYPE = "+proto";

    /**
     * {@code application/grpc} content-type as {@link CharSequence}.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_CONTENT_TYPE = newAsciiString(GRPC_CONTENT_TYPE_PREFIX);

    /**
     * ServiceTalk specific value to use for {@link HttpHeaderNames#SERVER} and {@link HttpHeaderNames#USER_AGENT}
     * headers.
     */
    // TODO (nkant): add project version
    public static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");

    private GrpcConstants() {
        // No instances
    }
}
