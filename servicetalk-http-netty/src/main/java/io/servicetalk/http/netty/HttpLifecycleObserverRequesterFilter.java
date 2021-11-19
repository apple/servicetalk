/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter;

/**
 * An HTTP requester filter that tracks events during request/response lifecycle.
 * <p>
 * This filter is recommended to be appended as the first filter at the
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory) client builder} to account
 * for all work done by other filters. If it's preferred to get visibility in all retried or redirected requests,
 * consider adding it after {@link RetryingHttpRequesterFilter} or {@link RedirectingHttpRequesterFilter}.
 * Alternatively, it can be applied as the first filter at
 * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory) connection level}
 * to also get visibility into
 * {@link SingleAddressHttpClientBuilder#autoRetryStrategy(AutoRetryStrategyProvider) automatic retries}.
 */
public class HttpLifecycleObserverRequesterFilter extends AbstractLifecycleObserverHttpFilter implements
        StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    /**
     * Create a new instance.
     *
     * @param observer The {@link HttpLifecycleObserver observer} implementation that consumes HTTP lifecycle events.
     */
    public HttpLifecycleObserverRequesterFilter(final HttpLifecycleObserver observer) {
        super(observer, true);
    }

    @Override
    public final StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return trackLifecycle(null, request, delegate::request);
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return delegate().reserveConnection(metaData)
                        .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return trackLifecycle(connectionContext(), request, r -> delegate().request(r));
                            }
                        });
            }
        };
    }

    @Override
    public final StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return trackLifecycle(connectionContext(), request, r -> delegate().request(r));
            }
        };
    }
}
