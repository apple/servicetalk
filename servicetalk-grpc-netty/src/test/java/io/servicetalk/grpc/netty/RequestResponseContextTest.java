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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
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
import io.servicetalk.grpc.netty.TesterProto.Tester;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
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
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_STATUS;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestResponseContextTest {

    private static final Key<CharSequence> CLIENT_CTX = newKey("CLIENT_CTX", CharSequence.class);
    private static final Key<CharSequence> CLIENT_FILTER_OUT_CTX = newKey("CLIENT_FILTER_OUT_CTX", CharSequence.class);
    private static final Key<CharSequence> CLIENT_FILTER_IN_CTX = newKey("CLIENT_FILTER_IN_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_IN_CTX = newKey("SERVER_FILTER_IN_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_CTX = newKey("SERVER_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_OUT_CTX = newKey("SERVER_FILTER_OUT_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_IN_TRAILER_CTX =
            newKey("SERVER_FILTER_IN_TRAILER_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_TRAILER_CTX = newKey("SERVER_TRAILER_CTX", CharSequence.class);
    private static final Key<CharSequence> SERVER_FILTER_OUT_TRAILER_CTX =
            newKey("SERVER_FILTER_OUT_TRAILER_CTX", CharSequence.class);
    private static final Key<CharSequence> CLIENT_FILTER_IN_TRAILER_CTX =
            newKey("CLIENT_FILTER_IN_TRAILER_CTX", CharSequence.class);

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
                (client, metadata) -> {
                    BlockingIterator<TestResponse> iterator = client.asBlockingClient()
                            .testBiDiStream(metadata, singletonList(REQUEST)).iterator();
                    assertThat(iterator.hasNext(), is(true));
                    if (error) {
                        iterator.next();    // will throw
                        throw new AssertionError("No error from response");
                    } else {
                        TestResponse response = iterator.next();
                        assertThat(response, is(notNullValue()));
                        assertThat(iterator.hasNext(), is(false));
                        return response;
                    }
                }, error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testResponseStreamBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> {
                    BlockingIterator<TestResponse> iterator = client.asBlockingClient()
                            .testResponseStream(metadata, REQUEST).iterator();
                    assertThat(iterator.hasNext(), is(true));
                    if (error) {
                        iterator.next();    // will throw
                        throw new AssertionError("No error from response");
                    } else {
                        TestResponse response = iterator.next();
                        assertThat(response, is(notNullValue()));
                        assertThat(iterator.hasNext(), is(false));
                        return response;
                    }
                }, error);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testRequestStreamBlocking(boolean error) throws Exception {
        testRequestResponse(new BlockingTesterServiceImpl(error),
                (client, metadata) -> client.asBlockingClient().testRequestStream(metadata, singletonList(REQUEST)),
                error);
    }

    private static void testRequestResponse(GrpcBindableService<TesterService> service, Exchange exchange,
                                            boolean error) throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(httpBuilder -> httpBuilder.appendServiceFilter(new ContextHttpServiceFilter()))
                .listenAndAwait(service);
             TesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                     .initializeHttp(httpBuilder -> httpBuilder.appendClientFilter(new ContextHttpClientFilter()))
                     .build(new Tester.ClientFactory())) {

            GrpcClientMetadata metadata = new DefaultGrpcClientMetadata();
            metadata.requestContext().put(CLIENT_CTX, value(CLIENT_CTX));

            if (error) {
                GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> exchange.send(client, metadata));
                assertThat(e.status().code(), is(UNKNOWN));
            } else {
                TestResponse response = exchange.send(client, metadata);
                assertThat(response.getMessage(), contentEqualTo(
                        join(":", value(CLIENT_CTX), value(CLIENT_FILTER_OUT_CTX), value(SERVER_FILTER_IN_CTX))));
            }

            ContextMap requestContext = metadata.requestContext();
            assertThat(requestContext.get(CLIENT_CTX), contentEqualTo(value(CLIENT_CTX)));
            assertThat(requestContext.get(CLIENT_FILTER_OUT_CTX), contentEqualTo(value(CLIENT_FILTER_OUT_CTX)));

            ContextMap responseContext = metadata.responseContext();
            assertThat(responseContext.get(CLIENT_FILTER_IN_CTX), contentEqualTo(value(CLIENT_FILTER_IN_CTX)));
            assertThat(responseContext.get(SERVER_FILTER_IN_CTX), contentEqualTo(value(SERVER_FILTER_IN_CTX)));
            assertThat(responseContext.get(SERVER_CTX), contentEqualTo(value(SERVER_CTX)));
            assertThat(responseContext.get(SERVER_FILTER_OUT_CTX), contentEqualTo(value(SERVER_FILTER_OUT_CTX)));

            assertThat(responseContext.get(CLIENT_FILTER_IN_TRAILER_CTX),
                    contentEqualTo(value(CLIENT_FILTER_IN_TRAILER_CTX)));
            assertThat(responseContext.get(SERVER_FILTER_IN_TRAILER_CTX),
                    contentEqualTo(value(SERVER_FILTER_IN_TRAILER_CTX)));
            assertThat(responseContext.get(SERVER_TRAILER_CTX), contentEqualTo(value(SERVER_TRAILER_CTX)));
            assertThat(responseContext.get(SERVER_FILTER_OUT_TRAILER_CTX),
                    contentEqualTo(value(SERVER_FILTER_OUT_TRAILER_CTX)));
        }
    }

    private static String header(Key<CharSequence> key) {
        return key.name().toLowerCase(Locale.ROOT) + "_header";
    }

    private static String value(Key<CharSequence> key) {
        return key.name() + "_VALUE";
    }

    private static void setHeadersContext(GrpcServiceContext ctx) {
        ctx.responseContext().put(SERVER_FILTER_IN_CTX, ctx.requestContext().get(SERVER_FILTER_IN_CTX));
        ctx.responseContext().put(SERVER_CTX, value(SERVER_CTX));
    }

    private static void setTrailersContext(GrpcServiceContext ctx) {
        ctx.responseContext().put(SERVER_FILTER_IN_TRAILER_CTX, ctx.requestContext().get(SERVER_FILTER_IN_TRAILER_CTX));
        ctx.responseContext().put(SERVER_TRAILER_CTX, value(SERVER_TRAILER_CTX));
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
                setHeadersContext(ctx);
                setTrailersContext(ctx);
                return error ? Single.failed(DELIBERATE_EXCEPTION) : succeeded(newResponse(ctx.requestContext()));
            });
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            setHeadersContext(ctx);
            return request.ignoreElements()
                    .whenOnComplete(() -> setTrailersContext(ctx))
                    .concat(error ? Publisher.failed(DELIBERATE_EXCEPTION) : from(newResponse(ctx.requestContext())));
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            setHeadersContext(ctx);
            return Publisher.defer(() -> {
                setTrailersContext(ctx);
                return error ? Publisher.failed(DELIBERATE_EXCEPTION) : from(newResponse(ctx.requestContext()));
            });
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            setHeadersContext(ctx);
            return request.ignoreElements()
                    .whenOnComplete(() -> setTrailersContext(ctx))
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
            setHeadersContext(ctx);
            setTrailersContext(ctx);
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
            setHeadersContext(ctx);
            request.forEach(__ -> { /* noop */ });
            if (error) {
                setTrailersContext(ctx);
                throw DELIBERATE_EXCEPTION;
            }
            try (GrpcPayloadWriter<TestResponse> writer = response.sendMetaData()) {
                writer.write(newResponse(ctx.requestContext()));
                setTrailersContext(ctx);
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
            setHeadersContext(ctx);
            if (error) {
                setTrailersContext(ctx);
                throw DELIBERATE_EXCEPTION;
            }
            try (GrpcPayloadWriter<TestResponse> writer = response.sendMetaData()) {
                writer.write(newResponse(ctx.requestContext()));
                setTrailersContext(ctx);
            }
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request) {
            setHeadersContext(ctx);
            request.forEach(__ -> { /* noop */ });
            setTrailersContext(ctx);
            if (error) {
                throw DELIBERATE_EXCEPTION;
            }
            return newResponse(ctx.requestContext());
        }
    }

    private static final class ContextHttpServiceFilter implements StreamingHttpServiceFilterFactory {

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                            StreamingHttpRequest request,
                                                            StreamingHttpResponseFactory responseFactory) {
                    // Take client-side values from headers:
                    request.context().put(CLIENT_CTX, request.headers().get(header(CLIENT_CTX)));
                    request.context().put(CLIENT_FILTER_OUT_CTX, request.headers().get(header(CLIENT_FILTER_OUT_CTX)));
                    // Set server-side values:
                    request.context().put(SERVER_FILTER_IN_CTX, value(SERVER_FILTER_IN_CTX));
                    request.context().put(SERVER_FILTER_IN_TRAILER_CTX, value(SERVER_FILTER_IN_TRAILER_CTX));
                    return delegate().handle(ctx, request, responseFactory).map(response -> {
                        HttpHeaders headers = response.headers();
                        // Take the first two values from context:
                        headers.set(header(SERVER_FILTER_IN_CTX),
                                requireNonNull(response.context().get(SERVER_FILTER_IN_CTX)));
                        headers.set(header(SERVER_CTX), requireNonNull(response.context().get(SERVER_CTX)));
                        // Set the last value explicitly:
                        assertThat(response.context().containsKey(SERVER_FILTER_OUT_CTX), is(false));
                        headers.set(header(SERVER_FILTER_OUT_CTX), value(SERVER_FILTER_OUT_CTX));

                        // For Trailers-Only response put everything into headers:
                        if (headers.contains(GRPC_STATUS)) {
                            setTrailers(response.context(), headers);
                            return response;
                        }
                        return response.transform(new StatelessTrailersTransformer<Buffer>() {
                            @Override
                            protected HttpHeaders payloadComplete(HttpHeaders trailers) {
                                setTrailers(response.context(), trailers);
                                return trailers;
                            }
                        });
                    });
                }
            };
        }

        private static void setTrailers(ContextMap ctx, HttpHeaders trailers) {
            // Take the first two values from context:
            trailers.set(header(SERVER_FILTER_IN_TRAILER_CTX), requireNonNull(ctx.get(SERVER_FILTER_IN_TRAILER_CTX)));
            trailers.set(header(SERVER_TRAILER_CTX), requireNonNull(ctx.get(SERVER_TRAILER_CTX)));
            // Set the last value explicitly:
            trailers.set(header(SERVER_FILTER_OUT_TRAILER_CTX), value(SERVER_FILTER_OUT_TRAILER_CTX));
        }
    }

    private static final class ContextHttpClientFilter implements StreamingHttpClientFilterFactory {

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                StreamingHttpRequest request) {
                    request.headers().set(header(CLIENT_CTX), requireNonNull(request.context().get(CLIENT_CTX)));
                    request.context().put(CLIENT_FILTER_OUT_CTX, value(CLIENT_FILTER_OUT_CTX));
                    request.headers().set(header(CLIENT_FILTER_OUT_CTX),
                            requireNonNull(request.context().get(CLIENT_FILTER_OUT_CTX)));
                    return delegate.request(request).shareContextOnSubscribe().map(response -> {
                        HttpHeaders headers = response.headers();
                        // Take the first three values from headers:
                        response.context().put(SERVER_FILTER_IN_CTX, headers.get(header(SERVER_FILTER_IN_CTX)));
                        response.context().put(SERVER_CTX, headers.get(header(SERVER_CTX)));
                        response.context().put(SERVER_FILTER_OUT_CTX, headers.get(header(SERVER_FILTER_OUT_CTX)));
                        // Set the last value explicitly:
                        response.context().put(CLIENT_FILTER_IN_CTX, value(CLIENT_FILTER_IN_CTX));

                        // For Trailers-Only response take everything from headers:
                        if (headers.contains(GRPC_STATUS)) {
                            readTrailers(headers, response.context());
                            return response;
                        }
                        return response.transform(new StatelessTrailersTransformer<Buffer>() {
                            @Override
                            protected HttpHeaders payloadComplete(HttpHeaders trailers) {
                                readTrailers(trailers, response.context());
                                return trailers;
                            }
                        });
                    });
                }
            };
        }

        private static void readTrailers(HttpHeaders trailers, ContextMap ctx) {
            // Take the first three values from trailers:
            ctx.put(SERVER_FILTER_IN_TRAILER_CTX, trailers.get(header(SERVER_FILTER_IN_TRAILER_CTX)));
            ctx.put(SERVER_TRAILER_CTX, trailers.get(header(SERVER_TRAILER_CTX)));
            ctx.put(SERVER_FILTER_OUT_TRAILER_CTX, trailers.get(header(SERVER_FILTER_OUT_TRAILER_CTX)));
            // Set the last value explicitly:
            ctx.put(CLIENT_FILTER_IN_TRAILER_CTX, value(CLIENT_FILTER_IN_TRAILER_CTX));
        }
    }
}
