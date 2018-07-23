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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpConnection.SettingKey.MAX_CONCURRENCY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class DefaultHttpConnectionBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void requestFromBuilderOverNonPipelinedHttpConnection() throws ExecutionException, InterruptedException {
        sendRequestAndValidate(1);
    }

    @Test
    public void requestFromBuilderOverPipelinedHttpConnection() throws ExecutionException, InterruptedException {
        sendRequestAndValidate(3);
    }

    @Test
    public void requestFromConnectionFactory() throws ExecutionException, InterruptedException {
        ConnectionFactory<SocketAddress, HttpConnection> cf =
                prepareBuilder(1).asConnectionFactory(CTX);
        Single<HttpConnection> connectionSingle =
                cf.newConnection(serverContext.getListenAddress());
        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    private static final class DummyFanoutFilter extends HttpConnection {

        private final HttpConnection delegate;

        private DummyFanoutFilter(final HttpConnection connection) {
            this.delegate = connection;
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            // fanout - simulates followup request on response
            return delegate.request(request).flatMap(resp ->
                    resp.getPayloadBody().ignoreElements().andThen(delegate.request(request)));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return delegate.getExecutionContext();
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return delegate.getConnectionContext();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            if (settingKey == MAX_CONCURRENCY) {
                // Compensate for the extra request
                return (Publisher<T>) delegate.getSettingStream(MAX_CONCURRENCY).map(i -> i - 1);
            }
            return delegate.getSettingStream(settingKey);
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }
    }

    @Test
    public void requestFromConnectionFactoryWithFilter() throws ExecutionException, InterruptedException {

        Single<DummyFanoutFilter> connectionSingle = prepareBuilder(10)
                .asConnectionFactory(CTX)
                .newConnection(serverContext.getListenAddress())
                .map(DummyFanoutFilter::new);

        DummyFanoutFilter connection = awaitIndefinitelyNonNull(connectionSingle);

        Integer maxConcurrent = awaitIndefinitely(connection.getSettingStream(MAX_CONCURRENCY).first());
        assertThat(maxConcurrent, equalTo(9));

        makeRequestValidateResponseAndClose(connection);
    }

    private void sendRequestAndValidate(int pipelinedRequests) throws ExecutionException, InterruptedException {
        DefaultHttpConnectionBuilder<SocketAddress> defaultBuilder = prepareBuilder(pipelinedRequests);

        Single<HttpConnection> connectionSingle =
                defaultBuilder.build(CTX, serverContext.getListenAddress());

        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    @Nonnull
    private DefaultHttpConnectionBuilder<SocketAddress> prepareBuilder(final int pipelinedRequests) {
        return new DefaultHttpConnectionBuilder<SocketAddress>()
                .setMaxPipelinedRequests(pipelinedRequests);
    }
}
