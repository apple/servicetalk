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
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class DefaultHttpClient<ResolvedAddress, EventType extends ServiceDiscoverer.Event<ResolvedAddress>>
        extends HttpClient<HttpPayloadChunk, HttpPayloadChunk> {

    private static final Function<LoadBalancedHttpConnection, LoadBalancedHttpConnection> SELECTOR_FOR_REQUEST =
            conn -> conn.tryRequest() ? conn : null;
    private static final Function<LoadBalancedHttpConnection, LoadBalancedHttpConnection> SELECTOR_FOR_RESERVE =
            conn -> conn.tryReserve() ? conn : null;

    // TODO Proto specific LB after upgrade and worry about SSL
    private final ExecutionContext executionContext;
    private final LoadBalancer<LoadBalancedHttpConnection> loadBalancer;

    @SuppressWarnings("unchecked")
    DefaultHttpClient(ExecutionContext executionContext,
                      LoadBalancer<LoadBalancedHttpConnection> loadBalancer) {
        this.executionContext = requireNonNull(executionContext);
        this.loadBalancer = requireNonNull(loadBalancer);
    }

    @Override
    public Single<? extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk>> reserveConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return loadBalancer.selectConnection(SELECTOR_FOR_RESERVE);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return error(new UnsupportedOperationException("Protocol upgrades not yet implemented"));
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        // TODO should we do smart things here, add encoding headers etc. ?
        // We have to do the incrementing/decrementing in the Client instead of LoadBalancedHttpConnection because
        // it is possible that someone can use the ConnectionFactory exported by this Client before the LoadBalancer
        // takes ownership of it (e.g. connection initialization) and in that case they will not be following the
        // LoadBalancer API which this Client depends upon to ensure the concurrent request count state is correct.
        return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST)
                .flatMap(c -> new RequestCompletionHelperSingle(c.request(request), c));
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    @Override
    public Completable onClose() {
        return loadBalancer.onClose();
    }

    @Override
    public Completable closeAsync() {
        return loadBalancer.closeAsync();
    }

    // TODO We can't make RequestConcurrencyController#requestFinished() work reliably with cancel() of HttpResponse.
    // This code will prematurely release connections when the cancel event is racing with the onComplete() of the
    // HttpRequest and gets ignored. This means that from the LoadBalancer's perspective the connection is free however
    // the user may still be subscribing and consume the payload.
    // This may be acceptable for now, with DefaultPipelinedConnection rejecting requests after a set maximum number and
    // this race condition expected to happen infrequently. For NonPipelinedHttpConnections there is no cap on
    // concurrent requests so we can expect to be making a pipelined request in this case.
    private static final class RequestCompletionHelperSingle extends Single<HttpResponse<HttpPayloadChunk>> {

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
}
