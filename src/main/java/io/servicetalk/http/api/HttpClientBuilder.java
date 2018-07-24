/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ExecutionContext;

/**
 * A builder of {@link HttpClient} objects.
 */
public interface HttpClientBuilder {

    /**
     * Build a new {@link HttpClient}.
     *
     * @param executionContext {@link ExecutionContext} used for {@link HttpClient#getExecutionContext()} and to build
     * new {@link HttpConnection}s.
     * @return A new {@link HttpClient}
     */
    HttpClient build(ExecutionContext executionContext);

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link HttpClient}
     * @see #build(ExecutionContext)
     */
    HttpClient build();

    /**
     * Build a new {@link AggregatedHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} used for {@link AggregatedHttpClient#getExecutionContext()} and
     * to build new {@link HttpConnection}s.
     * @return A new {@link AggregatedHttpClient}
     */
    default AggregatedHttpClient buildAggregated(ExecutionContext executionContext) {
        return build(executionContext).asAggregatedClient();
    }

    /**
     * Build a new {@link AggregatedHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link AggregatedHttpClient}
     * @see #buildAggregated(ExecutionContext)
     */
    default AggregatedHttpClient buildAggregated() {
        return build().asAggregatedClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingHttpConnection}s.
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking(ExecutionContext executionContext) {
        return build(executionContext).asBlockingClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingHttpClient}
     * @see #buildBlocking(ExecutionContext)
     */
    default BlockingHttpClient buildBlocking() {
        return build().asBlockingClient();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpClient}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingAggregatedHttpConnection}s.
     * @return {@link BlockingAggregatedHttpClient}
     */
    default BlockingAggregatedHttpClient buildBlockingAggregated(ExecutionContext executionContext) {
        return build(executionContext).asBlockingAggregatedClient();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingAggregatedHttpClient}
     * @see #buildBlockingAggregated(ExecutionContext)
     */
    default BlockingAggregatedHttpClient buildBlockingAggregated() {
        return build().asBlockingAggregatedClient();
    }
}
