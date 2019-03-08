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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.error;

final class ConditionalHttpClientFilter extends StreamingHttpClientFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final StreamingHttpClient predicatedClient;
    private final ListenableAsyncCloseable closeable;

    ConditionalHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                final StreamingHttpClient predicatedClient,
                                final StreamingHttpClient client) {
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
        boolean b;
        try {
            b = predicate.test(request);
        } catch (Throwable t) {
            return error(t);
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
