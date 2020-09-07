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

import java.util.Collection;

import static io.servicetalk.grpc.api.GrpcMessageEncodingRegistry.NONE;
import static io.servicetalk.http.api.CharSequences.newAsciiString;

/**
 * Supported <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#message-encoding">
 *     gRPC message encoding schemes</a>.
 */
public interface GrpcMessageEncoding {

    /**
     * A string representation for the message encoding.
     *
     * @return a string representation for the message encoding.
     */
    String name();

    /**
     * The codec that supports encoding/decoding for this type of message-encoding.
     *
     * @return a shared instance of the codec for that message-encoding
     */
    GrpcMessageCodec codec();

    /**
     * Construct the gRPC header {@code grpc-accept-encoding} representation of the given encodings.
     *
     * @param encodings the list of encodings to be used in the string representation.
     * @return a comma separated string representation of the encodings for use as a header value
     */
    static CharSequence toHeaderValue(final Collection<GrpcMessageEncoding> encodings) {
        StringBuilder builder = new StringBuilder();
        for (GrpcMessageEncoding enc : encodings) {
            if (enc == NONE) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(enc.name());
        }

        return newAsciiString(builder.toString());
    }
}
