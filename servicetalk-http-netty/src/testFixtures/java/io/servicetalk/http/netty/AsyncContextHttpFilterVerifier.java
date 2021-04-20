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
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.transport.api.HostAndPort;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class AsyncContextHttpFilterVerifier {

    private static final AsyncContextMap.Key<String> K1 = AsyncContextMap.Key.newKey("k1");
    private static final AsyncContextMap.Key<String> K2 = AsyncContextMap.Key.newKey("k2");
    private static final AsyncContextMap.Key<String> K3 = AsyncContextMap.Key.newKey("k3");
    private static final AsyncContextMap.Key<String> K4 = AsyncContextMap.Key.newKey("k4");

    private static final String V1 = "v1";
    private static final String V2 = "v2";
    private static final String V3 = "v3";
    private static final String V4 = "v4";

    private AsyncContextHttpFilterVerifier() {
    }

    public static void verifyServerFilterAsyncContextVisibility(final StreamingHttpServiceFilterFactory filter)
            throws Exception {
        final BlockingQueue<Throwable> errors = new LinkedBlockingDeque<>();

        final StreamingHttpServiceFilterFactory filters = asyncContextAssertionFilter(errors).append(filter);
        final StreamingHttpService service = filters.create(asyncContextRequestHandler(errors));

        final InetSocketAddress listenAddress =
                (InetSocketAddress) HttpServers.forPort(0).listenStreamingAndAwait(service).listenAddress();

        final BlockingHttpClient client = forSingleAddress(HostAndPort.of(listenAddress)).buildBlocking();
        final HttpRequest request = client.post("/test")
                      .payloadBody("Hello World", textSerializer());

        client.request(request);
        assertEmpty(errors);
    }

    private static StreamingHttpService asyncContextRequestHandler(final BlockingQueue<Throwable> errorQueue) {
        return (ctx, request, respFactory) -> {
            AsyncContext.put(K1, V1);
            return request.payloadBody(textDeserializer()).firstOrError().map(it -> {
                AsyncContext.put(K2, V2);
                assertAsyncContext(K1, V1, errorQueue);
                assertAsyncContext(K2, V2, errorQueue);
                return it;
            }).map(it -> {
                AsyncContext.put(K3, V3);
                assertAsyncContext(K1, V1, errorQueue);
                assertAsyncContext(K2, V2, errorQueue);
                assertAsyncContext(K3, V3, errorQueue);

                return respFactory.ok().payloadBody(from(it).map(body -> {
                    AsyncContext.put(K4, V4);
                    assertAsyncContext(K1, V1, errorQueue);
                    assertAsyncContext(K2, V2, errorQueue);
                    assertAsyncContext(K3, V3, errorQueue);
                    assertAsyncContext(K4, V4, errorQueue);
                    return body;
                }), textSerializer()).transformPayloadBody(publisher ->
                            publisher.beforeSubscriber(() -> new PublisherSource.Subscriber<Buffer>() {
                        @Override
                        public void onSubscribe(final PublisherSource.Subscription subscription) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                        }

                        @Override
                        public void onNext(final Buffer o) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                            assertAsyncContext(K4, V4, errorQueue);
                        }

                        @Override
                        public void onError(final Throwable t) {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                            assertAsyncContext(K4, V4, errorQueue);
                        }

                        @Override
                        public void onComplete() {
                            assertAsyncContext(K1, V1, errorQueue);
                            assertAsyncContext(K2, V2, errorQueue);
                            assertAsyncContext(K3, V3, errorQueue);
                            assertAsyncContext(K4, V4, errorQueue);
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
                    actualValue + " expected: " + expectedValue);
            errorQueue.add(e);
        }
    }

    private static void assertEmpty(Queue<Throwable> errorQueue) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, UTF_8.name())) {
            Throwable t;
            while ((t = errorQueue.poll()) != null) {
                t.printStackTrace(ps);
                ps.println(' ');
            }
            String data = new String(baos.toByteArray(), 0, baos.size(), UTF_8);
            if (!data.isEmpty()) {
                throw new AssertionError(data);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
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
                        new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            @Override
                            public void onComplete() {
                                assertAsyncContext(K1, V1, errorQueue);
                                assertAsyncContext(K2, V2, errorQueue);
                                assertAsyncContext(K3, V3, errorQueue);
                                assertAsyncContext(K4, V4, errorQueue);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                assertAsyncContext(K1, V1, errorQueue);
                                assertAsyncContext(K2, V2, errorQueue);
                                assertAsyncContext(K3, V3, errorQueue);
                                assertAsyncContext(K4, V4, errorQueue);
                            }

                            @Override
                            public void cancel() {
                                assertAsyncContext(K1, V1, errorQueue);
                                assertAsyncContext(K2, V2, errorQueue);
                                assertAsyncContext(K3, V3, errorQueue);
                                assertAsyncContext(K4, V4, errorQueue);
                            }
                        })).subscribeShareContext();
            }
        };
    }
}
