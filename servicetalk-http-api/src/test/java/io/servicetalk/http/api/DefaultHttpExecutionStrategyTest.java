/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyIoExecutors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.NoOffloadsHttpExecutionStrategy.NO_OFFLOADS_NO_EXECUTOR;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DefaultHttpExecutionStrategyTest {

    private boolean offloadReceiveMeta;
    private boolean offloadReceiveData;
    private boolean offloadSend;
    private HttpExecutionStrategy strategy;
    private final Executor executor = newCachedThreadExecutor();
    @RegisterExtension
    final ExecutionContextExtension contextRule =
            new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR,
                    () -> NettyIoExecutors.createIoExecutor(new DefaultThreadFactory()), () -> executor);

    private void setUp(final Params params) {
        this.offloadReceiveMeta = params.offloadReceiveMeta;
        this.offloadReceiveData = params.offloadReceiveData;
        this.offloadSend = params.offloadSend;
        HttpExecutionStrategies.Builder builder = customStrategyBuilder();
        if (params.strategySpecifiesExecutor) {
            builder.executor(executor);
        }
        if (offloadReceiveMeta) {
            builder.offloadReceiveMetadata();
        }
        if (offloadReceiveData) {
            builder.offloadReceiveData();
        }
        if (offloadSend) {
            builder.offloadSend();
        }
        strategy = builder.build();
    }

    enum Params {
        EXEC_OFFLOAD_NONE(false, false, false, true),
        EXEC_OFFLOAD_ALL(true, true, true, true),
        EXEC_OFFLOAD_RECV_META(true, false, false, true),
        EXEC_OFFLOAD_RECV_DATA(false, true, false, true),
        EXEC_OFFLOAD_RECV_ALL(true, true, false, true),
        EXEC_OFFLOAD_SEND(false, false, true, true),
        NO_EXEC_OFFLOAD_NONE(false, false, false, false),
        NO_EXEC_OFFLOAD_ALL(true, true, true, false),
        NO_EXEC_OFFLOAD_RECV_META(true, false, false, false),
        NO_EXEC_OFFLOAD_RECV_DATA(false, true, false, false),
        NO_EXEC_OFFLOAD_RECV_ALL(true, true, false, false),
        NO_EXEC_OFFLOAD_SEND(false, false, true, false);

        final boolean offloadReceiveMeta;
        final boolean offloadReceiveData;
        final boolean offloadSend;
        final boolean strategySpecifiesExecutor;

        Params(final boolean offloadReceiveMeta, final boolean offloadReceiveData,
               final boolean offloadSend, final boolean strategySpecifiesExecutor) {

            this.offloadReceiveMeta = offloadReceiveMeta;
            this.offloadReceiveData = offloadReceiveData;
            this.offloadSend = offloadSend;
            this.strategySpecifiesExecutor = strategySpecifiesExecutor;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void invokeClient(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpRequest req = analyzer.createNewRequest();
        StreamingHttpResponse resp = analyzer.createNewResponse();

        analyzer.instrumentedResponseForClient(
                strategy.invokeClient(executor, from(req, req.messageBody()),
                        null, (publisher, __) ->
                                analyzer.instrumentedFlatRequestForClient(publisher)
                                        .ignoreElements().concat(succeeded(resp))))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
            .toFuture().get();
        analyzer.verify();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void invokeCallableService(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        @SuppressWarnings("unchecked")
        Function<Executor, String> service = Mockito.mock(Function.class);
        strategy.invokeService(executor, e -> {
            analyzer.checkServiceInvocation();
            return service.apply(e);
        }).toFuture().get();
        analyzer.verifyNoErrors();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void wrapServiceThatWasAlreadyOffloaded(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpService svc = strategy.offloadService(executor, (ctx, request, responseFactory) -> {
            analyzer.checkContext(ctx);
            analyzer.checkServiceInvocationNotOffloaded();
            return succeeded(
                    analyzer.createNewResponse()
                            .payloadBody(
                                    analyzer.instrumentedRequestPayloadForServerNotOffloaded(request.payloadBody())));
        });
        StreamingHttpRequest req = analyzer.createNewRequest();
        DefaultStreamingHttpRequestResponseFactory respFactory =
                new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);
        TestHttpServiceContext ctx =
                new TestHttpServiceContext(INSTANCE, respFactory,
                        // Use the same strategy for the ctx to indicate that server already did all required offloads.
                        // So, the difference function inside #offloadService will return null.
                        new ExecutionContextToHttpExecutionContext(contextRule,
                                strategy));
        analyzer.instrumentedResponseForServerNotOffloaded(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .toFuture().get();
        analyzer.verify();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void wrapServiceThatWasNotOffloaded(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpService svc = strategy.offloadService(executor, (ctx, request, responseFactory) -> {
            analyzer.checkContext(ctx);
            analyzer.checkServiceInvocation();
            return succeeded(analyzer.createNewResponse()
                                 .payloadBody(analyzer.instrumentedRequestPayloadForServer(request.payloadBody())));
        });
        StreamingHttpRequest req = analyzer.createNewRequest();
        DefaultStreamingHttpRequestResponseFactory respFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);
        TestHttpServiceContext ctx =
                new TestHttpServiceContext(INSTANCE, respFactory,
                        // Use noOffloadsStrategy() for the ctx to indicate that there was no offloading before.
                        // So, the difference function inside #offloadService will return the tested strategy.
                        new ExecutionContextToHttpExecutionContext(contextRule,
                                noOffloadsStrategy()));
        analyzer.instrumentedResponseForServer(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
            .toFuture().get();
        analyzer.verify();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void offloadSendSingle(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, never())).subscribe(__ -> {
        }).cancel();
        analyzer.awaitCancel.await();
        analyzer.verifySend();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void offloadSendPublisher(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, from(1))).toFuture().get();
        analyzer.verifySend();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void offloadReceiveSingle(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentReceive(strategy.offloadReceive(executor, succeeded(1))).toFuture().get();
        analyzer.verifyReceive();
    }

    @ParameterizedTest
    @EnumSource(Params.class)
    void offloadReceivePublisher(final Params params) throws Exception {
        setUp(params);
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentReceive(strategy.offloadReceive(executor, from(1))).toFuture().get();
        analyzer.verifyReceive();
    }

    private final class ThreadAnalyzer {

        private static final int SEND_ANALYZED_INDEX = 0;
        private static final int RECEIVE_META_ANALYZED_INDEX = 1;
        private static final int RECEIVE_DATA_ANALYZED_INDEX = 2;
        private final ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        private final Thread testThread = currentThread();
        private final AtomicReferenceArray<Boolean> analyzed = new AtomicReferenceArray<>(3);
        private final CountDownLatch awaitCancel = new CountDownLatch(1);

        StreamingHttpRequest createNewRequest() {
            return newRequest(GET, "/", HTTP_1_1, INSTANCE.newHeaders(), DEFAULT_ALLOCATOR, INSTANCE)
                .payloadBody(from(DEFAULT_ALLOCATOR.fromAscii("Hello")));
        }

        StreamingHttpResponse createNewResponse() {
            return newResponse(HttpResponseStatus.OK, HTTP_1_1, INSTANCE.newHeaders(),
                               DEFAULT_ALLOCATOR, INSTANCE)
                .payloadBody(from(DEFAULT_ALLOCATOR.fromAscii("Hello-Response")));
        }

        Single<StreamingHttpResponse> instrumentedResponseForClient(Single<StreamingHttpResponse> resp) {
            return resp.map(response -> response.transformPayloadBody(p -> p.beforeOnNext(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(offloadReceiveData, "Unexpected thread for response payload onNext.");
            }))).beforeOnSuccess(__ -> {
                analyzed.set(RECEIVE_META_ANALYZED_INDEX, true);
                verifyThread(offloadReceiveMeta, "Unexpected thread for response single.");
            });
        }

        Single<StreamingHttpResponse> instrumentedResponseForServer(Single<StreamingHttpResponse> resp) {
            return resp.map(response -> response.transformPayloadBody(p -> p.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from request.");
            })));
        }

        Single<StreamingHttpResponse> instrumentedResponseForServerNotOffloaded(Single<StreamingHttpResponse> resp) {
            return resp.map(response -> response.transformPayloadBody(p -> p.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                if (testThread != currentThread()) {
                    addError("Unexpected offloading for thread requested from request.");
                }
            })));
        }

        Publisher<Object> instrumentedFlatRequestForClient(Publisher<Object> req) {
            return req.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from request.");
            });
        }

        <T> Single<T> instrumentSend(Single<T> original) {
            return original.beforeCancel(() -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from cancel.");
                awaitCancel.countDown();
            });
        }

        <T> Publisher<T> instrumentSend(Publisher<T> original) {
            return original.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from request.");
            });
        }

        <T> Single<T> instrumentReceive(Single<T> original) {
            return original.beforeOnSuccess(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(offloadReceiveData, "Unexpected thread requested from success.");
            });
        }

        <T> Publisher<T> instrumentReceive(Publisher<T> original) {
            return original.beforeOnNext(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(offloadReceiveData, "Unexpected thread requested from next.");
            });
        }

        void checkContext(HttpServiceContext context) {
            if (noOffloads()) {
                Executor expectedExecutor = strategy.executor() != null ? strategy.executor() : contextRule.executor();
                if (expectedExecutor != context.executionContext().executor()) {
                    errors.add(new AssertionError("Unexpected executor in context. Expected: " +
                            expectedExecutor + ", actual: " + context.executionContext().executor()));
                }
            } else if (context.executionContext().executor() != executor) {
                errors.add(new AssertionError("Unexpected executor in context. Expected: " + executor +
                                              ", actual: " + context.executionContext().executor()));
            }
        }

        private boolean noOffloads() {
            return !offloadReceiveData && !offloadReceiveMeta && !offloadSend;
        }

        void checkServiceInvocation() {
            analyzed.set(RECEIVE_META_ANALYZED_INDEX, true);
            verifyThread(offloadReceiveMeta, "Unexpected thread invoked service.");
        }

        void checkServiceInvocationNotOffloaded() {
            analyzed.set(RECEIVE_META_ANALYZED_INDEX, true);
            if (testThread != currentThread()) {
                addError("Unexpected offloading for the invoked service.");
            }
        }

        Publisher<Buffer> instrumentedRequestPayloadForServer(Publisher<Buffer> req) {
            return req.beforeOnNext(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(offloadReceiveData, "Unexpected thread for request payload onNext.");
            });
        }

        Publisher<Buffer> instrumentedRequestPayloadForServerNotOffloaded(Publisher<Buffer> req) {
            return req.beforeOnNext(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                if (testThread != currentThread()) {
                    addError("Unexpected offloading for request payload onNext.");
                }
            });
        }

        Publisher<Object> instrumentedResponseForServer(Publisher<Object> resp) {
            return resp.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(offloadSend, "Unexpected thread requested from response.");
            });
        }

        void verifyThread(final boolean offloadedPath, final String errMsg) {
            if (strategy == NO_OFFLOADS_NO_EXECUTOR && testThread != currentThread()) {
                addError(errMsg);
            } else if (offloadedPath && testThread == currentThread()) {
                addError(errMsg);
            }
        }

        void verifySend() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        void verifyReceive() {
            assertThat("Receive data path not analyzed.", analyzed.get(RECEIVE_DATA_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        void verify() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            assertThat("Receive metadata path not analyzed.", analyzed.get(RECEIVE_META_ANALYZED_INDEX), is(true));
            assertThat("Receive data path not analyzed.", analyzed.get(RECEIVE_DATA_ANALYZED_INDEX), is(true));
            verifyNoErrors();
        }

        void verifyNoErrors() {
            assertNoAsyncErrors(errors);
        }

        private void addError(final String msg) {
            errors.add(new AssertionError(msg + " Thread: " + currentThread()));
        }
    }
}
