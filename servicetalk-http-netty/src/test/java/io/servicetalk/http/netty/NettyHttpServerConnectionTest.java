/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.MockFlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class NettyHttpServerConnectionTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Buffer> responsePublisherRule = new PublisherRule<>();
    @Rule
    public final PublisherRule<Buffer> responsePublisherRule2 = new PublisherRule<>();
    @Rule
    public final ExecutionContextRule contextRule = immediate();

    private HttpExecutionStrategy serverExecutionStrategy;
    private HttpExecutionStrategy clientExecutionStrategy;
    private ServerContext serverContext;
    private StreamingHttpClient client;
    private MockFlushStrategy customStrategy;

    public NettyHttpServerConnectionTest(HttpExecutionStrategy serverExecutionStrategy,
                                         HttpExecutionStrategy clientExecutionStrategy) {
        this.serverExecutionStrategy = serverExecutionStrategy;
        this.clientExecutionStrategy = clientExecutionStrategy;
    }

    @Parameterized.Parameters(name = "server={0} client={1}")
    public static Collection<HttpExecutionStrategy[]> executionStrategies() {
        return Arrays.asList(
                new HttpExecutionStrategy[]{defaultStrategy(), defaultStrategy()},
                new HttpExecutionStrategy[]{noOffloadsStrategy(), defaultStrategy()},
                new HttpExecutionStrategy[]{defaultStrategy(), noOffloadsStrategy()},
                new HttpExecutionStrategy[]{noOffloadsStrategy(), noOffloadsStrategy()}
        );
    }

    @After
    public void cleanup() throws Exception {
        newCompositeCloseable().appendAll(client, serverContext).close();
    }

    @Test
    public void updateFlushStrategy() throws Exception {
        customStrategy = new MockFlushStrategy();
        AtomicReference<Cancellable> customCancellableRef = new AtomicReference<>();
        AtomicBoolean handledFirstRequest = new AtomicBoolean();
        CountDownLatch response1PayloadConsumedLatch = new CountDownLatch(1);
        CountDownLatch response2PayloadConsumedLatch = new CountDownLatch(1);

        serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(contextRule.ioExecutor())
                .appendConnectionAcceptorFilter(original -> original.append(ctx -> {
                            customCancellableRef.set(
                                    ((NettyConnectionContext) ctx).updateFlushStrategy(current -> customStrategy));
                            return completed();
                        }))
                .listenStreaming(new StreamingHttpService() {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        if (handledFirstRequest.compareAndSet(false, true)) {
                            customStrategy.doAfterFirstWrite(FlushStrategy.FlushSender::flush);
                            return success(responseFactory.ok().payloadBody(responsePublisherRule.publisher())
                            .transformRawPayloadBody(pub -> pub.doAfterSubscribe(subscription -> {
                                response1PayloadConsumedLatch.countDown();
                            })));
                        }
                        return success(responseFactory.ok().payloadBody(responsePublisherRule2.publisher())
                                .transformRawPayloadBody(pub -> pub.doAfterSubscribe(subscription -> {
                            response2PayloadConsumedLatch.countDown();
                        })));
                    }

                    @Override
                    public HttpExecutionStrategy executionStrategy() {
                        return serverExecutionStrategy;
                    }
                }).toFuture().get();

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .executionStrategy(clientExecutionStrategy)
                .buildStreaming();
        StreamingHttpResponse response = client.request(client.newRequest(GET, "/1")).toFuture().get();
        FlushStrategy.FlushSender customFlushSender = customStrategy.verifyApplied();
        Cancellable customCancellable = customCancellableRef.get();
        assertNotNull(customCancellable);

        // Verify that the custom strategy is applied and used for flushing.
        customStrategy.verifyWriteStarted();
        customStrategy.verifyItemWritten(1);
        customStrategy.verifyNoMoreInteractions();

        response1PayloadConsumedLatch.await();
        String payloadBodyString = "foo";
        responsePublisherRule.sendItems(DEFAULT_ALLOCATOR.fromAscii(payloadBodyString));
        responsePublisherRule.complete();
        customFlushSender.flush();
        Buffer responsePayload = response.payloadBody().reduce(DEFAULT_ALLOCATOR::newBuffer, (results, current) -> {
            results.writeBytes(current);
            return results;
        }).toFuture().get();
        assertEquals(payloadBodyString, responsePayload.toString(US_ASCII));
        customStrategy.verifyItemWritten(2);
        customStrategy.verifyWriteTerminated();

        // Restore the default flush strategy, which should flush on each
        customCancellable.cancel();
        StreamingHttpResponse response2 = client.request(client.newRequest(GET, "/2")).toFuture().get();
        response2PayloadConsumedLatch.await();
        responsePublisherRule2.sendItems(DEFAULT_ALLOCATOR.fromAscii(payloadBodyString));
        responsePublisherRule2.complete();
        responsePayload = response2.payloadBody().reduce(DEFAULT_ALLOCATOR::newBuffer, (results, current) -> {
            results.writeBytes(current);
            return results;
        }).toFuture().get();
        assertEquals(payloadBodyString, responsePayload.toString(US_ASCII));
    }
}
