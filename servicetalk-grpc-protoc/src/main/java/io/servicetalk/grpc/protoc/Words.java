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
package io.servicetalk.grpc.protoc;

final class Words {
    static final String append = "append";
    static final String appendServiceFilter = append + "ServiceFilter";
    static final String bind = "bind";
    static final String builder = "builder";
    static final String client = "client";
    static final String close = "close";
    static final String closeable = close + "able";
    static final String closeAsync = close + "Async";
    static final String closeAsyncGracefully = closeAsync + "Gracefully";
    static final String closeGracefully = close + "Gracefully";
    static final String executionContext = "executionContext";
    static final String ctx = "ctx";
    static final String delegate = "delegate";
    static final String existing = "existing";
    static final String factory = "factory";
    static final String onClose = "onClose";
    static final String metadata = "metadata";
    static final String request = "request";
    static final String responseWriter = "responseWriter";
    static final String routes = "routes";
    static final String rpc = "rpc";
    static final String initSerializationProvider = "initSerializationProvider";
    static final String addService = "addService";
    static final String registerRoutes = "registerRoutes";
    static final String service = "service";
    static final String strategy = "strategy";
    static final String requestEncoding = "requestEncoding";
    static final String timeout = "timeout";
    static final String supportedMessageCodings = "supportedMessageCodings";
    static final String isSupportedMessageCodingsEmpty = "isSupportedMessageCodingsEmpty";
    static final String bufferDecoderGroup = "bufferDecoderGroup";
    static final String bufferEncoders = "bufferEncoders";
    static final String strategyFactory = strategy + "Factory";
    static final String methodDescriptor = "methodDescriptor";
    static final String Service = "Service";
    static final String Blocking = "Blocking";
    static final String Builder = "Builder";
    static final String Call = "Call";
    static final String Default = "Default";
    static final String Metadata = "Metadata";
    static final String Factory = "Factory";
    static final String Filter = "Filter";
    static final String Rpc = "Rpc";
    static final String To = "To";
    static final String PROTO_CONTENT_TYPE = "+proto";
    static final String INSTANCE = "INSTANCE";
    static final String PROTOBUF = "PROTOBUF";
    static final String RPC_PATH = "PATH";
    static final String COMMENT_PRE_TAG = "<pre>";
    static final String COMMENT_POST_TAG = "</pre>";
    static final String JAVADOC_PARAM = "@param ";
    static final String JAVADOC_RETURN = "@return ";
    static final String JAVADOC_THROWS = "@throws ";
    static final String JAVADOC_DEPRECATED = "@deprecated";

    private Words() {
        // no instance
    }
}
