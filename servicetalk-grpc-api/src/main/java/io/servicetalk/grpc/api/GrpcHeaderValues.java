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

import io.servicetalk.http.api.HttpHeaderNames;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Common <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC header values</a>.
 */
public final class GrpcHeaderValues {
    /**
     * {@code application/grpc} prefix for the content-type.
     */
    static final String GRPC_CONTENT_TYPE_PREFIX = "application/grpc";

    /**
     * {@code +proto} suffix for the content-type.
     */
    static final String GRPC_CONTENT_TYPE_PROTO_SUFFIX = "+proto";

    /**
     * {@code application/grpc} value for {@link HttpHeaderNames#CONTENT_TYPE content-type} header.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence APPLICATION_GRPC = newAsciiString(GRPC_CONTENT_TYPE_PREFIX);

    /**
     * {@code application/grpc+proto} value for {@link HttpHeaderNames#CONTENT_TYPE content-type} header.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence APPLICATION_GRPC_PROTO =
            newAsciiString(GRPC_CONTENT_TYPE_PREFIX + GRPC_CONTENT_TYPE_PROTO_SUFFIX);

    /**
     * ServiceTalk specific value to use for {@link HttpHeaderNames#USER_AGENT} and {@link HttpHeaderNames#SERVER}
     * headers.
     */
    // TODO (nkant): add project version
    public static final CharSequence SERVICETALK_USER_AGENT = newAsciiString("servicetalk-grpc/");

    private GrpcHeaderValues() {
        // No instances.
    }
}
