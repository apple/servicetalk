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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * A builder for {@link HttpConnection} objects.
 *
 * @param <ResolvedAddress> A resolved address that can be used for connecting
 * @param <I> The type of content of the request
 * @param <O> The type of content of the response
 */
public interface HttpConnectionBuilder<ResolvedAddress, I, O> {

    /**
     * Create a new {@link HttpConnection}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link HttpConnection}s.
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     */
    Single<HttpConnection<I, O>> build(ExecutionContext executionContext, ResolvedAddress resolvedAddress);

    /**
     * Convert this {@link HttpConnectionBuilder} to a {@link ConnectionFactory}. This can be useful to take advantage
     * of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @param executionContext {@link ExecutionContext} to use for the connections.
     * @param connectionFilter {@link Function} to decorate an {@link HttpConnection} for the purpose of filtering.
     * @param <FilteredConnection> type of {@link HttpConnection} after filtering
     * @return A {@link ConnectionFactory} that will use the {@link #build(ExecutionContext, Object)}
     * method to create new {@link HttpConnection} objects.
     */
    default <FilteredConnection extends HttpConnection<I, O>> ConnectionFactory<ResolvedAddress, FilteredConnection>
        asConnectionFactory(ExecutionContext executionContext,
                            Function<HttpConnection<I, O>, FilteredConnection> connectionFilter) {

        return new ConnectionFactory<ResolvedAddress, FilteredConnection>() {
            private final CompletableProcessor onClose = new CompletableProcessor();
            private final Completable closeAsync = new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    onClose.onComplete();
                    onClose.subscribe(subscriber);
                }
            };

            @Override
            public Single<FilteredConnection> newConnection(ResolvedAddress resolvedAddress) {
                return build(executionContext, resolvedAddress).map(connectionFilter);
            }

            @Override
            public Completable onClose() {
                return onClose;
            }

            @Override
            public Completable closeAsync() {
                return closeAsync;
            }
        };
    }
}
