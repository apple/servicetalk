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
package io.servicetalk.grpc.api;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Common <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC header values</a>.
 */
public final class GrpcHeaderValues {

    /**
     * {@code application/grpc} prefix
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final String GRPC_CONTENT_TYPE_PREFIX = "application/grpc";

    /**
     * {@code application/grpc+proto} content type
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final String GRPC_PROTO_CONTENT_TYPE = "+proto";

    /**
     * {@code application/grpc} default content type
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_CONTENT_TYPE = newAsciiString(GRPC_CONTENT_TYPE_PREFIX);

    // TODO (nkant): add project version
    /**
     * ServiceTalk specific value for use with {@code server} and {@code user-agent} headers.
     */
    public static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");

    private GrpcHeaderValues() {
        // No instances.
    }
}
