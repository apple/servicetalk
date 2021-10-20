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

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

public final class GrpcConstants {
    public static final CharSequence GRPC_STATUS_CODE_TRAILER = newAsciiString("grpc-status");
    public static final CharSequence GRPC_STATUS_DETAILS_TRAILER = newAsciiString("grpc-status-details-bin");
    public static final CharSequence GRPC_STATUS_MESSAGE_TRAILER = newAsciiString("grpc-message");
    public static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    public static final CharSequence GRPC_ACCEPT_ENCODING_KEY = newAsciiString("grpc-accept-encoding");
    public static final String GRPC_CONTENT_TYPE_PREFIX = "application/grpc";
    public static final String GRPC_PROTO_CONTENT_TYPE = "+proto";
    public static final CharSequence GRPC_CONTENT_TYPE = newAsciiString(GRPC_CONTENT_TYPE_PREFIX);
    // TODO (nkant): add project version
    public static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");

    private GrpcConstants() {
        // No instances.
    }
}
