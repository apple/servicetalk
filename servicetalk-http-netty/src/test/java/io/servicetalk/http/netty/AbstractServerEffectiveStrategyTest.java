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

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class AbstractServerEffectiveStrategyTest {
    public static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Nullable
    protected ServerContext context;
    @Nullable
    private BlockingHttpClient client;
    protected final ConcurrentMap<ServerOffloadPoint, Thread> invokingThreads;
    protected final HttpServerBuilder builder;
    protected final Executor serviceExecutor;
    private final IoExecutor ioExecutor;

    public AbstractServerEffectiveStrategyTest(boolean addBlockingFilter) {
        ioExecutor = createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY));
        serviceExecutor = newCachedThreadExecutor();
        builder = HttpServers.forAddress(localAddress(0)).ioExecutor(ioExecutor);
        builder.appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                recordThread(ServerOffloadPoint.ServiceHandle);
                return delegate().handle(ctx, request.transformPayloadBody(publisher ->
                        publisher.doBeforeNext(__ -> recordThread(ServerOffloadPoint.RequestPayload))), responseFactory)
                        .map(resp -> resp.transformPayloadBody(pub ->
                                pub.doBeforeRequest(__ -> recordThread(ServerOffloadPoint.Response))));
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                return mergeWith;
            }
        });
        if (addBlockingFilter) {
            // Here since we do not override mergeForEffectiveStrategy, it will default to offload-all.
            builder.appendServiceFilter(StreamingHttpServiceFilter::new);
        }
        invokingThreads = new ConcurrentHashMap<>();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        CompositeCloseable closeable = newCompositeCloseable();
        if (context != null) {
            closeable.append(context);
        }
        closeable.appendAll(serviceExecutor, ioExecutor).closeAsync().toFuture().get();
    }

    protected BlockingHttpClient context(ServerContext context) {
        this.context = context;
        client = HttpClients.forSingleAddress(serverHostAndPort(context)).buildBlocking();
        return client;
    }

    protected void assertNoOffload(final ServerOffloadPoint offloadPoint) {
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    protected void assertOffload(final ServerOffloadPoint offloadPoint) {
        assertThat("Unexpected thread for point: " + offloadPoint, invokingThreads.get(offloadPoint).getName(),
                not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
    }

    protected void verifyOffloadCount() {
        assertThat("Unexpected offload points recorded.", invokingThreads.size(), is(3));
    }

    private void recordThread(final ServerOffloadPoint offloadPoint) {
        invokingThreads.put(offloadPoint, Thread.currentThread());
    }

    protected enum ServerOffloadPoint {
        ServiceHandle,
        RequestPayload,
        Response
    }
}
