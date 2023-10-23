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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class HttpMessageDiscardWatchdogClientFilterTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @BeforeEach
    public void setup() {
        LoggerStringWriter.reset();
    }

    @AfterEach
    public void tearDown() {
        LoggerStringWriter.remove();
    }

    /**
     * Asserts that the response message payload is cleaned up properly if discarded in a filter and not
     * properly cleaned up by the filter body.
     */
    @ParameterizedTest(name = "{displayName} [{index}] filterType={0} expectedException={1} transformer={2}")
    @MethodSource("responseTransformers")
    void warnsIfDiscarded(final FilterType filterType,
                                                    final @Nullable Class<?> expectedException,
                                                    ResponseTransformer transformer)
            throws Exception {
        // TODO: CONNECTION type filters currently time out instead of propagating the expectedException.
        // TODO: Once the root cause has been identified, those tests should be re-enabled again.
        assumeTrue(filterType == FilterType.CLIENT || expectedException == null);

        try (HttpServerContext serverContext = newServerBuilder(SERVER_CTX)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        Single.fromSupplier(() -> responseFactory.ok().payloadBody(Publisher.from(ctx.executionContext()
                                .bufferAllocator().fromUtf8("Hello, World!")))))) {
            try (StreamingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX)
                    .appendConnectionFilter(c -> new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                            if (filterType.equals(FilterType.CONNECTION)) {
                                return transformer.apply(delegate(), request);
                            } else {
                                return delegate().request(request);
                            }
                        }
                    })
                    .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                        @Override
                        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                        final StreamingHttpRequest request) {
                            if (filterType.equals(FilterType.CLIENT)) {
                                return transformer.apply(delegate, request);
                            } else {
                                return delegate.request(request);
                            }
                        }
                    })
                    .buildStreaming()) {

                int numRequests = 5;
                for (int i = 0; i < numRequests; i++) {
                    if (expectedException == null) {
                        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
                        assertEquals(HttpResponseStatus.OK, response.status());
                        // Consume the body to release the connection back to the pool
                        response.messageBody().ignoreElements().toFuture().get();
                    } else {
                        ExecutionException ex = assertThrows(ExecutionException.class,
                                () -> client.request(client.get("/")).toFuture().get());
                        assertTrue(ex.getCause().getClass().isAssignableFrom(expectedException));
                    }
                }

                String output = LoggerStringWriter.stableAccumulated(1000);
                System.err.println(output);
            }
        }
    }

    private enum FilterType {
        CLIENT,
        CONNECTION
    }

    private static Stream<Arguments> responseTransformers() {
        final List<Arguments> arguments = new ArrayList<>();

        for (FilterType filterType : FilterType.values()) {
            arguments.addAll(Arrays.asList(
                    Arguments.of(filterType, null, new ResponseTransformer() {
                        @Override
                        public Single<StreamingHttpResponse> apply(final StreamingHttpRequester requester,
                                                                   final StreamingHttpRequest request) {
                            return requester.request(request);
                        }

                        @Override
                        public String toString() {
                            return "Just delegation, no failure";
                        }
                    }),
                    Arguments.of(filterType, DeliberateException.class, new ResponseTransformer() {
                        @Override
                        public Single<StreamingHttpResponse> apply(final StreamingHttpRequester requester,
                                                                   final StreamingHttpRequest request) {
                            return requester
                                    .request(request)
                                    .map(dropped -> {
                                        throw new DeliberateException();
                                    });
                        }

                        @Override
                        public String toString() {
                            return "Throws exception in filter which drops message";
                        }
                    }),
                    Arguments.of(filterType, DeliberateException.class, new ResponseTransformer() {
                        @Override
                        public Single<StreamingHttpResponse> apply(final StreamingHttpRequester requester,
                                                                   final StreamingHttpRequest request) {
                            return requester
                                    .request(request)
                                    .flatMap(dropped -> Single.failed(new DeliberateException()));
                        }

                        @Override
                        public String toString() {
                            return "Returns a failed Single which drops message";
                        }
                    })
            ));
        }

        return arguments.stream();
    }

    interface ResponseTransformer
            extends BiFunction<StreamingHttpRequester, StreamingHttpRequest, Single<StreamingHttpResponse>> { }
}
