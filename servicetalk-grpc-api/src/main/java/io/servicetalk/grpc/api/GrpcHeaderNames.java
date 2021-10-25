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
 * Common <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC header names</a>.
 */
public final class GrpcHeaderNames {

    /**
     * Status → {@code grpc-status}
     * <p>
     * Holds a status code of gRPC operation.
     * <p>
     * Note: this can be either an HTTP header or a trailers.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/statuscodes.md">Status codes and their use in gRPC</a>
     */
    public static final CharSequence GRPC_STATUS = newAsciiString("grpc-status");

    /**
     * Status-Details (binary) → {@code grpc-status-details-bin}
     * <p>
     * Holds a base64 encoded value of the binary representation of
     * <a href="https://github.com/googleapis/googleapis/blob/master/google/rpc/status.proto">google.rpc.Status</a>
     * proto.
     * <p>
     * Note: this can be either an HTTP header or a trailers.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     * @see <a href="https://cloud.google.com/apis/design/errors#error_model">Error Model</a>
     */
    public static final CharSequence GRPC_STATUS_DETAILS_BIN = newAsciiString("grpc-status-details-bin");

    /**
     * Status-Message → {@code grpc-message}
     * <p>
     * Defines an arbitrary message to provide more context for the {@link #GRPC_STATUS}.
     * <p>
     * Note: this can be either an HTTP header or a trailers.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_STATUS_MESSAGE = newAsciiString("grpc-message");

    /**
     * Message-Type → {@code grpc-message-type}
     * <p>
     * Defines a fully qualified proto message name.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    public static final CharSequence GRPC_MESSAGE_TYPE = newAsciiString("grpc-message-type");

    /**
     * Message-Encoding → {@code grpc-encoding}
     * <p>
     * Defines a used Content-Coding of the gRPC message.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     * @see HttpHeaderNames#CONTENT_ENCODING
     */
    public static final CharSequence GRPC_MESSAGE_ENCODING = newAsciiString("grpc-encoding");

    /**
     * Message-Accept-Encoding → {@code grpc-accept-encoding}
     * <p>
     * Defines what Content-Codings a client may accept.
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     * @see HttpHeaderNames#ACCEPT_ENCODING
     */
    public static final CharSequence GRPC_MESSAGE_ACCEPT_ENCODING = newAsciiString("grpc-accept-encoding");

    /**
     * Timeout → {@code grpc-timeout}
     *
     * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     * @see <a href="https://grpc.io/blog/deadlines/">gRPC and Deadlines</a>
     */
    public static final CharSequence GRPC_TIMEOUT = newAsciiString("grpc-timeout");

    private GrpcHeaderNames() {
        // No instances.
    }
}
