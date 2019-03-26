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
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class DefaultHttpConnectionBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void requestFromBuilderOverNonPipelinedHttpConnection() throws Exception {
        sendRequestAndValidate(1);
    }

    @Test
    public void requestFromBuilderOverPipelinedHttpConnection() throws Exception {
        sendRequestAndValidate(3);
    }

    @Test
    public void requestFromConnectionFactory() throws Exception {
        ConnectionFactory<SocketAddress, StreamingHttpConnection> cf =
                prepareBuilder(1).ioExecutor(CTX.ioExecutor())
                        .executionStrategy(defaultStrategy(CTX.executor()))
                        .asConnectionFactory();
        Single<StreamingHttpConnection> connectionSingle =
                cf.newConnection(serverContext.listenAddress());
        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    private static final class DummyFanoutFilter extends StreamingHttpConnectionFilter {

        private DummyFanoutFilter(final StreamingHttpConnectionFilter connection) {
            super(connection);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            // fanout - simulates followup request on response
            return delegate.request(strategy, request).flatMap(resp ->
                    resp.payloadBody().ignoreElements().concat(delegate.request(strategy, request)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            if (settingKey == MAX_CONCURRENCY) {
                // Compensate for the extra request
                return (Publisher<T>) super.settingStream(MAX_CONCURRENCY).map(i -> i - 1);
            }
            return super.settingStream(settingKey);
        }
    }

    @Test
    public void requestFromConnectionFactoryWithFilter() throws Exception {

        Single<StreamingHttpConnection> connectionSingle = prepareBuilder(10)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .appendConnectionFilter(DummyFanoutFilter::new)
                .asConnectionFactory()
                .newConnection(serverContext.listenAddress());

        StreamingHttpConnection connection = awaitIndefinitelyNonNull(connectionSingle);

        Integer maxConcurrent = connection.settingStream(MAX_CONCURRENCY).firstOrElse(() -> null).toFuture().get();
        assertThat(maxConcurrent, equalTo(9));

        makeRequestValidateResponseAndClose(connection);
    }

    private static void sendRequestAndValidate(int pipelinedRequests) throws ExecutionException, InterruptedException {
        DefaultHttpConnectionBuilder<SocketAddress> defaultBuilder = prepareBuilder(pipelinedRequests);

        Single<StreamingHttpConnection> connectionSingle =
                defaultBuilder.ioExecutor(CTX.ioExecutor())
                        .executionStrategy(defaultStrategy(CTX.executor()))
                .buildStreaming(serverContext.listenAddress());

        makeRequestValidateResponseAndClose(awaitIndefinitelyNonNull(connectionSingle));
    }

    @Nonnull
    private static DefaultHttpConnectionBuilder<SocketAddress> prepareBuilder(final int pipelinedRequests) {
        return new DefaultHttpConnectionBuilder<SocketAddress>()
                .maxPipelinedRequests(pipelinedRequests);
    }
}
