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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscription;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.net.InetAddress.getLoopbackAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

public class HttpClientAsyncContextTest {
    private static final AsyncContextMap.Key<CharSequence> K1 = AsyncContextMap.Key.newKey("k1");
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    private static final InetSocketAddress LOCAL_0 = new InetSocketAddress(getLoopbackAddress(), 0);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void contextPreservedOverFilterBoundariesOffloaded() throws Exception {
        contextPreservedOverFilterBoundaries(false);
    }

    @Test
    public void contextPreservedOverFilterBoundariesNoOffload() throws Exception {
        contextPreservedOverFilterBoundaries(true);
    }

    private void contextPreservedOverFilterBoundaries(boolean useImmediate) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext serverContext = compositeCloseable.append(HttpServers.forAddress(LOCAL_0)
                .listenAndAwait((ctx, request, responseFactory) -> success(responseFactory.ok())));
        try {
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder = HttpClients.forSingleAddress(
                    HostAndPort.of((InetSocketAddress) serverContext.listenAddress()))
                    .appendClientFilter((c, lbEvents) -> new StreamingHttpClientFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                     final HttpExecutionStrategy strategy,
                                                                     final StreamingHttpRequest request) {
                            CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                            if (requestId != null) {
                                AsyncContext.put(K1, requestId);
                            }
                            return delegate.request(strategy, request).map(resp -> resp.transformRawPayloadBody(pub ->
                                    pub.doAfterSubscriber(() -> new org.reactivestreams.Subscriber<Object>() {
                                        @Override
                                        public void onSubscribe(final Subscription subscription) {
                                        }

                                        @Override
                                        public void onNext(final Object o) {
                                            assertAsyncContext();
                                        }

                                        @Override
                                        public void onError(final Throwable throwable) {
                                            assertAsyncContext();
                                        }

                                        @Override
                                        public void onComplete() {
                                            assertAsyncContext();
                                        }

                                        private void assertAsyncContext() {
                                            Object k1Value = AsyncContext.get(K1);
                                            if (requestId != null && !requestId.equals(k1Value)) {
                                                errorQueue.add(new AssertionError("AsyncContext[" + K1 + "]=[" +
                                                        k1Value + "], expected=[" + requestId + "]"));
                                            }
                                        }
                                    })));
                        }
                    });
            if (useImmediate) {
                clientBuilder.executionStrategy(HttpExecutionStrategies.noOffloadsStrategy());
            }
            StreamingHttpClient client = compositeCloseable.append(clientBuilder.buildStreaming());
            makeClientRequestWithId(client, "1");
            assertThat("Error queue is not empty!", errorQueue, empty());
        } finally {
            compositeCloseable.close();
        }
    }

    private static void makeClientRequestWithId(StreamingHttpRequester connection, String requestId)
            throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertEquals(OK, response.status());
        response.payloadBody().ignoreElements().subscribe();
    }
}
