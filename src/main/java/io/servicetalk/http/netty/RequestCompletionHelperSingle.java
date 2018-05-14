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
package io.servicetalk.http.netty;

import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

// TODO We can't make RequestConcurrencyController#requestFinished() work reliably with cancel() of HttpResponse.
// This code will prematurely release connections when the cancel event is racing with the onComplete() of the
// HttpRequest and gets ignored. This means that from the LoadBalancer's perspective the connection is free however
// the user may still be subscribing and consume the payload.
// This may be acceptable for now, with DefaultPipelinedConnection rejecting requests after a set maximum number and
// this race condition expected to happen infrequently. For NonPipelinedHttpConnections there is no cap on
// concurrent requests so we can expect to be making a pipelined request in this case.
final class RequestCompletionHelperSingle extends Single<HttpResponse<HttpPayloadChunk>> {
    private static final AtomicIntegerFieldUpdater<RequestCompletionHelperSingle> terminatedUpdater =
            newUpdater(RequestCompletionHelperSingle.class, "terminated");

    private final RequestConcurrencyController limiter;
    private final Single<HttpResponse<HttpPayloadChunk>> response;

    @SuppressWarnings("unused")
    private volatile int terminated;

    RequestCompletionHelperSingle(Single<HttpResponse<HttpPayloadChunk>> response,
                                  RequestConcurrencyController limiter) {
        this.response = requireNonNull(response);
        this.limiter = requireNonNull(limiter);
    }

    private void finished() {
        // Avoid double counting
        if (terminatedUpdater.compareAndSet(this, 0, 1)) {
            limiter.requestFinished();
        }
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber) {
        response.doBeforeError(e -> finished())
                .doBeforeCancel(this::finished)
                .map(resp -> resp.transformPayloadBody(payload -> payload.doBeforeFinally(this::finished)))
                .subscribe(subscriber);
    }
}
