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
package io.servicetalk.grpc.api;

/**
 * Supported <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#message-encoding">
 *     gRPC message encoding schemes</a>.
 */
public enum GrpcMessageEncoding {

    None("identity"),
    Gzip("gzip"),
    Zlib("zlib"),
    Snappy("snappy");

    private final String encoding;

    GrpcMessageEncoding(final String encoding) {
        this.encoding = encoding;
    }

    /**
     * A string representation for the message encoding.
     *
     * @return a string representation for the message encoding.
     */
    public String encoding() {
        return encoding;
    }
}
