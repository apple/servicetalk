/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class AbstractClientEffectiveStrategyTest {
    public static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    protected final ServerContext context;
    protected final StreamingHttpClient client;
    protected final ConcurrentMap<ClientOffloadPoint, Thread> invokingThreads;
    private final IoExecutor ioExecutor;

    public AbstractClientEffectiveStrategyTest(boolean addBlockingFilter) throws Exception {
        context = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                                .fromAscii("Hello")));
        invokingThreads = new ConcurrentHashMap<>();
        ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY));
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort(context)).ioExecutor(ioExecutor);
        builder.appendClientFilter((c, __) -> new StreamingHttpClientFilter(c) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return delegate.request(strategy,
                                request.transformPayloadBody(payload ->
                                        payload.doBeforeRequest(__ ->
                                                recordThread(ClientOffloadPoint.RequestPayloadSubscription))))
                                .doBeforeSuccess(__ -> recordThread(ClientOffloadPoint.ResponseMeta))
                                .map(resp -> resp.transformPayloadBody(payload ->
                                        payload.doBeforeNext(__ -> recordThread(ClientOffloadPoint.ResponseData))));
                    }

                    @Override
                    protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                        // Don't modify the effective strategy calculation
                        return mergeWith;
                    }
                });
        if (addBlockingFilter) {
            // Here since we do not override mergeForEffectiveStrategy, it will default to offload-all.
            builder.appendClientFilter((client, __) -> new StreamingHttpClientFilter(client));
        }
        client = builder.buildStreaming();
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, context, ioExecutor).closeAsync().toVoidFuture().get();
    }

    protected void assertNoOffload(final ClientOffloadPoint offloadPoint) {
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    protected void assertOffload(final ClientOffloadPoint offloadPoint) {
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
    }

    protected void verifyOffloadCount() {
        assertThat("Unexpected offload points recorded.", invokingThreads.size(), is(3));
    }

    private void recordThread(final ClientOffloadPoint offloadPoint) {
        invokingThreads.put(offloadPoint, Thread.currentThread());
    }

    protected enum ClientOffloadPoint {
        RequestPayloadSubscription,
        ResponseMeta,
        ResponseData
    }
}
