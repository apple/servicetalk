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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.anyStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;

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
        HttpExecutionStrategy filterOffloads = clientFilter.requiredOffloads();
        if (defaultStrategy() == filterOffloads || noOffloadsStrategy() == filterOffloads) {
            LOGGER.warn("Ignoring illegal filter required strategy ({}) for {}", filterOffloads, clientFilter);
            filterOffloads = anyStrategy();
        }
        if (defaultStrategy() != filterOffloads && filterOffloads.hasOffloads()) {
            clientChain = null != clientChain ? clientChain.merge(filterOffloads) : filterOffloads;
        }
    }

    void add(HttpLoadBalancerFactory<?> lb) {
        HttpExecutionStrategy filterOffloads = lb.requiredOffloads();
        if (defaultStrategy() == filterOffloads || noOffloadsStrategy() == filterOffloads) {
            LOGGER.warn("Ignoring illegal load balancer required strategy ({}) for {}", filterOffloads, lb);
            filterOffloads = anyStrategy();
        }
        if (defaultStrategy() != filterOffloads && filterOffloads.hasOffloads()) {
            clientChain = null != clientChain ? clientChain.merge(filterOffloads) : filterOffloads;
        }
    }

    void add(ConnectionFactoryFilter<?, FilterableStreamingHttpConnection> connectionFactoryFilter) {
        ExecutionStrategy filterOffloads = connectionFactoryFilter.requiredOffloads();
        if (defaultStrategy() == filterOffloads || noOffloadsStrategy() == filterOffloads) {
            LOGGER.warn("Ignoring illegal connection factory required strategy ({}) for {}",
                    filterOffloads, connectionFactoryFilter);
            filterOffloads = anyStrategy();
        }
        if (defaultStrategy() != filterOffloads && filterOffloads.hasOffloads()) {
            connFactoryChain = null != connFactoryChain ?
                    connFactoryChain.merge(filterOffloads) : ConnectAndHttpExecutionStrategy.from(filterOffloads);
        }
    }

    void add(StreamingHttpConnectionFilterFactory connectionFilter) {
        HttpExecutionStrategy filterOffloads = connectionFilter.requiredOffloads();
        if (defaultStrategy() == filterOffloads || noOffloadsStrategy() == filterOffloads) {
            LOGGER.warn("Ignoring illegal connection filter required strategy ({}) for {}",
                    filterOffloads, connectionFilter);
            filterOffloads = anyStrategy();
        }
        if (defaultStrategy() != filterOffloads && filterOffloads.hasOffloads()) {
            connFilterChain = null != connFilterChain ? connFilterChain.merge(filterOffloads) : filterOffloads;
        }
    }

    HttpExecutionStrategy buildForClient(HttpExecutionStrategy transportStrategy) {
        // We merge against default in case transport strategy is default.
        return transportStrategy.merge(null != clientChain ? clientChain : defaultStrategy())
                .merge(null != connFilterChain ? connFilterChain : defaultStrategy())
                .merge(null != connFactoryChain ?
                        HttpExecutionStrategy.from(buildForConnectionFactory()) : defaultStrategy());
    }

    ExecutionStrategy buildForConnectionFactory() {
        return null == connFactoryChain ?
                ExecutionStrategy.anyStrategy() :
                defaultStrategy() != connFactoryChain.httpStrategy() ?
                        ConnectExecutionStrategy.anyStrategy() != connFactoryChain.connectStrategy() ?
                                connFactoryChain : connFactoryChain.httpStrategy() :
                        ConnectExecutionStrategy.anyStrategy() != connFactoryChain.connectStrategy() ?
                                connFactoryChain.connectStrategy() : ExecutionStrategy.anyStrategy();
    }

    ClientStrategyInfluencerChainBuilder copy() {
        return new ClientStrategyInfluencerChainBuilder(this);
    }
}
