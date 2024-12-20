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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.TransportObserver;

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
     * @see SingleAddressHttpClientBuilder#proxyConfig(ProxyConfig)
     * @deprecated Use {@link TransportObserverConnectionFactoryFilter} to configure {@link TransportObserver} and then
     * listen {@link ConnectionObserver#onProxyConnect(Object)} callback to distinguish between a regular connection and
     * a connection to the secure HTTP proxy tunnel. For clear text HTTP proxies, consider installing a custom client
     * filter that will populate {@link HttpRequestMetaData#context()} with a similar key or reach out to the
     * ServiceTalk developers to discuss ideas.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static final Key<Object> HTTP_TARGET_ADDRESS_BEHIND_PROXY =  // FIXME: 0.43 - remove deprecated constant
            newKey("HTTP_TARGET_ADDRESS_BEHIND_PROXY", Object.class);

    /**
     * If set to true, forces creating a new connection versus potentially selecting an already established one.
     * <p>
     * This key is only available when reserving a connection and will be ignored when performing a regular
     * request on the client (for example through {@link HttpClient#request(HttpRequest)}).
     *
     * @see HttpClient#reserveConnection(HttpRequestMetaData)
     */
    public static final Key<Boolean> HTTP_FORCE_NEW_CONNECTION =
            newKey("HTTP_FORCE_NEW_CONNECTION", Boolean.class);

    private HttpContextKeys() {
        // No instances
    }
}
