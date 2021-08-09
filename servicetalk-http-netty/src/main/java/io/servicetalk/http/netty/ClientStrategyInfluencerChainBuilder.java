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
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.StrategyInfluencerChainBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;

import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;

final class ClientStrategyInfluencerChainBuilder {

    private final StrategyInfluencerChainBuilder connFactoryChain;
    private final StrategyInfluencerChainBuilder connFilterChain;
    private final StrategyInfluencerChainBuilder clientChain;

    ClientStrategyInfluencerChainBuilder() {
        connFactoryChain = new StrategyInfluencerChainBuilder();
        connFilterChain = new StrategyInfluencerChainBuilder();
        clientChain = new StrategyInfluencerChainBuilder();
    }

    private ClientStrategyInfluencerChainBuilder(ClientStrategyInfluencerChainBuilder from) {
        connFactoryChain = from.connFactoryChain.copy();
        connFilterChain = from.connFilterChain.copy();
        clientChain = from.clientChain.copy();
    }

    void add(StreamingHttpClientFilterFactory clientFilter) {
        if (!clientChain.appendIfInfluencer(clientFilter)) {
            // If the filter is not influencing strategy, then the default is to offload all.
            clientChain.append(defaultStreamingInfluencer());
        }
    }

    void add(HttpLoadBalancerFactory<?> lb) {
        if (!clientChain.prependIfInfluencer(lb)) {
            // If the load balancer is not influencing strategy, then the default is to offload all.
            clientChain.prepend(defaultStreamingInfluencer());
        }
    }

    void add(ConnectionFactoryFilter<?, FilterableStreamingHttpConnection> connectionFactoryFilter) {
        if (!connFactoryChain.appendIfInfluencer(connectionFactoryFilter)) {
            // If the filter is not influencing strategy, then the default is to offload all.
            connFactoryChain.append(defaultStreamingInfluencer());
        }
    }

    void add(StreamingHttpConnectionFilterFactory connectionFilter) {
        if (!connFilterChain.appendIfInfluencer(connectionFilter)) {
            // If the filter is not influencing strategy, then the default is to offload all.
            connFilterChain.append(defaultStreamingInfluencer());
        }
    }

    HttpExecutionStrategyInfluencer buildForClient(HttpExecutionStrategy transportStrategy) {
        StrategyInfluencerChainBuilder forClient = new StrategyInfluencerChainBuilder();
        forClient.append(buildForConnectionFactory(transportStrategy));
        forClient.append(clientChain.build());
        return forClient.build();
    }

    HttpExecutionStrategyInfluencer buildForConnectionFactory(HttpExecutionStrategy transportStrategy) {
        StrategyInfluencerChainBuilder forConnFactory = new StrategyInfluencerChainBuilder();
        forConnFactory.append(connFilterChain.build());
        forConnFactory.append(connFactoryChain.build(transportStrategy));
        return forConnFactory.build();
    }

    ClientStrategyInfluencerChainBuilder copy() {
        return new ClientStrategyInfluencerChainBuilder(this);
    }
}
