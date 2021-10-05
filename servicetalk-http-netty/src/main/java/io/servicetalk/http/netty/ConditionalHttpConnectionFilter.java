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
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;

final class ConditionalHttpConnectionFilter extends StreamingHttpConnectionFilter {
    private final Predicate<StreamingHttpRequest> predicate;
    private final FilterableStreamingHttpConnection predicatedConnection;
    private final ListenableAsyncCloseable closeable;

    ConditionalHttpConnectionFilter(final Predicate<StreamingHttpRequest> predicate,
                                    final FilterableStreamingHttpConnection predicatedConnection,
                                    final FilterableStreamingHttpConnection connection) {
        super(connection);
        this.predicate = predicate;
        this.predicatedConnection = predicatedConnection;
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        compositeCloseable.append(predicatedConnection);
        compositeCloseable.append(connection);
        closeable = toListenableAsyncCloseable(compositeCloseable);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        final boolean b;
        try {
            b = predicate.test(request);
        } catch (Throwable t) {
            return failed(t);
        }

        if (b) {
            return predicatedConnection.request(strategy, request);
        }

        return delegate().request(strategy, request);
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
