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

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;

final class HttpClientGroupToHttpRequester<UnresolvedAddress> extends HttpRequester {
    private final HttpClientGroup<UnresolvedAddress> clientGroup;
    private final Function<HttpRequest, GroupKey<UnresolvedAddress>> requestToGroupKeyFunc;
    private final ExecutionContext executionContext;

    HttpClientGroupToHttpRequester(HttpClientGroup<UnresolvedAddress> clientGroup,
                                   Function<HttpRequest,
                                                     GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
                                   ExecutionContext executionContext) {
        super(clientGroup.reqRespFactory);
        this.clientGroup = requireNonNull(clientGroup);
        this.requestToGroupKeyFunc = requireNonNull(requestToGroupKeyFunc);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public Single<HttpResponse> request(final HttpRequest request) {
        return new Single<HttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse> subscriber) {
                final Single<? extends HttpResponse> response;
                try {
                    response = clientGroup.request(requestToGroupKeyFunc.apply(request), request);
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
    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public Completable onClose() {
        return clientGroup.onClose();
    }

    @Override
    public Completable closeAsync() {
        return clientGroup.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return clientGroup.closeAsyncGracefully();
    }
}
