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
package io.servicetalk.grpc.internal;

import io.servicetalk.context.api.ContextMap;

import static io.servicetalk.context.api.ContextMap.Key.newKey;

/**
 * All {@link ContextMap.Key}(s) defined for gRPC.
 */
public final class GrpcContextKeys {
    /**
     * For the blocking server this key allows the router to notify an upstream filter that it is safe to consolidate
     * tailing empty data frames when set to true.
     *
     */
    public static final ContextMap.Key<Boolean> TRAILERS_ONLY_RESPONSE =
            newKey("TRAILERS_ONLY_RESPONSE", Boolean.class);

    private GrpcContextKeys() {
    }
}
