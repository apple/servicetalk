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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DisableInterruptOnCancelHttpServiceFilter;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class DisableInterruptOnCancelHttpServiceFilterTest {

    @Test
    void verifyAsyncContext() throws Exception {
        verifyServerFilterAsyncContextVisibility(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
    }

    @ParameterizedTest(name = "{displayName} [{index}] withFilter={0}")
    @ValueSource(booleans = {false, true})
    void clientCancelMidStreamFailsWrite(boolean withFilter) throws Exception {
        CountDownLatch releaseLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();

        BlockingStreamingHttpService service = (ctx, request, response) -> {
            HttpPayloadWriter<Buffer> writer = response.sendMetaData();
            try {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii("first"));
                writer.flush();
                try {
                    releaseLatch.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                // The server notices the disconnect only when a write fails.
                for (;;) {
                    writer.write(ctx.executionContext().bufferAllocator().fromAscii("x"));
                    writer.flush();
                }
            } catch (IOException e) {
                writeFailure.set(e);
                // The interrupt precedes the writer cancel, so it is visible here.
                if (Thread.interrupted()) {
                    interrupted.set(true);
                }
            } finally {
                doneLatch.countDown();
            }
        };

        HttpServerBuilder builder = forAddress(localAddress(0));
        if (withFilter) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        try (ServerContext serverContext = builder.listenBlockingStreamingAndAwait(service)) {
            try (StreamingHttpClient client = forSingleAddress(serverHostAndPort(serverContext)).buildStreaming()) {
                ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/"))
                        .toFuture().get();
                StreamingHttpResponse response = connection.request(connection.get("/")).toFuture().get();
                CountDownLatch firstChunk = new CountDownLatch(1);
                Cancellable cancellable = response.payloadBody()
                        .afterOnNext(__ -> firstChunk.countDown())
                        .ignoreElements()
                        .subscribe();
                firstChunk.await();
                // The client is still reading, so it resets the HTTP/1.1 connection. That fails the next server write.
                cancellable.cancel();
                connection.onClose().toFuture().get();
            } finally {
                releaseLatch.countDown();
            }
            doneLatch.await();
        }

        assertThat("the filter must decide whether the service thread is interrupted", interrupted.get(),
                is(!withFilter));
        assertThat(writeFailure.get(), instanceOf(IOException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] withFilter={0}")
    @ValueSource(booleans = {false, true})
    void timeoutBeforeSendMetaDataFailsWrite(boolean withFilter) throws Exception {
        CountDownLatch responseCancelled = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();

        BlockingStreamingHttpService service = (ctx, request, response) -> {
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            HttpPayloadWriter<Buffer> writer = response.sendMetaData();
            try {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii("x"));
                writer.close();
            } catch (IOException e) {
                writeFailure.set(e);
            } finally {
                doneLatch.countDown();
            }
        };

        HttpServerBuilder builder = forAddress(localAddress(0))
                .appendServiceFilter(new TimeoutHttpServiceFilter(ofMillis(100)))
                .appendServiceFilter(delegate -> new StreamingHttpServiceFilter(delegate) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return delegate().handle(ctx, request, responseFactory)
                                .afterCancel(responseCancelled::countDown);
                    }
                });
        if (withFilter) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        try (ServerContext serverContext = builder.listenBlockingStreamingAndAwait(service)) {
            try (BlockingHttpClient client = forSingleAddress(serverHostAndPort(serverContext)).buildBlocking()) {
                // Fails with the timeout; only the resulting cancel matters.
                client.request(client.get("/"));
                // Release only after the cancel landed, or a missing interrupt looks like a late one.
                responseCancelled.await();
            } finally {
                releaseLatch.countDown();
            }
            doneLatch.await();
        }

        assertThat("the filter must decide whether the service thread is interrupted", interrupted.get(),
                is(!withFilter));
        assertThat(writeFailure.get(), instanceOf(IOException.class));
    }
}
