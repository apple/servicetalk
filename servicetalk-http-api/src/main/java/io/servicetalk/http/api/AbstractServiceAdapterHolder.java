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
package io.servicetalk.http.api;

import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;

abstract class AbstractServiceAdapterHolder implements StreamingHttpService, ServiceAdapterHolder {

    private final HttpExecutionStrategy serviceInvocationStrategy;

    protected AbstractServiceAdapterHolder(final HttpExecutionStrategy serviceInvocationStrategy) {
        this.serviceInvocationStrategy = serviceInvocationStrategy;
    }

    @Override
    public StreamingHttpService adaptor() {
        return this;
    }

    @Override
    public HttpExecutionStrategy serviceInvocationStrategy() {
        return serviceInvocationStrategy;
    }

    /**
     * If the influencer returns default then apply our own default otherwise use the strategy provided by influencer.
     *
     * @param influencer the strategy influencer
     * @param defaultStrategy our default strategy
     * @return the strategy to use.
     */
    static HttpExecutionStrategy influencedStrategy(HttpExecutionStrategyInfluencer influencer,
                                                    HttpExecutionStrategy defaultStrategy) {
        HttpExecutionStrategy influenced = influencer.influenceStrategy(HttpExecutionStrategies.defaultStrategy());
        return HttpExecutionStrategies.defaultStrategy() == influenced ? defaultStrategy : influenced;
    }
}
