/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.AutoRetryStrategyProvider.AutoRetryStrategy;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

/**
 * A {@link StreamingHttpClient} filter that will account for transient failures introduced by a {@link LoadBalancer}
 * not being ready for {@link #request(StreamingHttpRequest)} and retry/delay requests until the
 * {@link LoadBalancer} is ready.
 */
final class AutoRetryFilter extends StreamingHttpClientFilter {
    private final AutoRetryStrategy retryStrategy;

    AutoRetryFilter(final FilterableStreamingHttpClient next, final AutoRetryStrategy retryStrategy) {
        super(next);
        this.retryStrategy = retryStrategy;
    }

    @Override
    public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
            final HttpRequestMetaData metaData) {
        return delegate().reserveConnection(metaData)
                .retryWhen(retryStrategy);
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final StreamingHttpRequest request) {
        return delegate.request(request).retryWhen(retryStrategy);
    }
}
