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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcProtocolConfig;
import io.servicetalk.http.netty.H2ProtocolConfig;

/**
 * {@link GrpcProtocolConfig} for <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a> transport.
 */
public interface GrpcH2ProtocolConfig extends H2ProtocolConfig {
}
