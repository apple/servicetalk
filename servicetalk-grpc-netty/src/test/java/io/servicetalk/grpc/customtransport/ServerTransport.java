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
package io.servicetalk.grpc.customtransport;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;

import io.netty.channel.Channel;

import javax.annotation.Nullable;

interface ServerTransport {
    // Channel may be optional depending on what capabilities you want to provide to the Service.
    // GrpcServiceContext is part of the gRPC service interface which is 1:1 with a child channel (h2 stream) in
    // ServiceTalk. The service therefore has the ability to close/reset the channel/stream and listen for its
    // closure.
    Publisher<Buffer> handle(Channel channel,
                             @Nullable String clientId, String method,
                             Publisher<Buffer> requestMessages);
}
