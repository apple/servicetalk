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
 * Common <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC header names</a>.
 */
public final class GrpcHeaderNames {

    /**
     * {@code grpc-status}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_STATUS = newAsciiString("grpc-status");

    /**
     * {@code grpc-status-details-bin}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_STATUS_DETAILS_BIN = newAsciiString("grpc-status-details-bin");

    /**
     * {@code grpc-message}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_STATUS_MESSAGE = newAsciiString("grpc-message");

    /**
     * {@code grpc-message-type}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_MESSAGE_TYPE = newAsciiString("grpc-message-type");

    /**
     * {@code grpc-encoding}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_MESSAGE_ENCODING = newAsciiString("grpc-encoding");

    /**
     * {@code grpc-accept-encoding}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_MESSAGE_ACCEPT_ENCODING = newAsciiString("grpc-accept-encoding");

    /**
     * {@code grpc-timeout}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_TIMEOUT = newAsciiString("grpc-timeout");

    private GrpcHeaderNames() {
        // No instances.
    }
}
