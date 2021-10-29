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

import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * An extension of {@link ExecutionContext} for <a href="https://www.grpc.io">gRPC</a>.
 */
public interface GrpcExecutionContext extends HttpExecutionContext {

    /**
     * Returns the {@link GrpcExecutionStrategy} associated with this context.
     *
     * @return The {@link GrpcExecutionStrategy} associated with this context.
     */
    @Override
    GrpcExecutionStrategy executionStrategy();

    /**
     * Creates a new {@link GrpcExecutionContext} from the passed {@link ExecutionContext}.
     *
     * @param executionContext source {@link ExecutionContext}.
     *
     * @return New {@link GrpcExecutionContext} using the passed {@link ExecutionContext}.
     */
    static GrpcExecutionContext from(ExecutionContext executionContext) {
        GrpcExecutionContext result;
        if (executionContext instanceof GrpcExecutionContext) {
            result = (GrpcExecutionContext) executionContext;
        } else if (executionContext instanceof HttpExecutionContext) {
            result = new DefaultGrpcExecutionContext((HttpExecutionContext) executionContext);
        } else {
            HttpExecutionStrategy httpExecutionStrategy =
                    executionContext.executionStrategy() instanceof HttpExecutionStrategy ?
                            GrpcExecutionStrategy.from((HttpExecutionStrategy) executionContext.executionStrategy()) :
                            HttpExecutionStrategies.defaultStrategy();
            result = new DefaultGrpcExecutionContext(new DefaultHttpExecutionContext(
                    executionContext.bufferAllocator(),
                    executionContext.ioExecutor(),
                    executionContext.executor(),
                    httpExecutionStrategy
            ));
        }

        return result;
    }
}
