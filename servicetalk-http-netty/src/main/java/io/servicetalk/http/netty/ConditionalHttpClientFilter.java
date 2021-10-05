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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;

final class ConditionalHttpClientFilter extends StreamingHttpClientFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final FilterableStreamingHttpClient predicatedClient;
    private final ListenableAsyncCloseable closeable;

    ConditionalHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                final FilterableStreamingHttpClient predicatedClient,
                                final FilterableStreamingHttpClient client) {
        super(client);
        this.predicate = predicate;
        this.predicatedClient = predicatedClient;
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        compositeCloseable.append(predicatedClient);
        compositeCloseable.append(client);
        closeable = toListenableAsyncCloseable(compositeCloseable);
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        final boolean b;
        try {
            b = predicate.test(request);
        } catch (Throwable t) {
            return failed(t);
        }

        if (b) {
            return predicatedClient.request(strategy, request);
        }

        return delegate.request(strategy, request);
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return closeable.onClose();
    }
}
