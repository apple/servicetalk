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
package io.servicetalk.http.api;

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;

import static io.servicetalk.context.api.ContextMap.Key.newKey;

/**
 * All {@link ContextMap.Key}(s) defined for HTTP.
 */
public final class HttpContextKeys {

    /**
     * Allows using a custom {@link HttpExecutionStrategy} for the HTTP message execution, when present in the meta-data
     * {@link HttpMetaData#context() context}. Otherwise, an automatically inferred strategy will be used by a client
     * or server.
     */
    public static final Key<HttpExecutionStrategy> HTTP_EXECUTION_STRATEGY_KEY =
            newKey("HTTP_EXECUTION_STRATEGY_KEY", HttpExecutionStrategy.class);

    /**
     * Tracks the original API mode used for client API conversions
     */
    public static final Key<HttpApiConversions.ClientAPI> HTTP_CLIENT_API_KEY =
            newKey("HTTP_CLIENT_API_KEY", HttpApiConversions.ClientAPI.class);

    private HttpContextKeys() {
        // No instances
    }
}
