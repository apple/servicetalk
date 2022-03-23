/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.data.protobuf.jersey;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpHeaderValues;

import javax.ws.rs.core.MediaType;

/**
 * Media type definitions for Protobuf.
 */
public final class ProtobufMediaTypes {
    /**
     * {@code "application/x-protobuf"}.
     * @see #APPLICATION_X_PROTOBUF_TYPE
     * @see HttpHeaderValues#APPLICATION_X_PROTOBUF
     */
    public static final String APPLICATION_X_PROTOBUF = "application/x-protobuf";
    /**
     * {@code "application/x-protobuf-var-int"}
     * <a href="https://developers.google.com/protocol-buffers/docs/encoding">base 128 VarInt protobuf encoding</a>.
     * Should be used with {@link Publisher} to stream data.
     * @see #APPLICATION_X_PROTOBUF_VAR_INT_TYPE
     * @see HttpHeaderValues#APPLICATION_X_PROTOBUF_VAR_INT
     */
    public static final String APPLICATION_X_PROTOBUF_VAR_INT = "application/x-protobuf-var-int";
    /**
     * {@code "application/x-protobuf"}.
     * @see #APPLICATION_X_PROTOBUF
     */
    public static final MediaType APPLICATION_X_PROTOBUF_TYPE = new MediaType("application", "x-protobuf");
    /**
     * {@code "application/x-protobuf-var-int"}
     * <a href="https://developers.google.com/protocol-buffers/docs/encoding">base 128 VarInt protobuf encoding</a>.
     * Should be used with {@link Publisher} to stream data.
     * @see #APPLICATION_X_PROTOBUF_VAR_INT
     */
    public static final MediaType APPLICATION_X_PROTOBUF_VAR_INT_TYPE =
            new MediaType("application", "x-protobuf-var-int");

    private ProtobufMediaTypes() {
    }
}
