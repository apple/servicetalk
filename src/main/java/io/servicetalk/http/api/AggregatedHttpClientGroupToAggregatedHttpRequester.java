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

final class AggregatedHttpClientGroupToAggregatedHttpRequester<UnresolvedAddress> extends AggregatedHttpRequester {
    private final AggregatedHttpClientGroup<UnresolvedAddress> clientGroup;
    private final Function<AggregatedHttpRequest<HttpPayloadChunk>, GroupKey<UnresolvedAddress>> requestToGroupKeyFunc;
    private final ExecutionContext executionContext;

    AggregatedHttpClientGroupToAggregatedHttpRequester(AggregatedHttpClientGroup<UnresolvedAddress> clientGroup,
            Function<AggregatedHttpRequest<HttpPayloadChunk>, GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
            ExecutionContext executionContext) {
        this.clientGroup = requireNonNull(clientGroup);
        this.requestToGroupKeyFunc = requireNonNull(requestToGroupKeyFunc);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return new Single<AggregatedHttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(
                    final Subscriber<? super AggregatedHttpResponse<HttpPayloadChunk>> subscriber) {
                final Single<AggregatedHttpResponse<HttpPayloadChunk>> response;
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
    public ExecutionContext getExecutionContext() {
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
}
