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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

/**
 * A {@link StreamingHttpClient} that supports filtering.
 */
public interface FilterableStreamingHttpClient extends
            StreamingHttpRequester, ExecutionStrategyInfluencer<HttpExecutionStrategy> {
    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides a {@link FilterableReservedStreamingHttpConnection} upon completion.
     */
    Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(HttpRequestMetaData metaData);

    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // safe default--implementations are expected to override
        return HttpExecutionStrategies.offloadAll();
    }
}
