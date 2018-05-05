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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Makes the wrapped {@link HttpConnection} aware of the {@link LoadBalancer}.
 */
class LoadBalancedHttpConnection extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> {

    private final ConcurrentReservedResource reserved;
    private final HttpConnection<HttpPayloadChunk, HttpPayloadChunk> delegate;

    LoadBalancedHttpConnection(HttpConnection<HttpPayloadChunk, HttpPayloadChunk> delegate) {
        this.delegate = requireNonNull(delegate);
        reserved = new ConcurrentReservedResource(getSettingStream(SettingKey.MAX_CONCURRENCY), this);
    }

    /**
     * Visible for testing.
     * @param delegate the {@link HttpConnection} to delegate requests to
     * @param reserved the reserved resource manager used for mocking behavior
     */
    LoadBalancedHttpConnection(HttpConnection<HttpPayloadChunk, HttpPayloadChunk> delegate,
                               ConcurrentReservedResource reserved) {
        this.delegate = requireNonNull(delegate);
        this.reserved = requireNonNull(reserved);
    }

    @Override
    public Completable releaseAsync() {
        return reserved.release();
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return delegate.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
        return delegate.getSettingStream(settingKey);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return new RequestCompletionHelperSingle(delegate.request(request), reserved);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return delegate.getExecutionContext();
    }

    // TODO We can't make ConcurrentReservedResource#requestFinished() work reliably with cancel() of HttpResponse.
    // This code will prematurely release connections when the cancel event is racing with the onComplete() of the
    // HttpRequest and gets ignored. This means that from the LoadBalancer's perspective the connection is free however
    // the user may still be subscribing and consume the payload.
    // This may be acceptable for now, with DefaultPipelinedConnection rejecting requests after a set maximum number and
    // this race condition expected to happen infrequently. For NonPipelinedHttpConnections there is no cap on
    // concurrent requests so we can expect to be making a pipelined request in this case.
    private static final class RequestCompletionHelperSingle extends Single<HttpResponse<HttpPayloadChunk>> {

        private static final AtomicIntegerFieldUpdater<RequestCompletionHelperSingle> terminatedUpdater =
                newUpdater(RequestCompletionHelperSingle.class, "terminated");

        private final ConcurrentReservedResource reserved;
        private final Single<HttpResponse<HttpPayloadChunk>> response;

        @SuppressWarnings("unused")
        private volatile int terminated;

        RequestCompletionHelperSingle(Single<HttpResponse<HttpPayloadChunk>> response,
                                              ConcurrentReservedResource reserved) {
            this.response = requireNonNull(response);
            this.reserved = requireNonNull(reserved);
        }

        private void finished() {
            // Avoid double counting
            if (terminatedUpdater.compareAndSet(this, 0, 1)) {
                reserved.requestFinished();
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

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    public boolean tryReserve() {
        return reserved.tryReserve();
    }

    public boolean tryRequest() {
        return reserved.tryRequest();
    }
}
