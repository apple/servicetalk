/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.transport.api.ServerContext;

import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Utility verifiers for {@link StreamingHttpServiceFilterFactory} filters and their
 * interactions with {@link AsyncContext}.
 */
public final class AsyncContextHttpFilterVerifier {

    private static final AsyncContextMap.Key<String> K1 = AsyncContextMap.Key.newKey("k1");
    private static final AsyncContextMap.Key<String> K2 = AsyncContextMap.Key.newKey("k2");
    private static final AsyncContextMap.Key<String> K3 = AsyncContextMap.Key.newKey("k3");

    private static final String V1 = "v1";
    private static final String V2 = "v2";
    private static final String V3 = "v3";

    private AsyncContextHttpFilterVerifier() {
    }

    /**
     * Verify that all interactions with the request/response and message-body from a request that goes through
     * the provided {@link StreamingHttpServiceFilterFactory} filter, have valid visibility of the {@link AsyncContext}.
     *
     * @param filter The {@link StreamingHttpServiceFilterFactory} filter to verify.
     */
    public static void verifyServerFilterAsyncContextVisibility(final StreamingHttpServiceFilterFactory filter)
            throws Exception {
        final BlockingQueue<Throwable> errors = new LinkedBlockingDeque<>();
        final List<String> payload = singletonList("Hello World");

        final ServerContext serverContext = forAddress(localAddress(0))
                .appendServiceFilter(asyncContextAssertionFilter(errors))
                .appendServiceFilter(filter)
                .listenStreamingAndAwait(asyncContextRequestHandler(errors));

        final BlockingStreamingHttpClient client = forSingleAddress(serverHostAndPort(serverContext))
                .buildBlockingStreaming();
        final BlockingStreamingHttpRequest request = client.post("/test")
                .payloadBody(payload, appSerializerUtf8FixLen());

        final BlockingStreamingHttpResponse resp = client.request(request);
        assertThat(resp.status(), is(OK));
        Iterator<String> itr = resp.payloadBody(appSerializerUtf8FixLen()).iterator();
        assertThat(itr.hasNext(), is(true));
        assertThat(itr.next(), is(payload.get(0)));
        assertThat(itr.hasNext(), is(false));
        assertNoAsyncErrors(errors);
    }

    private static StreamingHttpService asyncContextRequestHandler(final BlockingQueue<Throwable> errorQueue) {
        return (ctx, request, respFactory) -> {
            AsyncContext.put(K1, V1);
            return request.payloadBody(appSerializerUtf8FixLen())
                    .collect(StringBuilder::new, (collector, it) -> {
                        collector.append(it);
                        return collector;
            }).map(StringBuilder::toString).map(it -> {
                AsyncContext.put(K2, V2);
                assertAsyncContext(K1, V1, errorQueue);
                assertAsyncContext(K2, V2, errorQueue);

                return respFactory.ok().payloadBody(from(it).map(body -> {
                    AsyncContext.put(K3, V3);
                    assertAsyncContext(K1, V1, errorQueue);
                    assertAsyncContext(K2, V2, errorQueue);
                    assertAsyncContext(K3, V3, errorQueue);
                    return body;
                }), appSerializerUtf8FixLen()).transformPayloadBody(publisher ->
                            publisher.beforeSubscriber(() -> new PublisherSource.Subscriber<Buffer>() {
                        @Override
                        public void onSubscribe(final PublisherSource.Subscription subscription) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                        }

                        @Override
                        public void onNext(final Buffer o) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                        }

                        @Override
                        public void onError(final Throwable t) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                        }

                        @Override
                        public void onComplete() {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                        }
                    })
                );
            });
        };
    }

    private static <T> void assertAsyncContext(final AsyncContextMap.Key<T> key, final T expectedValue,
                                               final Queue<Throwable> errorQueue) {
        final T actualValue = AsyncContext.get(key);
        if (!expectedValue.equals(actualValue)) {
            AssertionError e = new AssertionError("unexpected value for " + key + ": " +
                    actualValue + ", expected: " + expectedValue);
            errorQueue.add(e);
        }
    }

    private static StreamingHttpServiceFilterFactory asyncContextAssertionFilter(
            final BlockingQueue<Throwable> errorQueue) {
        return service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return super.handle(ctx, request, responseFactory).liftSync(
                        new BeforeFinallyHttpOperator(() -> {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                        })).subscribeShareContext();
            }
        };
    }
}
