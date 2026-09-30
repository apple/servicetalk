/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PipelinedBlockingStreamingCloseTest {

    @Test
    void blockingStreamingPostDoesNotCloseConnections() throws Exception {
        final int callers = 64;
        final int perCaller = 200;
        ConnectionCloseObserver observer = new ConnectionCloseObserver();
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, factory) -> factory.noContent());
             BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     // Retries would replay requests that failed on a closing connection and hide the failure.
                     .appendClientFilter(disableAutoRetries())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(observer))
                     .buildBlockingStreaming()) {
            BufferAllocator alloc = client.executionContext().bufferAllocator();
            // Releases all callers together once every caller thread is running.
            CyclicBarrier start = new CyclicBarrier(callers);
            List<Throwable> errors = new CopyOnWriteArrayList<>();
            List<Future<?>> tasks = new ArrayList<>(callers);
            for (int c = 0; c < callers; c++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perCaller; i++) {
                        try {
                            BlockingStreamingHttpResponse response = client.request(client.post("/")
                                    .payloadBody(singletonList(alloc.fromAscii("hello"))));
                            try (BlockingIterator<Buffer> body = response.payloadBody().iterator()) {
                                while (body.hasNext()) {
                                    body.next();
                                }
                            }
                        } catch (Throwable t) {
                            errors.add(t);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            assertThat(errors, is(emptyList()));
            assertThat(observer.closed.get(), is(0));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class ConnectionCloseObserver implements TransportObserver, ConnectionObserver {

        final AtomicInteger closed = new AtomicInteger();

        @Override
        public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
            return this;
        }

        @Override
        public void connectionClosed() {
            closed.incrementAndGet();
        }

        @Override
        public void connectionClosed(Throwable error) {
            closed.incrementAndGet();
        }
    }
}
