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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
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
        ConnectionFactory<SocketAddress, StreamingHttpConnection> cf =
                prepareBuilder(1).ioExecutor(CTX.ioExecutor()).executor(CTX.executor())
                        .asConnectionFactory();
        Single<StreamingHttpConnection> connectionSingle =
                cf.newConnection(serverContext.listenAddress());
        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    private static final class DummyFanoutFilter extends StreamingHttpConnectionAdapter {

        private DummyFanoutFilter(final StreamingHttpConnection connection) {
            super(connection);
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            // fanout - simulates followup request on response
            return delegate().request(strategy, request).flatMap(resp ->
                    resp.payloadBody().ignoreElements().concatWith(delegate().request(request)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            if (settingKey == MAX_CONCURRENCY) {
                // Compensate for the extra request
                return (Publisher<T>) delegate().settingStream(MAX_CONCURRENCY).map(i -> i - 1);
            }
            return delegate().settingStream(settingKey);
        }
    }

    @Test
    public void requestFromConnectionFactoryWithFilter() throws ExecutionException, InterruptedException {

        Single<DummyFanoutFilter> connectionSingle = prepareBuilder(10)
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .asConnectionFactory()
                .newConnection(serverContext.listenAddress())
                .map(DummyFanoutFilter::new);

        DummyFanoutFilter connection = awaitIndefinitelyNonNull(connectionSingle);

        Integer maxConcurrent = awaitIndefinitely(connection.settingStream(MAX_CONCURRENCY).first());
        assertThat(maxConcurrent, equalTo(9));

        makeRequestValidateResponseAndClose(connection);
    }

    private void sendRequestAndValidate(int pipelinedRequests) throws ExecutionException, InterruptedException {
        DefaultHttpConnectionBuilder<SocketAddress> defaultBuilder = prepareBuilder(pipelinedRequests);

        Single<StreamingHttpConnection> connectionSingle =
                defaultBuilder.ioExecutor(CTX.ioExecutor()).executor(CTX.executor())
                .buildStreaming(serverContext.listenAddress());

        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    @Nonnull
    private DefaultHttpConnectionBuilder<SocketAddress> prepareBuilder(final int pipelinedRequests) {
        return new DefaultHttpConnectionBuilder<SocketAddress>()
                .setMaxPipelinedRequests(pipelinedRequests);
    }
}
