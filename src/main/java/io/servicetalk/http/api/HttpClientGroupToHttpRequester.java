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

import io.servicetalk.client.api.DefaultGroupKey;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;

/**
 * Class for {@link HttpClientGroup#asRequester(Function, ExecutionContext)} transformation.
 */
final class HttpClientGroupToHttpRequester<UnresolvedAddress, I, O> extends HttpRequester<I, O> {

    private final ConcurrentMap<UnresolvedAddress, GroupKey<UnresolvedAddress>> keysMap = new ConcurrentHashMap<>();

    private final HttpClientGroup<UnresolvedAddress, I, O> clientGroup;
    private final Function<HttpRequest<I>, UnresolvedAddress> addressExtractor;
    private final ExecutionContext executionContext;

    HttpClientGroupToHttpRequester(final HttpClientGroup<UnresolvedAddress, I, O> clientGroup,
                                           final Function<HttpRequest<I>, UnresolvedAddress> addressExtractor,
                                           final ExecutionContext executionContext) {
        this.clientGroup = requireNonNull(clientGroup);
        this.addressExtractor = requireNonNull(addressExtractor);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
        return new Single<HttpResponse<O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<O>> subscriber) {
                final Single<HttpResponse<O>> response;
                try {
                    final UnresolvedAddress address = addressExtractor.apply(request);
                    GroupKey<UnresolvedAddress> key = keysMap.get(address);
                    key = key != null ? key : keysMap.computeIfAbsent(address,
                            a -> new DefaultGroupKey<>(a, executionContext));
                    response = clientGroup.request(key, request);
                } catch (final Throwable t) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(t);
                    return;
                }
                response.subscribe(subscriber);
            }
        };
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    @Override
    public Completable onClose() {
        return clientGroup.onClose().doAfterComplete(keysMap::clear);
    }

    @Override
    public Completable closeAsync() {
        return clientGroup.closeAsync();
    }
}
