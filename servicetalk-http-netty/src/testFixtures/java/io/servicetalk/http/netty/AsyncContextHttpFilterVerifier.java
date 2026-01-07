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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Matcher;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Utility verifiers for {@link StreamingHttpServiceFilterFactory} filters and their
 * interactions with {@link AsyncContext}.
 */
public final class AsyncContextHttpFilterVerifier {

    public static final Key<String> K1 = newKey("k1", String.class);
    public static final Key<String> K2 = newKey("k2", String.class);
    public static final Key<String> K3 = newKey("k3", String.class);

    public static final String V1 = "v1";
    public static final String V2 = "v2";
    public static final String V3 = "v3";

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
        final String content = "Hello World";

        try (ServerContext serverContext = forAddress(localAddress(0))
                .appendServiceFilter(new AsyncContextAssertionFilter(errors))
                .appendServiceFilter(filter)
                .listenStreamingAndAwait(asyncContextRequestHandler(errors));
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(serverContext)).buildBlocking()) {

            HttpResponse resp = client.request(client.post("/test")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii(content)));
            assertThat(resp.status(), is(OK));
            assertThat(resp.payloadBody().toString(US_ASCII), is(equalTo(content)));
        }
        assertNoAsyncErrors(errors);
    }

    /**
     * Creates new {@link StreamingHttpService} that sets keys.
     *
     * @param errorQueue {@link Queue} to add an {@link AssertionError} in case the assertion fails
     * @return {@link StreamingHttpService} that sets keys
     */
    public static StreamingHttpService asyncContextRequestHandler(final Queue<Throwable> errorQueue) {
        return (ctx, request, respFactory) -> {
            AsyncContext.put(K1, V1);
            ContextMap current = AsyncContext.context();
            return request.payloadBody()
                    .collect(StringBuilder::new, (collector, it) -> {
                        collector.append(it.toString(US_ASCII));
                        return collector;
            }).map(StringBuilder::toString).map(it -> {
                AsyncContext.put(K2, V2);
                assertAsyncContext(K1, V1, errorQueue);
                assertAsyncContext(K2, V2, errorQueue);
                assertSameContext(current, errorQueue);

                return respFactory.ok().payloadBody(from(it).map(body -> {
                    AsyncContext.put(K3, V3);
                    assertAsyncContext(K1, V1, errorQueue);
                    assertAsyncContext(K2, V2, errorQueue);
                    assertAsyncContext(K3, V3, errorQueue);
                    assertSameContext(current, errorQueue);
                    return ctx.executionContext().bufferAllocator().fromAscii(body);
                })).transformPayloadBody(publisher ->
                            publisher.beforeSubscriber(() -> new PublisherSource.Subscriber<Buffer>() {
                        @Override
                        public void onSubscribe(final PublisherSource.Subscription subscription) {
                            assertContextState(null);
                        }

                        @Override
                        public void onNext(final Buffer o) {
                            assertContextState(V3);
                        }

                        @Override
                        public void onError(final Throwable t) {
                            assertContextState(V3);
                        }

                        @Override
                        public void onComplete() {
                            assertContextState(V3);
                        }

                        private void assertContextState(@Nullable String v3) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, v3, errorQueue);
                            assertSameContext(current, errorQueue);
                        }
                    })
                );
            });
        };
    }

    /**
     * Asserts that a certain {@link Key} is present in {@link AsyncContext} with the expected value.
     *
     * @param key {@link Key} to verify
     * @param expectedValue value to expect or {@code null} if not expected
     * @param errorQueue {@link Queue} to add an {@link AssertionError} in case the assertion fails
     * @param <T> type of the {@link Key}
     */
    public static <T> void assertAsyncContext(final Key<T> key, @Nullable final T expectedValue,
                                              final Queue<Throwable> errorQueue) {
        final T actualValue = AsyncContext.get(key);
        if ((expectedValue == null && actualValue != null) ||
                (expectedValue != null && !expectedValue.equals(actualValue))) {
            AssertionError e = new AssertionError("unexpected value for " + key + ": " +
                    actualValue + ", expected: " + expectedValue);
            errorQueue.add(e);
        }
    }

    /**
     * Asserts that {@link AsyncContext#context()} is the same instance as the expected one.
     *
     * @param expected {@link ContextMap} we expect
     * @param errorQueue {@link Queue} to add an {@link AssertionError} in case the assertion fails
     */
    public static void assertSameContext(@Nullable final ContextMap expected, final Queue<Throwable> errorQueue) {
        assertContext(sameInstance(expected), errorQueue);
    }

    /**
     * Asserts that {@link AsyncContext#context()} is NOT the same instance as the notExpected one.
     *
     * @param notExpected {@link ContextMap} we do not expect
     * @param errorQueue {@link Queue} to add an {@link AssertionError} in case the assertion fails
     */
    public static void assertNotSameContext(@Nullable final ContextMap notExpected, final Queue<Throwable> errorQueue) {
        assertContext(not(sameInstance(notExpected)), errorQueue);
    }

    private static void assertContext(Matcher<ContextMap> matcher, final Queue<Throwable> errorQueue) {
        try {
            assertThat(AsyncContext.context(), is(matcher));
        } catch (Throwable t) {
            errorQueue.add(t);
        }
    }

    /**
     * A filter that asserts presence of {@link #K1}, {@link #K2}, and {@link #K3} in {@link AsyncContext}.
     */
    public static final class AsyncContextAssertionFilter implements StreamingHttpServiceFilterFactory {

        final Queue<Throwable> errorQueue;
        private final boolean lazyPayload;
        private final boolean hasK2;
        private final boolean hasK3;
        private final boolean aggregatedResponse;
        private final boolean requestPayloadContextCopy;

        /**
         * Creates a new instance.
         *
         * @param errorQueue {@link Queue} to add an {@link AssertionError} in case an assertion fails
         */
        public AsyncContextAssertionFilter(final Queue<Throwable> errorQueue) {
            this(errorQueue, true, true, true, false, false);
        }

        /**
         * Creates a new instance.
         *
         * @param errorQueue {@link Queue} to add an {@link AssertionError} in case an assertion fails
         * @param lazyPayload {@code true} if the target service consumes request payload body lazily
         * @param hasK2 {@code true} if the target service sets {@link #K2} before completion of the response meta-data
         * @param hasK3 {@code true} if the target service sets {@link #K3} before completion of the response payload
         * @param aggregatedResponse {@code true} if the target service returns aggregated response, in this case keys
         * set during response payload body processing will be visible together with response metadata
         * @param requestPayloadContextCopy {@code true} if the request payload body should use a copy of the context
         * instead of sharing the same instance
         */
        public AsyncContextAssertionFilter(final Queue<Throwable> errorQueue,
                                           final boolean lazyPayload, final boolean hasK2, final boolean hasK3,
                                           final boolean aggregatedResponse, final boolean requestPayloadContextCopy) {
            this.errorQueue = errorQueue;
            this.lazyPayload = lazyPayload;
            this.hasK2 = hasK2;
            this.hasK3 = hasK3;
            this.aggregatedResponse = aggregatedResponse;
            this.requestPayloadContextCopy = requestPayloadContextCopy;
        }

        @Override
        public StreamingHttpServiceFilter create(StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    ContextMap current = AsyncContext.context();
                    assertAsyncContext(K1, null, errorQueue);
                    assertAsyncContext(K2, null, errorQueue);
                    assertAsyncContext(K3, null, errorQueue);
                    return delegate().handle(ctx, request.transformMessageBody(p -> p.beforeFinally(() -> {
                                if (requestPayloadContextCopy) {
                                    assertNotSameContext(current, errorQueue);
                                    assertContext(equalTo(current), errorQueue);
                                } else {
                                    assertSameContext(current, errorQueue);
                                }
                                assertAsyncContext(K1, lazyPayload ? V1 : null, errorQueue);
                                assertAsyncContext(K2, null, errorQueue);
                                assertAsyncContext(K3, null, errorQueue);
                            })), responseFactory)
                            .beforeOnSuccess(__ -> {
                                assertSameContext(current, errorQueue);
                                assertAsyncContext(K1, V1, errorQueue);
                                assertAsyncContext(K2, hasK2 ? V2 : null, errorQueue);
                                assertAsyncContext(K3, aggregatedResponse && hasK3 ? V3 : null, errorQueue);
                            })
                            .liftSync(new BeforeFinallyHttpOperator(() -> {
                                assertSameContext(current, errorQueue);
                                assertAsyncContext(K1, V1, errorQueue);
                                assertAsyncContext(K2, hasK2 ? V2 : null, errorQueue);
                                assertAsyncContext(K3, hasK3 ? V3 : null, errorQueue);
                            })).shareContextOnSubscribe();
                }
            };
        }
    }
}
