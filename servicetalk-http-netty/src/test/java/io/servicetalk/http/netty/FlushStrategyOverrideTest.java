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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;
import io.servicetalk.transport.netty.internal.MockFlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class FlushStrategyOverrideTest {

    @Rule
    public final ExecutionContextRule ctx = immediate();
    @Rule
    public final PublisherRule<Object> payload = new PublisherRule<>();

    private StreamingHttpClient client;
    private ServerContext serverCtx;
    private ReservedStreamingHttpConnection conn;
    private FlushingService service;

    @Before
    public void setUp() throws Exception {
        service = new FlushingService();
        serverCtx = new DefaultHttpServerStarter()
                .startStreaming(ctx, 0, service)
                .toFuture().get();
        InetSocketAddress serverAddr = (InetSocketAddress) serverCtx.getListenAddress();
        client = forSingleAddress(new NoopSD(serverAddr), serverAddr)
                .disableHostHeaderFallback()
                .buildStreaming(ctx);
        conn = client.reserveConnection(client.get("/")).toFuture().get();
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(conn, client, serverCtx).closeAsync().toFuture().get();
    }

    @Test
    public void overrideFlush() throws Throwable {
        NettyConnectionContext nctx = (NettyConnectionContext) conn.connectionContext();
        MockFlushStrategy clientStrategy = new MockFlushStrategy();
        Cancellable c = nctx.updateFlushStrategy(old -> clientStrategy);

        CountDownLatch reqWritten = new CountDownLatch(1);
        StreamingHttpRequest req = client.get("/flush").payloadBody(from(1, 2, 3)
                .map(count -> ctx.bufferAllocator().fromAscii("" + count))
                .doAfterFinally(reqWritten::countDown));

        Future<? extends Collection<Object>> clientResp = conn.request(req)
                .flatMapPublisher(StreamingHttpResponse::payloadBodyAndTrailers).toFuture();
        reqWritten.await(); // Wait for request to be written.

        FlushSender clientFlush = clientStrategy.verifyApplied();
        clientStrategy.verifyWriteStarted();
        clientStrategy.verifyItemWritten(5 /* Header + 3 chunks + trailers*/);
        clientStrategy.verifyWriteTerminated();
        clientFlush.flush();
        clientStrategy.verifyNoMoreInteractions();

        MockFlushStrategy serverStrategy = service.getLastUsedStrategy();

        FlushSender serverFlush = serverStrategy.verifyApplied();
        serverStrategy.verifyWriteStarted();
        serverStrategy.verifyItemWritten(4 /*headers + 3 chunks*/); // we uninstall the strategy post body-end.
        serverStrategy.verifyWriteTerminated();
        serverFlush.flush();

        Collection<Object> chunks = clientResp.get();
        assertThat("Unexpected items received.", chunks, hasSize(4 /*3 chunks + last chunk*/));
        clientStrategy.verifyWriteCancelled(); // Single.flatMapPublisher sends a cancel when switching to Publisher.
        c.cancel(); // revert to flush on each.

        // No more custom strategies.
        Collection<Object> secondReqChunks = conn.request(conn.get(""))
                .flatMapPublisher(StreamingHttpResponse::payloadBodyAndTrailers).toFuture().get();
        clientStrategy.verifyNoMoreInteractions();
        service.getLastUsedStrategy();
        serverStrategy.verifyNoMoreInteractions();
        assertThat("Unexpected payload for regular flush.", secondReqChunks, hasSize(1/*last chunk*/));
    }

    private static final class FlushingService extends StreamingHttpService {

        private BlockingQueue<MockFlushStrategy> flushStrategies = new LinkedBlockingQueue<>();

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            if (request.path().startsWith("/flush")) {
                NettyConnectionContext nctx = (NettyConnectionContext) ctx;
                MockFlushStrategy strategy = new MockFlushStrategy();
                Cancellable c = nctx.updateFlushStrategy(old -> strategy);
                return success(responseFactory.ok().payloadBody(request.payloadBody().doAfterFinally(() -> {
                    flushStrategies.add(strategy);
                    c.cancel();
                })));
            } else {
                return success(responseFactory.ok().payloadBody(request.payloadBody()
                        .doAfterFinally(() -> flushStrategies.add(new MockFlushStrategy()))));
            }
        }

        MockFlushStrategy getLastUsedStrategy() throws InterruptedException {
            return flushStrategies.take();
        }
    }

    private static final class NoopSD implements ServiceDiscoverer<InetSocketAddress, InetSocketAddress> {

        private final ListenableAsyncCloseable closeable;
        private final InetSocketAddress serverAddr;

        NoopSD(final InetSocketAddress serverAddr) {
            this.serverAddr = serverAddr;
            closeable = emptyAsyncCloseable();
        }

        @Override
        public Publisher<Event<InetSocketAddress>> discover(final InetSocketAddress inetSocketAddress) {
            return from(new Event<InetSocketAddress>() {
                @Override
                public InetSocketAddress getAddress() {
                    return serverAddr;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }
            });
        }

        @Override
        public Completable onClose() {
            return closeable.onClose();
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }
    }
}
