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
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class HttpMessageDiscardWatchdogServiceFilterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMessageDiscardWatchdogServiceFilterTest.class);

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();

    @BeforeEach
    public void setup() {
        loggerStringWriter.reset();
        // Ensure our logger is fully initialized.
        String expected = "Logger initialized";
        do {
            LOGGER.info(expected);
        } while (!loggerStringWriter.accumulated().contains(expected));
    }

    @AfterEach
    public void tearDown() {
        loggerStringWriter.remove();
    }

    @ParameterizedTest(name = "{displayName} [{index}] transformer={0}")
    @MethodSource("responseTransformers")
    void warnsIfDiscarded(final ResponseTransformer transformer) throws Exception {
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
                    final Publisher<Buffer> buffer = Publisher
                            .from(ctx.executionContext().bufferAllocator().fromUtf8("Hello, World!"));
                    return responseFactory.ok().payloadBody(buffer);
                }))) {

            try (BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX)
                    .buildBlocking()) {
                HttpResponse response = client.request(client.get("/"));
                assertEquals(0, response.payloadBody().readableBytes());
            }

            String output = loggerStringWriter.stableAccumulated(CI ? 5000 : 1000);
            if (!output.contains("Discovered un-drained HTTP response message body which " +
                    "has been dropped by user code")) {
                throw new AssertionError("Logs didn't contain the expected output:\n-- START OUTPUT--\n"
                        + output + "\n-- END OUTPUT --");
            }
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
