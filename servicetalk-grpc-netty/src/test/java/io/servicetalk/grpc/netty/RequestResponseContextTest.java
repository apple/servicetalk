/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.grpc.api.BlockingStreamingGrpcServerResponse;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcBindableService;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestResponseContextTest {

    private static final Key<CharSequence> CLIENT_CTX = newKey("CLIENT_CTX", CharSequence.class);
    private static final Key<CharSequence> CLIENT_FILTER_OUT_CTX =
            newKey("CLIENT_FILTER_OUT_CTX", CharSequence.class);
    private static final Key<CharSequence> CLIENT_FILTER_IN_CTX =
            newKey("CLIENT_FILTER_IN_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_IN_CTX =
            newKey("SERVER_FILTER_IN_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_CTX = newKey("SERVER_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_OUT_CTX =
            newKey("SERVER_FILTER_OUT_CTX", CharSequence.class);

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("name").build();

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void test(boolean error) throws Exception {
        testRequestResponse(new TesterServiceImpl(error),
                (client, metadata) -> blockingInvocation(client.test(metadata, REQUEST)), error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testBiDiStream(boolean error) throws Exception {
        testRequestResponse(new TesterServiceImpl(error),
                (client, metadata) -> blockingInvocation(client.testBiDiStream(metadata, from(REQUEST)).firstOrError()),
                error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testResponseStream(boolean error) throws Exception {
        testRequestResponse(new TesterServiceImpl(error),
                (client, metadata) -> blockingInvocation(client.testResponseStream(metadata, REQUEST).firstOrError()),
                error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testRequestStream(boolean error) throws Exception {
        testRequestResponse(new TesterServiceImpl(error),
                (client, metadata) -> blockingInvocation(client.testRequestStream(metadata, from(REQUEST))), error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> client.asBlockingClient().test(metadata, REQUEST), error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testBiDiStreamBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> client.asBlockingClient().testBiDiStream(metadata, singletonList(REQUEST))
                        .iterator().next(), error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testResponseStreamBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> client.asBlockingClient().testResponseStream(metadata, REQUEST)
                        .iterator().next(), error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testRequestStreamBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> client.asBlockingClient().testRequestStream(metadata, singletonList(REQUEST)),
                error);
    }

    private static void testRequestResponse(GrpcBindableService<?> service, Exchange exchange,
                                            boolean error) throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(httpBuilder -> httpBuilder.appendServiceFilter(s -> new StreamingHttpServiceFilter(s) {

                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                                StreamingHttpRequest request,
                                                                StreamingHttpResponseFactory responseFactory) {
                        // Take the first two values from headers
                        request.context().put(CLIENT_CTX, request.headers().get(header(CLIENT_CTX)));
                        request.context().put(CLIENT_FILTER_OUT_CTX,
                                request.headers().get(header(CLIENT_FILTER_OUT_CTX)));
                        // Set the last value to context only
                        request.context().put(SERVER_FILTER_IN_CTX, value(SERVER_FILTER_IN_CTX));
                        return delegate().handle(ctx, request, responseFactory)
                                .whenOnSuccess(response -> {
                                    // Take the first two values from context
                                    response.headers().set(header(SERVER_FILTER_IN_CTX),
                                            requireNonNull(response.context().get(SERVER_FILTER_IN_CTX)));
                                    response.headers().set(header(SERVER_CTX),
                                            requireNonNull(response.context().get(SERVER_CTX)));
                                    // Set the last value to headers only
                                    assertThat(response.context().containsKey(SERVER_FILTER_OUT_CTX), is(false));
                                    response.headers().set(header(SERVER_FILTER_OUT_CTX),
                                            value(SERVER_FILTER_OUT_CTX));
                                });
                    }
                }))
                .listenAndAwait(service);
             TesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                     .initializeHttp(httpBuilder -> httpBuilder
                             .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                                 @Override
                                 protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                                 StreamingHttpRequest request) {
                                     return Single.defer(() -> {
                                         request.headers().set(header(CLIENT_CTX),
                                                 requireNonNull(request.context().get(CLIENT_CTX)));
                                         request.context().put(CLIENT_FILTER_OUT_CTX, value(CLIENT_FILTER_OUT_CTX));
                                         request.headers().set(header(CLIENT_FILTER_OUT_CTX),
                                                 requireNonNull(request.context().get(CLIENT_FILTER_OUT_CTX)));
                                         return delegate.request(request).shareContextOnSubscribe()
                                                 .whenOnSuccess(response -> {
                                                     // Take the first three values from headers
                                                     response.context().put(SERVER_FILTER_IN_CTX,
                                                             response.headers().get(header(SERVER_FILTER_IN_CTX)));
                                                     response.context().put(SERVER_CTX,
                                                             response.headers().get(header(SERVER_CTX)));
                                                     response.context().put(SERVER_FILTER_OUT_CTX,
                                                             response.headers().get(header(SERVER_FILTER_OUT_CTX)));
                                                     // Set the last value to context only
                                                     response.context().put(CLIENT_FILTER_IN_CTX,
                                                             value(CLIENT_FILTER_IN_CTX));
                                                 });
                                     });
                                 }
                             }))
                     .build(new TesterProto.Tester.ClientFactory())) {

            GrpcClientMetadata metadata = new DefaultGrpcClientMetadata();
            metadata.requestContext().put(CLIENT_CTX, value(CLIENT_CTX));

            if (error) {
                GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> exchange.send(client, metadata));
                assertThat(e.status().code(), is(UNKNOWN));
            } else {
                TestResponse response = exchange.send(client, metadata);
                assertThat(response.getMessage(), is(contentEqualTo(
                        join(":", value(CLIENT_CTX), value(CLIENT_FILTER_OUT_CTX), value(SERVER_FILTER_IN_CTX)))));
            }

            ContextMap requestContext = metadata.requestContext();
            assertThat(requestContext.get(CLIENT_CTX), is(contentEqualTo(value(CLIENT_CTX))));
            assertThat(requestContext.get(CLIENT_FILTER_OUT_CTX), is(contentEqualTo(value(CLIENT_FILTER_OUT_CTX))));

            ContextMap responseContext = metadata.responseContext();
            assertThat(responseContext.get(CLIENT_FILTER_IN_CTX), is(contentEqualTo(value(CLIENT_FILTER_IN_CTX))));
            assertThat(responseContext.get(SERVER_FILTER_IN_CTX), is(contentEqualTo(value(SERVER_FILTER_IN_CTX))));
            assertThat(responseContext.get(SERVER_CTX), is(contentEqualTo(value(SERVER_CTX))));
            assertThat(responseContext.get(SERVER_FILTER_OUT_CTX), is(contentEqualTo(value(SERVER_FILTER_OUT_CTX))));
        }
    }

    private static String header(Key<CharSequence> key) {
        return key.name().toLowerCase(Locale.ROOT) + "_header";
    }

    private static String value(Key<CharSequence> key) {
        return key.name() + "_VALUE";
    }

    private static void setContext(GrpcServiceContext ctx) {
        ctx.responseContext().put(SERVER_FILTER_IN_CTX, ctx.requestContext().get(SERVER_FILTER_IN_CTX));
        ctx.responseContext().put(SERVER_CTX, value(SERVER_CTX));
    }

    private static TestResponse newResponse(ContextMap requestContext) {
        return TestResponse.newBuilder()
                .setMessage(join(":",
                        requestContext.get(CLIENT_CTX),
                        requestContext.get(CLIENT_FILTER_OUT_CTX),
                        requestContext.get(SERVER_FILTER_IN_CTX)))
                .build();
    }

    @FunctionalInterface
    private interface Exchange {
        TestResponse send(TesterClient client, GrpcClientMetadata metadata) throws Exception;
    }

    private static final class TesterServiceImpl implements TesterService {

        private final boolean error;

        private TesterServiceImpl(final boolean error) {
            this.error = error;
        }

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            return Single.defer(() -> {
                setContext(ctx);
                return error ? Single.failed(DELIBERATE_EXCEPTION) : succeeded(newResponse(ctx.requestContext()));
            });
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return request.ignoreElements()
                    .whenOnComplete(() -> setContext(ctx))
                    .concat(error ? Publisher.failed(DELIBERATE_EXCEPTION) : from(newResponse(ctx.requestContext())));
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            return Publisher.defer(() -> {
                setContext(ctx);
                return error ? Publisher.failed(DELIBERATE_EXCEPTION) : from(newResponse(ctx.requestContext()));
            });
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return request.ignoreElements()
                    .whenOnComplete(() -> setContext(ctx))
                    .concat(error ? Single.failed(DELIBERATE_EXCEPTION) : succeeded(newResponse(ctx.requestContext())));
        }
    }

    private static final class BlockingTesterServiceImpl implements BlockingTesterService {

        private final boolean error;

        private BlockingTesterServiceImpl(final boolean error) {
            this.error = error;
        }

        @Override
        public TestResponse test(GrpcServiceContext ctx, TestRequest request) {
            setContext(ctx);
            if (error) {
                throw DELIBERATE_EXCEPTION;
            }
            return newResponse(ctx.requestContext());
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   GrpcPayloadWriter<TestResponse> responseWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   BlockingStreamingGrpcServerResponse<TestResponse> response) throws Exception {
            assertThat(ctx.responseContext(), is(sameInstance(response.context())));
            setContext(ctx);
            if (error) {
                throw DELIBERATE_EXCEPTION;
            }
            try (GrpcPayloadWriter<TestResponse> writer = response.sendMetaData()) {
                writer.write(newResponse(ctx.requestContext()));
            }
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       BlockingStreamingGrpcServerResponse<TestResponse> response) throws Exception {
            assertThat(ctx.responseContext(), is(sameInstance(response.context())));
            setContext(ctx);
            if (error) {
                throw DELIBERATE_EXCEPTION;
            }
            try (GrpcPayloadWriter<TestResponse> writer = response.sendMetaData()) {
                writer.write(newResponse(ctx.requestContext()));
            }
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request) {
            setContext(ctx);
            if (error) {
                throw DELIBERATE_EXCEPTION;
            }
            return newResponse(ctx.requestContext());
        }
    }
}
