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

import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_ALL_STRATEGY;

@Deprecated
final class DefaultStreamingStrategyInfluencer implements HttpExecutionStrategyInfluencer {

    @Deprecated
    static final HttpExecutionStrategyInfluencer DEFAULT_STREAMING_STRATEGY_INFLUENCER =
            new DefaultStreamingStrategyInfluencer();

    private DefaultStreamingStrategyInfluencer() {
        // singleton
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return OFFLOAD_ALL_STRATEGY;
    }
}
