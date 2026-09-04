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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.utils.InterruptBlockingServiceOnCancelHttpServiceFilter;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verifies {@code INTERRUPT_BLOCKING_SERVICE_ON_CANCEL} against a real server, where the response is cancelled by a
 * timeout while the service is still running.
 */
class BlockingServiceInterruptOnCancelTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);

    private final CountDownLatch cancelled = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicBoolean interrupted = new AtomicBoolean();

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void blockingService(boolean interruptOnCancel) throws Exception {
        BlockingHttpService service = (ctx, request, responseFactory) -> {
            awaitCancellation();
            return responseFactory.ok();
        };
        assertBehavior(builder -> builder.listenBlockingAndAwait(service), interruptOnCancel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] interruptOnCancel={0}")
    @ValueSource(booleans = {true, false})
    void blockingStreamingService(boolean interruptOnCancel) throws Exception {
        BlockingStreamingHttpService service = (ctx, request, response) -> {
            awaitCancellation();
            try {
                response.sendMetaData().close();
            } catch (Exception ignored) {
                // The exchange is already cancelled; only the interrupt behavior is under test.
            }
        };
        assertBehavior(builder -> builder.listenBlockingStreamingAndAwait(service), interruptOnCancel);
    }

    private void awaitCancellation() {
        try {
            proceed.await();
        } catch (InterruptedException e) {
            interrupted.set(true);
        }
        if (Thread.interrupted()) {
            interrupted.set(true);
        }
        done.countDown();
    }

    private void assertBehavior(ServerStarter starter, boolean interruptOnCancel) throws Exception {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new TimeoutHttpServiceFilter(TIMEOUT, true))
                .appendServiceFilter(delegate -> new StreamingHttpServiceFilter(delegate) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return delegate().handle(ctx, request, responseFactory).afterCancel(cancelled::countDown);
                    }
                })
                .appendServiceFilter(new InterruptBlockingServiceOnCancelHttpServiceFilter(interruptOnCancel));

        ServerContext serverContext = starter.start(builder);
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .buildBlocking()) {
            assertThat("the timeout must cancel the response while the service is still running",
                    client.request(client.get("/")).status(), is(INTERNAL_SERVER_ERROR));
            assertThat(cancelled.await(30, SECONDS), is(true));

            if (!interruptOnCancel) {
                proceed.countDown();
            }
            assertThat("when an interrupt is expected it must be the only thing that releases the service thread",
                    done.await(30, SECONDS), is(true));

            assertThat("interrupt-on-cancel must follow the request context",
                    interrupted.get(), is(interruptOnCancel));
        } finally {
            proceed.countDown();
            serverContext.closeAsync().toFuture().get();
        }
    }

    @FunctionalInterface
    private interface ServerStarter {
        ServerContext start(HttpServerBuilder builder) throws Exception;
    }
}
