/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcRequestObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcResponseObserver;

final class NoopGrpcLifecycleObservers {

    static final GrpcRequestObserver NOOP_GRPC_REQUEST_OBSERVER = new GrpcRequestObserver() { };
    static final GrpcResponseObserver NOOP_GRPC_RESPONSE_OBSERVER = new GrpcResponseObserver() { };

    private NoopGrpcLifecycleObservers() {
        // No instances
    }
}
