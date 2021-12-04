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
package io.servicetalk.grpc.api;

import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;

/**
 * An execution strategy for <a href="https://www.grpc.io">gRPC</a> client and servers.
 */
public interface GrpcExecutionStrategy extends HttpExecutionStrategy {

    /**
     * Creates a new {@link GrpcExecutionStrategy} from the passed {@link HttpExecutionStrategy}.
     *
     * @param httpExecutionStrategy {@link HttpExecutionStrategy} to use.
     *
     * @return New {@link GrpcExecutionStrategy} using the passed {@link HttpExecutionStrategy}.
     */
    static GrpcExecutionStrategy from(HttpExecutionStrategy httpExecutionStrategy) {
        GrpcExecutionStrategy result;
        if (httpExecutionStrategy instanceof GrpcExecutionStrategy) {
            result = (GrpcExecutionStrategy) httpExecutionStrategy;
        } else if (HttpExecutionStrategies.offloadNever() == httpExecutionStrategy) {
            result = GrpcExecutionStrategies.offloadNever();
        } else if (HttpExecutionStrategies.defaultStrategy() == httpExecutionStrategy) {
            result = GrpcExecutionStrategies.defaultStrategy();
        } else {
            result = new DefaultGrpcExecutionStrategy(httpExecutionStrategy);
        }

        return result;
    }
}
