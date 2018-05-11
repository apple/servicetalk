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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpRequests.fromBlockingRequest;
import static java.util.Objects.requireNonNull;

final class HttpClientToBlockingHttpClient<I, O> extends BlockingHttpClient<I, O> {
    private final HttpClient<I, O> client;

    HttpClientToBlockingHttpClient(HttpClient<I, O> client) {
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingHttpResponse<O> request(final BlockingHttpRequest<I> request) throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public BlockingReservedHttpConnection<I, O> reserveConnection(final BlockingHttpRequest<I> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new ReservedHttpConnectionToBlocking<>(awaitIndefinitelyNonNull(client.reserveConnection(
                fromBlockingRequest(request, getExecutionContext().getExecutor()))));
    }

    @Override
    public BlockingUpgradableHttpResponse<I, O> upgradeConnection(final BlockingHttpRequest<I> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new UpgradableHttpResponseToBlocking<>(awaitIndefinitelyNonNull(client.upgradeConnection(
                fromBlockingRequest(request, getExecutionContext().getExecutor()))),
                getExecutionContext().getExecutor());
    }

    @Override
    public void close() throws Exception {
        BlockingUtils.close(client);
    }

    Completable onClose() {
        return client.onClose();
    }

    @Override
    HttpClient<I, O> asAsynchronousClientInternal() {
        return client;
    }

    static final class ReservedHttpConnectionToBlocking<I, O> extends BlockingReservedHttpConnection<I, O> {
        private final ReservedHttpConnection<I, O> reservedConnection;

        ReservedHttpConnectionToBlocking(ReservedHttpConnection<I, O> reservedConnection) {
            this.reservedConnection = requireNonNull(reservedConnection);
        }

        @Override
        public void release() throws Exception {
            // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter).
            // So we don't apply any explicit timeout here and just wait forever.
            awaitIndefinitely(reservedConnection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return reservedConnection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final HttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public BlockingHttpResponse<O> request(final BlockingHttpRequest<I> request) throws Exception {
            return BlockingUtils.request(reservedConnection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return reservedConnection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            BlockingUtils.close(reservedConnection);
        }

        Completable onClose() {
            return reservedConnection.onClose();
        }

        @Override
        ReservedHttpConnection<I, O> asAsynchronousReservedConnectionInternal() {
            return reservedConnection;
        }
    }

    private static final class UpgradableHttpResponseToBlocking<I, O> implements BlockingUpgradableHttpResponse<I, O> {
        private final UpgradableHttpResponse<I, O> upgradeResponse;
        private final BlockingIterable<O> payloadBody;
        private final Executor executor;

        UpgradableHttpResponseToBlocking(UpgradableHttpResponse<I, O> upgradeResponse,
                                         Executor executor) {
            this(upgradeResponse, upgradeResponse.getPayloadBody().toIterable(), executor);
        }

        private UpgradableHttpResponseToBlocking(UpgradableHttpResponse<I, O> upgradeResponse,
                                                 BlockingIterable<O> payloadBody,
                                                 Executor executor) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.executor = requireNonNull(executor);
        }

        @Override
        public BlockingReservedHttpConnection<I, O> getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToBlocking<>(upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public BlockingHttpResponse<O> setVersion(final HttpProtocolVersion version) {
            upgradeResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradeResponse.getHeaders();
        }

        @Override
        public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence>
                                       headerFilter) {
            return upgradeResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradeResponse.getStatus();
        }

        @Override
        public BlockingHttpResponse<O> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public BlockingIterable<O> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> BlockingUpgradableHttpResponse<I, R> transformPayloadBody(final Function<BlockingIterable<O>,
                BlockingIterable<R>> transformer) {
            final BlockingIterable<R> transformedPayload = transformer.apply(payloadBody);
            return new UpgradableHttpResponseToBlocking<>(
                    new UpgradableHttpResponseToBlockingConverter<>(upgradeResponse,
                            pub -> from(transformer.apply(pub.toIterable())),
                            from(transformedPayload)),
                    transformedPayload, executor);
        }
    }

    private static final class UpgradableHttpResponseToBlockingConverter<I, O1, O2> implements
                                                                                    UpgradableHttpResponse<I, O2> {
        private final UpgradableHttpResponse<I, O1> upgradeResponse;
        private final Function<Publisher<O1>, Publisher<O2>> transformer;
        private final Publisher<O2> payloadBody;

        UpgradableHttpResponseToBlockingConverter(UpgradableHttpResponse<I, O1> upgradeResponse,
                                                  Function<Publisher<O1>, Publisher<O2>> transformer,
                                                  Publisher<O2> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.transformer = requireNonNull(transformer);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedHttpConnection<I, O2> getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionConverter<>(upgradeResponse.getHttpConnection(releaseReturnsToClient),
                    transformer);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public HttpResponse<O2> setVersion(final HttpProtocolVersion version) {
            upgradeResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradeResponse.getHeaders();
        }

        @Override
        public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence>
                                       headerFilter) {
            return upgradeResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradeResponse.getStatus();
        }

        @Override
        public HttpResponse<O2> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public Publisher<O2> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> UpgradableHttpResponse<I, R> transformPayloadBody(final Function<Publisher<O2>,
                Publisher<R>> transformer) {
            return new UpgradableHttpResponseToBlockingConverter<>(this, transformer, transformer.apply(payloadBody));
        }
    }

    private static final class ReservedHttpConnectionConverter<I, O1, O2> extends ReservedHttpConnection<I, O2> {
        private final ReservedHttpConnection<I, O1> reservedConnection;
        private final Function<Publisher<O1>, Publisher<O2>> transformer;

        ReservedHttpConnectionConverter(ReservedHttpConnection<I, O1> reservedConnection,
                                        Function<Publisher<O1>, Publisher<O2>> transformer) {
            this.reservedConnection = requireNonNull(reservedConnection);
            this.transformer = requireNonNull(transformer);
        }

        @Override
        public Completable releaseAsync() {
            return reservedConnection.releaseAsync();
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return reservedConnection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey);
        }

        @Override
        public Single<HttpResponse<O2>> request(final HttpRequest<I> request) {
            return reservedConnection.request(request).map(resp -> resp.transformPayloadBody(transformer));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return reservedConnection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return reservedConnection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return reservedConnection.closeAsync();
        }
    }
}
