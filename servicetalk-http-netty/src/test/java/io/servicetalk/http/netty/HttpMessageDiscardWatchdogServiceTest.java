/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HttpMessageDiscardWatchdogServiceTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @ParameterizedTest(name = "{displayName} [{index}] transformer={0}")
    @MethodSource("responseTransformers")
    void cleansPayloadBodyIfDiscardedInFilter(final ResponseTransformer transformer) throws Exception {
        final AtomicInteger serviceCallCounter = new AtomicInteger();
        final AtomicInteger payloadSubscriptionCounter = new AtomicInteger();

        try (HttpServerContext serverContext = newServerBuilder(SERVER_CTX)
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return transformer.apply(delegate().handle(ctx, request, responseFactory), responseFactory);
                    }
                })
                .listenStreamingAndAwait((ctx, request, responseFactory) -> Single.fromSupplier(() -> {
                    serviceCallCounter.incrementAndGet();
                    final Publisher<Buffer> buffer = Publisher
                            .from(ctx.executionContext().bufferAllocator().fromUtf8("Hello, World!"))
                            .beforeOnSubscribe(subscription -> payloadSubscriptionCounter.incrementAndGet());
                    return responseFactory.ok().payloadBody(buffer);
                }))) {

            try (BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX)
                    .buildBlocking()) {
                HttpResponse response = client.request(client.get("/"));
                assertEquals(0, response.payloadBody().readableBytes());
            }

            assertTrue(serviceCallCounter.get() > 0);
            assertEquals(serviceCallCounter.get(), payloadSubscriptionCounter.get());
        }
    }

    private static Stream<Arguments> responseTransformers() {
        return Stream.of(
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final Single<StreamingHttpResponse> response,
                                                               final StreamingHttpResponseFactory responseFactory) {
                        return response.map(r -> {
                            throw new DeliberateException();
                        });
                    }

                    @Override
                    public String toString() {
                        return "Throws Exception";
                    }
                }),
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final Single<StreamingHttpResponse> response,
                                                               final StreamingHttpResponseFactory responseFactory) {
                        return response.beforeOnSuccess(r -> r.transformPayloadBody(payload -> Publisher.empty()));
                    }

                    @Override
                    public String toString() {
                        return "Drops payload body while transforming";
                    }
                }),
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final Single<StreamingHttpResponse> response,
                                                               final StreamingHttpResponseFactory responseFactory) {
                        return response.beforeOnSuccess(r -> r.transformMessageBody(msg -> Publisher.empty()));
                    }

                    @Override
                    public String toString() {
                        return "Drops message body while transforming";
                    }
                }),
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final Single<StreamingHttpResponse> response,
                                                               final StreamingHttpResponseFactory responseFactory) {
                        return response.map(dropped -> responseFactory.ok());
                    }

                    @Override
                    public String toString() {
                        return "Drops response and creates new one";
                    }
                }),
                Arguments.of(new ResponseTransformer() {

                    private final AtomicInteger retryCounter = new AtomicInteger();

                    @Override
                    public Single<StreamingHttpResponse> apply(final Single<StreamingHttpResponse> response,
                                                               final StreamingHttpResponseFactory responseFactory) {
                        return response.map(dropped -> {
                            if (retryCounter.getAndIncrement() > 0) {
                                return responseFactory.ok();
                            } else {
                                throw new DeliberateException();
                            }
                        }).retry((i, throwable) -> true);
                    }

                    @Override
                    public String toString() {
                        return "Retries and drops again";
                    }
                })
        );
    }

    interface ResponseTransformer extends BiFunction<
            Single<StreamingHttpResponse>, StreamingHttpResponseFactory, Single<StreamingHttpResponse>> {
    }
}
