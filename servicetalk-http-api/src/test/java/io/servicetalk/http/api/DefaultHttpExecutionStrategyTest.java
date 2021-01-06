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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyIoExecutors;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.failed;
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
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class DefaultHttpExecutionStrategyTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final boolean offloadReceiveMeta;
    private final boolean offloadReceiveData;
    private final boolean offloadSend;
    private final HttpExecutionStrategy strategy;
    private final Executor executor = newCachedThreadExecutor();
    @Rule
    public final ExecutionContextRule contextRule = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> NettyIoExecutors.createIoExecutor(new DefaultThreadFactory()), () -> executor);

    public DefaultHttpExecutionStrategyTest(@SuppressWarnings("unused") final String description,
                                            final boolean offloadReceiveMeta, final boolean offloadReceiveData,
                                            final boolean offloadSend, final boolean strategySpecifiesExecutor) {
        this.offloadReceiveMeta = offloadReceiveMeta;
        this.offloadReceiveData = offloadReceiveData;
        this.offloadSend = offloadSend;
        HttpExecutionStrategies.Builder builder = customStrategyBuilder();
        if (strategySpecifiesExecutor) {
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

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam("exec & offload none", false, false, false, true));
        params.add(newParam("exec & offload all", true, true, true, true));
        params.add(newParam("exec & offload recv meta", true, false, false, true));
        params.add(newParam("exec & offload recv data", false, true, false, true));
        params.add(newParam("exec & offload recv all", true, true, false, true));
        params.add(newParam("exec & offload send", false, false, true, true));
        params.add(newParam("no exec & offload none", false, false, false, false));
        params.add(newParam("no exec & offload all", true, true, true, false));
        params.add(newParam("no exec & offload recv meta", true, false, false, false));
        params.add(newParam("no exec & offload recv data", false, true, false, false));
        params.add(newParam("no exec & offload recv all", true, true, false, false));
        params.add(newParam("no exec & offload send", false, false, true, false));
        return params;
    }

    private static Object[] newParam(String description, final boolean offloadReceiveMeta,
                                     final boolean offloadReceiveData, final boolean offloadSend,
                                     final boolean strategySpecifiesExecutor) {
        return new Object[]{description, offloadReceiveMeta, offloadReceiveData, offloadSend,
                strategySpecifiesExecutor};
    }

    @After
    public void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Test
    public void invokeClient() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpRequest req = analyzer.createNewRequest();
        StreamingHttpResponse resp = analyzer.createNewResponse();

        analyzer.instrumentedResponseForClient(strategy.invokeClient(executor, from(req, req.messageBody()),
                null, (publisher, __) ->
                        analyzer.instrumentedFlatRequestForClient(publisher).ignoreElements().concat(succeeded(resp))))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .toFuture().get();
        analyzer.verify();
    }

    @Test
    public void invokeService() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpRequest req = analyzer.createNewRequest();
        StreamingHttpResponse resp = analyzer.createNewResponse();

        analyzer.instrumentedResponseForServer(strategy.invokeService(executor, req, request -> {
            analyzer.checkServiceInvocation();
            return analyzer.instrumentedRequestPayloadForServer(request.payloadBody())
                    .ignoreElements().concat(Publisher.<Object>from(resp)).concat(resp.messageBody());
        }, (throwable, executor1) -> failed(throwable))).toFuture().get();

        analyzer.verify();
    }

    @Test
    public void invokeCallableService() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        @SuppressWarnings("unchecked")
        Function<Executor, String> service = Mockito.mock(Function.class);
        strategy.invokeService(executor, e -> {
            analyzer.checkServiceInvocation();
            return service.apply(e);
        }).toFuture().get();
        analyzer.verifyNoErrors();
    }

    @Test
    public void wrapServiceThatWasAlreadyOffloaded() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        StreamingHttpService svc = strategy.offloadService(executor, (ctx, request, responseFactory) -> {
            analyzer.checkContext(ctx);
            analyzer.checkServiceInvocationNotOffloaded();
            return succeeded(analyzer.createNewResponse()
                    .payloadBody(analyzer.instrumentedRequestPayloadForServerNotOffloaded(request.payloadBody())));
        });
        StreamingHttpRequest req = analyzer.createNewRequest();
        DefaultStreamingHttpRequestResponseFactory respFactory =
                new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);
        TestHttpServiceContext ctx = new TestHttpServiceContext(INSTANCE, respFactory,
                // Use the same strategy for the ctx to indicate that server already did all required offloads.
                // So, the difference function inside #offloadService will return null.
                new ExecutionContextToHttpExecutionContext(contextRule, strategy));
        analyzer.instrumentedResponseForServerNotOffloaded(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .toFuture().get();
        analyzer.verify();
    }

    @Test
    public void wrapServiceThatWasNotOffloaded() throws Exception {
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
        TestHttpServiceContext ctx = new TestHttpServiceContext(INSTANCE, respFactory,
                // Use noOffloadsStrategy() for the ctx to indicate that there was no offloading before.
                // So, the difference function inside #offloadService will return the tested strategy.
                new ExecutionContextToHttpExecutionContext(contextRule, noOffloadsStrategy()));
        analyzer.instrumentedResponseForServer(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .toFuture().get();
        analyzer.verify();
    }

    @Test
    public void offloadSendSingle() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, never())).subscribe(__ -> { }).cancel();
        analyzer.awaitCancel.await();
        analyzer.verifySend();
    }

    @Test
    public void offloadSendPublisher() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentSend(strategy.offloadSend(executor, from(1))).toFuture().get();
        analyzer.verifySend();
    }

    @Test
    public void offloadReceiveSingle() throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        analyzer.instrumentReceive(strategy.offloadReceive(executor, succeeded(1))).toFuture().get();
        analyzer.verifyReceive();
    }

    @Test
    public void offloadReceivePublisher() throws Exception {
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
            assertThat("Unexpected errors found: " + errors, errors, hasSize(0));
        }

        private void addError(final String msg) {
            errors.add(new AssertionError(msg + " Thread: " + currentThread()));
        }
    }
}
