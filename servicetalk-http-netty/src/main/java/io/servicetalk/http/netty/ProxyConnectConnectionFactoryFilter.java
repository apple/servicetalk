/*
 * Copyright © 2019-2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DefaultContextMap;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpContextKeys;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_TARGET_ADDRESS_BEHIND_PROXY;

/**
 * A {@link ConnectionFactoryFilter} that is prepended before any user-defined filters for the purpose of setting a
 * {@link HttpContextKeys#HTTP_TARGET_ADDRESS_BEHIND_PROXY} key.
 * <p>
 * The actual logic to do a proxy connect was moved to {@link ProxyConnectLBHttpConnectionFactory}.
 * <p>
 * This filter can be removed when {@link HttpContextKeys#HTTP_TARGET_ADDRESS_BEHIND_PROXY} key is deprecated and
 * removed.
 *
 * @param <ResolvedAddress> The type of resolved addresses that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 * @deprecated This filter won't be required after {@link HttpContextKeys#HTTP_TARGET_ADDRESS_BEHIND_PROXY} is removed.
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
@SuppressWarnings("DeprecatedIsStillUsed")
final class ProxyConnectConnectionFactoryFilter<ResolvedAddress, C extends FilterableStreamingHttpConnection>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConnectConnectionFactoryFilter.class);

    private final String connectAddress;

    ProxyConnectConnectionFactoryFilter(final CharSequence connectAddress, final ExecutionStrategy connectStrategy) {
        this.connectAddress = connectAddress.toString();
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return new ProxyFilter(original);
    }

    private final class ProxyFilter extends DelegatingConnectionFactory<ResolvedAddress, C> {

        private ProxyFilter(final ConnectionFactory<ResolvedAddress, C> delegate) {
            super(delegate);
        }

        @Override
        public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                       @Nullable ContextMap context,
                                       @Nullable final TransportObserver observer) {
            return Single.defer(() -> {
                final ContextMap contextMap = context != null ? context : new DefaultContextMap();
                logUnexpectedAddress(contextMap.put(HTTP_TARGET_ADDRESS_BEHIND_PROXY, connectAddress),
                        connectAddress, LOGGER);
                // The rest of the logic was moved to ProxyConnectLBHttpConnectionFactory
                return delegate().newConnection(resolvedAddress, contextMap, observer)
                        .shareContextOnSubscribe();
            });
        }
    }

    static void logUnexpectedAddress(@Nullable final Object current, final Object expected, final Logger logger) {
        if (current != null && !expected.equals(current)) {
            logger.info("Observed unexpected value for {}: {}, overridden with: {}",
                    HTTP_TARGET_ADDRESS_BEHIND_PROXY, current, expected);
        }
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }
}
