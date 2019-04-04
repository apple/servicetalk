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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.StrategyInfluencerChainBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;

import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;

final class ClientStrategyInfuencerChainBuilder {

    private StrategyInfluencerChainBuilder connFactoryChain;
    private StrategyInfluencerChainBuilder connFilterChain;
    private StrategyInfluencerChainBuilder clientChain;

    ClientStrategyInfuencerChainBuilder() {
        connFactoryChain = new StrategyInfluencerChainBuilder();
        connFilterChain = new StrategyInfluencerChainBuilder();
        clientChain = new StrategyInfluencerChainBuilder();
    }

    private ClientStrategyInfuencerChainBuilder(ClientStrategyInfuencerChainBuilder from) {
        connFactoryChain = from.connFactoryChain.copy();
        connFilterChain = from.connFilterChain.copy();
        clientChain = from.clientChain.copy();
    }

    void add(MultiAddressHttpClientFilterFactory<?> multiAddressHttpClientFilter) {
        if (clientChain.appendIfInfluencer(multiAddressHttpClientFilter) < 0) {
            // If the filter is not influencing strategy, then the default is to offload all.
            clientChain.append(defaultStreamingInfluencer());
        }
    }

    void add(StreamingHttpClientFilterFactory clientFilter) {
        if (clientChain.appendIfInfluencer(clientFilter) < 0) {
            // If the filter is not influencing strategy, then the default is to offload all.
            clientChain.append(defaultStreamingInfluencer());
        }
    }

    void add(LoadBalancerFactory<?, StreamingHttpConnection> lb) {
        if (!clientChain.addIfInfluencerAt(0, lb)) {
            // If the load balancer is not influencing strategy, then the default is to offload all.
            clientChain.addAt(0, defaultStreamingInfluencer());
        }
    }

    void add(ConnectionFactoryFilter<?, StreamingHttpConnection> connectionFactoryFilter) {
        if (connFactoryChain.appendIfInfluencer(connectionFactoryFilter) < 0) {
            // If the filter is not influencing strategy, then the default is to offload all.
            connFactoryChain.append(defaultStreamingInfluencer());
        }
    }

    void add(StreamingHttpConnectionFilterFactory connectionFilter) {
        if (connFilterChain.appendIfInfluencer(connectionFilter) < 0) {
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

    ClientStrategyInfuencerChainBuilder copy() {
        return new ClientStrategyInfuencerChainBuilder(this);
    }
}
