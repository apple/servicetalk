/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ConnectionInfo;

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
     * When opening a connection to a proxy, this key tells what is the actual (unresolved) target address behind the
     * proxy this connection will be established to.
     * <p>
     * To distinguish between a
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Proxy_servers_and_tunneling#http_tunneling">secure
     * HTTP proxy tunneling</a> and a clear text HTTP proxy, check presence of {@link ConnectionInfo#sslConfig()}.
     *
     * @see SingleAddressHttpClientBuilder#proxyAddress(Object)
     */
    public static final Key<Object> HTTP_TARGET_ADDRESS_BEHIND_PROXY =
            newKey("HTTP_TARGET_ADDRESS_BEHIND_PROXY", Object.class);

    private HttpContextKeys() {
        // No instances
    }
}
