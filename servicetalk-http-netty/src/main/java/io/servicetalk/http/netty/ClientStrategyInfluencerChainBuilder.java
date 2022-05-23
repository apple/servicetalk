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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.http.api.ConnectAndHttpExecutionStrategy;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

final class ClientStrategyInfluencerChainBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStrategyInfluencerChainBuilder.class);

    @Nullable
    private ConnectAndHttpExecutionStrategy connFactoryChain;
    @Nullable
    private HttpExecutionStrategy connFilterChain;
    @Nullable
    private HttpExecutionStrategy clientChain;

    ClientStrategyInfluencerChainBuilder() {
        connFactoryChain = null;
        connFilterChain = null;
        clientChain = null;
    }

    private ClientStrategyInfluencerChainBuilder(ClientStrategyInfluencerChainBuilder from) {
        connFactoryChain = from.connFactoryChain;
        connFilterChain = from.connFilterChain;
        clientChain = from.clientChain;
    }

    void add(StreamingHttpClientFilterFactory clientFilter) {
        add(StreamingHttpClientFilterFactory.class, clientFilter, clientFilter.requiredOffloads());
    }

    void add(HttpLoadBalancerFactory<?> lb) {
        add(HttpLoadBalancerFactory.class, lb, lb.requiredOffloads());
    }

    private void add(Class<?> clazz, ExecutionStrategyInfluencer<?> influencer, HttpExecutionStrategy strategy) {
        if (offloadNever() == strategy) {
            offloadNeverWarning(clazz, influencer);
            strategy = offloadNone();
        }
        if (defaultStrategy() == strategy) {
            defaultStrategyWarning(clazz, influencer);
            strategy = offloadAll();
        }
        @Nullable
        final HttpExecutionStrategy clientChain = this.clientChain;
        this.clientChain = null != clientChain ? clientChain.merge(strategy) : strategy;
        logIfChanges(clazz, influencer, clientChain, this.clientChain);
    }

    void add(ConnectionFactoryFilter<?, FilterableStreamingHttpConnection> connectionFactoryFilter) {
        ExecutionStrategy filterOffloads = connectionFactoryFilter.requiredOffloads();
        if (offloadNever() == filterOffloads) {
            offloadNeverWarning(ConnectionFactoryFilter.class, connectionFactoryFilter);
            filterOffloads = offloadNone();
        }
        if (defaultStrategy() == filterOffloads) {
            defaultStrategyWarning(ConnectionFactoryFilter.class, connectionFactoryFilter);
            filterOffloads = offloadAll();
        }
        @Nullable
        final ConnectAndHttpExecutionStrategy connFactoryChain = this.connFactoryChain;
        this.connFactoryChain = null != connFactoryChain ?
                 connFactoryChain.merge(filterOffloads) : ConnectAndHttpExecutionStrategy.from(filterOffloads);
        logIfChanges(ConnectionFactoryFilter.class, connectionFactoryFilter, connFactoryChain, this.connFactoryChain);
    }

    void add(StreamingHttpConnectionFilterFactory connectionFilter) {
        HttpExecutionStrategy filterOffloads = connectionFilter.requiredOffloads();
        if (offloadNever() == filterOffloads) {
            offloadNeverWarning(StreamingHttpConnectionFilterFactory.class, connectionFilter);
            filterOffloads = offloadNone();
        }
        if (defaultStrategy() == filterOffloads) {
            defaultStrategyWarning(StreamingHttpConnectionFilterFactory.class, connectionFilter);
            filterOffloads = offloadAll();
        }
        if (filterOffloads.hasOffloads()) {
            @Nullable
            HttpExecutionStrategy connFilterChain = this.connFilterChain;
            connFilterChain = null != connFilterChain ? connFilterChain.merge(filterOffloads) : filterOffloads;
            logIfChanges(ConnectionFactoryFilter.class, connectionFilter, connFilterChain, this.connFilterChain);
        }
    }

    HttpExecutionStrategy buildForClient(HttpExecutionStrategy transportStrategy) {
        HttpExecutionStrategy chainStrategy = clientChain;
        if (null != connFilterChain) {
            chainStrategy = null != chainStrategy ? chainStrategy.merge(connFilterChain) : connFilterChain;
        }
        if (null != connFactoryChain) {
            HttpExecutionStrategy connectionFactoryStrategy = HttpExecutionStrategy.from(buildForConnectionFactory());
            chainStrategy = null != chainStrategy ?
                    chainStrategy.merge(connectionFactoryStrategy) : connectionFactoryStrategy;
        }

        return (null == chainStrategy || !chainStrategy.hasOffloads()) ?
                transportStrategy :
                defaultStrategy() == transportStrategy || !transportStrategy.hasOffloads() ?
                        chainStrategy : chainStrategy.merge(transportStrategy);
    }

    ExecutionStrategy buildForConnectionFactory() {
        return null == connFactoryChain ?
                ExecutionStrategy.offloadNone() :
                defaultStrategy() != connFactoryChain.httpStrategy() ?
                        ConnectExecutionStrategy.offloadNone() != connFactoryChain.connectStrategy() ?
                                connFactoryChain : connFactoryChain.httpStrategy() :
                        ConnectExecutionStrategy.offloadNone() != connFactoryChain.connectStrategy() ?
                                connFactoryChain.connectStrategy() : ExecutionStrategy.offloadNone();
    }

    ClientStrategyInfluencerChainBuilder copy() {
        return new ClientStrategyInfluencerChainBuilder(this);
    }

    private static void offloadNeverWarning(final Class<?> clazz, final ExecutionStrategyInfluencer<?> influencer) {
        LOGGER.warn("{}#requiredOffloads() returns offloadNever(), which is unexpected. offloadNone() should be used " +
                        "instead. Making automatic adjustment, update the {} to avoid this warning.",
                influencer, clazz.getSimpleName());
    }

    private static void defaultStrategyWarning(final Class<?> clazz, final ExecutionStrategyInfluencer<?> influencer) {
        LOGGER.warn("{}#requiredOffloads() returns defaultStrategy(), which is unexpected. " +
                        "offloadAll() (safe default) or more appropriate custom strategy should be used instead." +
                        "Making automatic adjustment, update the {} to avoid this warning.",
                influencer, clazz.getSimpleName());
    }

    private static void logIfChanges(Class<?> clazz, ExecutionStrategyInfluencer<?> influencer,
                                     @Nullable ExecutionStrategy before, @Nullable ExecutionStrategy after) {
        if (before != after) {
            LOGGER.debug("{} '{}' changes execution strategy from '{}' to '{}'", clazz, influencer, before, after);
        }
    }
}
