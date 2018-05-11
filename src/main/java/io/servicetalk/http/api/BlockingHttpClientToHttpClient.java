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
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;
import io.servicetalk.http.api.BlockingHttpClient.BlockingUpgradableHttpResponse;
import io.servicetalk.http.api.HttpClientToBlockingHttpClient.ReservedHttpConnectionToBlocking;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientToHttpClient<I, O> extends HttpClient<I, O> {
    private final BlockingHttpClient<I, O> blockingClient;

    BlockingHttpClientToHttpClient(BlockingHttpClient<I, O> blockingClient) {
        this.blockingClient = requireNonNull(blockingClient);
    }

    @Override
    public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
        return BlockingUtils.request(blockingClient, request);
    }

    @Override
    public Single<? extends ReservedHttpConnection<I, O>> reserveConnection(final HttpRequest<I> request) {
        return new Single<ReservedHttpConnection<I, O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super ReservedHttpConnection<I, O>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final ReservedHttpConnection<I, O> response;
                try {
                    // Do the conversion here in case there is a null value returned by the upgradeConnection.
                    response = new BlockingToReservedHttpConnection<>(
                            blockingClient.reserveConnection(new DefaultBlockingHttpRequest<>(request)));
                } catch (Throwable cause) {
                    cancellable.setDone();
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.setDone();

                subscriber.onSuccess(response);
            }
        };
    }

    @Override
    public Single<? extends UpgradableHttpResponse<I, O>> upgradeConnection(final HttpRequest<I> request) {
        return new Single<UpgradableHttpResponse<I, O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super UpgradableHttpResponse<I, O>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final UpgradableHttpResponse<I, O> response;
                try {
                    // Do the conversion here in case there is a null value returned by the upgradeConnection.
                    response = new BlockingToUpgradableHttpResponse<>(blockingClient.upgradeConnection(
                            new DefaultBlockingHttpRequest<>(request)), getExecutionContext().getExecutor());
                } catch (Throwable cause) {
                    cancellable.setDone();
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.setDone();

                subscriber.onSuccess(response);
            }
        };
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return blockingClient.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (blockingClient instanceof HttpClientToBlockingHttpClient) {
            return ((HttpClientToBlockingHttpClient) blockingClient).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClient.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClient::close);
    }

    @Override
    BlockingHttpClient<I, O> asBlockingClientInternal() {
        return blockingClient;
    }

    static final class BlockingToReservedHttpConnection<I, O> extends ReservedHttpConnection<I, O> {
        private final BlockingReservedHttpConnection<I, O> blockingReservedConnection;

        BlockingToReservedHttpConnection(BlockingReservedHttpConnection<I, O> blockingReservedConnection) {
            this.blockingReservedConnection = requireNonNull(blockingReservedConnection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(blockingReservedConnection::release);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return blockingReservedConnection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return from(blockingReservedConnection.getSettingIterable(settingKey));
        }

        @Override
        public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
            return BlockingUtils.request(blockingReservedConnection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return blockingReservedConnection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            if (blockingReservedConnection instanceof ReservedHttpConnectionToBlocking) {
                return ((ReservedHttpConnectionToBlocking) blockingReservedConnection).onClose();
            }

            return error(new UnsupportedOperationException("unsupported type: " +
                    blockingReservedConnection.getClass()));
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(blockingReservedConnection::close);
        }

        BlockingReservedHttpConnection<I, O> asBlockingReservedConnectionInternal() {
            return blockingReservedConnection;
        }
    }

    private static final class BlockingToUpgradableHttpResponse<I, O> implements UpgradableHttpResponse<I, O> {
        private final BlockingUpgradableHttpResponse<I, O> upgradeResponse;
        private final Publisher<O> payloadBody;
        private final Executor executor;

        BlockingToUpgradableHttpResponse(BlockingUpgradableHttpResponse<I, O> upgradeResponse,
                                         Executor executor) {
            this(upgradeResponse, from(upgradeResponse.getPayloadBody()), executor);
        }

        private BlockingToUpgradableHttpResponse(BlockingUpgradableHttpResponse<I, O> upgradeResponse,
                                                 Publisher<O> payloadBody,
                                                 Executor executor) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.executor = requireNonNull(executor);
        }

        @Override
        public ReservedHttpConnection<I, O> getHttpConnection(final boolean releaseReturnsToClient) {
            return new BlockingToReservedHttpConnection<>(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public HttpResponse<O> setVersion(final HttpProtocolVersion version) {
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
        public HttpResponse<O> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public Publisher<O> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> UpgradableHttpResponse<I, R> transformPayloadBody(final Function<Publisher<O>,
                                                                                    Publisher<R>> transformer) {
            final Publisher<R> transformedPayload = transformer.apply(payloadBody);
            return new BlockingToUpgradableHttpResponse<>(
                    new BlockingUpgradableHttpResponseConverter<>(upgradeResponse,
                            itr -> transformer.apply(from(itr)).toIterable(),
                            transformedPayload.toIterable()),
                    transformedPayload, executor);
        }
    }

    private static final class BlockingUpgradableHttpResponseConverter<I, O1, O2> implements
                                                                      BlockingUpgradableHttpResponse<I, O2> {
        private final BlockingUpgradableHttpResponse<I, O1> upgradeResponse;
        private final Function<BlockingIterable<O1>, BlockingIterable<O2>> transformer;
        private final BlockingIterable<O2> payloadBody;

        BlockingUpgradableHttpResponseConverter(BlockingUpgradableHttpResponse<I, O1> upgradeResponse,
                                                Function<BlockingIterable<O1>, BlockingIterable<O2>> transformer,
                                                BlockingIterable<O2> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.transformer = requireNonNull(transformer);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public BlockingReservedHttpConnection<I, O2> getHttpConnection(final boolean releaseReturnsToClient) {
            return new BlockingReservedHttpConnectionConverter<>(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient), transformer);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public BlockingHttpResponse<O2> setVersion(final HttpProtocolVersion version) {
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
        public BlockingHttpResponse<O2> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public BlockingIterable<O2> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> BlockingUpgradableHttpResponse<I, R> transformPayloadBody(final Function<BlockingIterable<O2>,
                                                                                    BlockingIterable<R>> transformer) {
            return new BlockingUpgradableHttpResponseConverter<>(this, transformer, transformer.apply(payloadBody));
        }
    }

    public static final class BlockingReservedHttpConnectionConverter<I, O1, O2> extends
                                                                               BlockingReservedHttpConnection<I, O2> {
        private final BlockingReservedHttpConnection<I, O1> reservedConnection;
        private final Function<BlockingIterable<O1>, BlockingIterable<O2>> transformer;

        BlockingReservedHttpConnectionConverter(BlockingReservedHttpConnection<I, O1> reservedConnection,
                                                Function<BlockingIterable<O1>, BlockingIterable<O2>> transformer) {
            this.reservedConnection = requireNonNull(reservedConnection);
            this.transformer = requireNonNull(transformer);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return reservedConnection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final HttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.getSettingIterable(settingKey);
        }

        @Override
        public void close() throws Exception {
            reservedConnection.close();
        }

        @Override
        public BlockingHttpResponse<O2> request(final BlockingHttpRequest<I> request) throws Exception {
            return reservedConnection.request(request).transformPayloadBody(transformer);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return reservedConnection.getExecutionContext();
        }

        @Override
        public void release() throws Exception {
            reservedConnection.release();
        }
    }
}
