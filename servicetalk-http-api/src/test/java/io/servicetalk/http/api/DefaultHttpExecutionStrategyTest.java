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
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class DefaultHttpExecutionStrategyTest {

    @RegisterExtension
    static final ExecutionContextExtension contextRule =
            new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR,
                    () -> createIoExecutor("st-ioexecutor"),
                    () -> newCachedThreadExecutor(new DefaultThreadFactory("st-executor")))
            .setClassLevel(true);

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void wrapServiceThatWasAlreadyOffloaded(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        StreamingHttpService svc = StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(
                strategy, contextRule.executor(), Boolean.TRUE::booleanValue, (ctx, request, responseFactory) -> {
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
                        new ExecutionContextToHttpExecutionContext(contextRule, strategy));
        analyzer.instrumentedResponseForServerNotOffloaded(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                .flatMapPublisher(StreamingHttpResponse::payloadBody)
                .toFuture().get();
        analyzer.verify();
    }

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void wrapServiceThatWasNotOffloaded(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        StreamingHttpService svc = StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(
                strategy, contextRule.executor(), Boolean.TRUE::booleanValue, (ctx, request, responseFactory) -> {
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
                        // Use offloadNone() for the ctx to indicate that there was no offloading before.
                        // So, the difference function inside #offloadService will return the tested strategy.
                        new ExecutionContextToHttpExecutionContext(contextRule, offloadNone()));
        Callable<?> runHandle = () ->
                analyzer.instrumentedResponseForServer(svc.handle(ctx, req, ctx.streamingResponseFactory()))
                    .flatMapPublisher(StreamingHttpResponse::payloadBody)
                    .toFuture().get();
        if (strategy.isRequestResponseOffloaded()) {
            NettyIoExecutor ioExecutor = (NettyIoExecutor) contextRule.ioExecutor();
            ioExecutor.submit(runHandle).toFuture().get();
        } else {
            runHandle.call();
        }
        analyzer.verify();
    }

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadSendSingle(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        int result = analyzer.instrumentSend(strategy.isSendOffloaded() ?
                succeeded(1).subscribeOn(contextRule.executor()) : succeeded(1)).toFuture().get();
        analyzer.verifySend();
        assertThat("Unexpected result", result, is(1));
    }

    @Disabled("https://github.com/apple/servicetalk/issues/1716")
    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadSendSingleCancel(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        analyzer.instrumentSendCancel(strategy.isSendOffloaded() ?
                never().subscribeOn(contextRule.executor()) : never()).subscribe(__ -> {
        }).cancel();
        analyzer.awaitCancel.await();
        analyzer.verifySend();
    }

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadSendPublisher(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        Collection<Integer> result = analyzer.instrumentSend(strategy.isSendOffloaded() ?
                        from(1).subscribeOn(contextRule.executor()) : from(1))
                .toFuture().get();
        assertThat("Unexpected Result", result, contains(1));
        analyzer.verifySend();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/1716")
    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadSendPublisherCancel(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        analyzer.instrumentSendCancel(
                strategy.isSendOffloaded() ? Publisher.never().subscribeOn(contextRule.executor()) : Publisher.never())
                .toFuture()
                .cancel(false);
        analyzer.awaitCancel.await();
        analyzer.verifySend();
    }

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadReceiveSingle(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        analyzer.instrumentReceive(strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
                succeeded(1).publishOn(contextRule.executor()) : succeeded(1)).toFuture().get();
        analyzer.verifyReceive();
    }

    @ParameterizedTest
    @EnumSource(DefaultHttpExecutionStrategy.class)
    void offloadReceivePublisher(final HttpExecutionStrategy strategy) throws Exception {
        ThreadAnalyzer analyzer = new ThreadAnalyzer(strategy);
        analyzer.instrumentReceive(strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
                from(1).publishOn(contextRule.executor()) : from(1)).toFuture().get();
        analyzer.verifyReceive();
    }

    private static final class ThreadAnalyzer {

        private static final int SEND_ANALYZED_INDEX = 0;
        private static final int RECEIVE_META_ANALYZED_INDEX = 1;
        private static final int RECEIVE_DATA_ANALYZED_INDEX = 2;
        private final ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        private final Thread testThread = currentThread();
        private final AtomicReferenceArray<Boolean> analyzed = new AtomicReferenceArray<>(3);
        private final CountDownLatch awaitCancel = new CountDownLatch(1);
        private final HttpExecutionStrategy strategy;

        ThreadAnalyzer(HttpExecutionStrategy strategy) {
            this.strategy = strategy;
        }

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
                verifyThread(strategy.isMetadataReceiveOffloaded(), "Unexpected thread for response payload onNext.");
            }))).beforeOnSuccess(__ -> {
                analyzed.set(RECEIVE_META_ANALYZED_INDEX, true);
                verifyThread(strategy.isMetadataReceiveOffloaded(), "Unexpected thread for response single.");
            });
        }

        Single<StreamingHttpResponse> instrumentedResponseForServer(Single<StreamingHttpResponse> resp) {
            return resp.map(response -> response.transformPayloadBody(p -> p.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from request.");
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
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from request.");
            });
        }

        <T> Single<T> instrumentSend(Single<T> original) {
            return original.beforeOnSuccess((T t) -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from success.");
            });
        }

        <T> Single<T> instrumentSendCancel(Single<T> original) {
            return original.beforeCancel(() -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from cancel.");
                awaitCancel.countDown();
            });
        }

        <T> Publisher<T> instrumentSend(Publisher<T> original) {
            return original.beforeRequest(__ -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from request.");
            });
        }

        <T> Publisher<T> instrumentSendCancel(Publisher<T> original) {
            return original.beforeCancel(() -> {
                analyzed.set(SEND_ANALYZED_INDEX, true);
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from cancel.");
                awaitCancel.countDown();
            });
        }

        <T> Single<T> instrumentReceive(Single<T> original) {
            return original.beforeOnSuccess(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(strategy.isDataReceiveOffloaded(), "Unexpected thread requested from success.");
            });
        }

        <T> Publisher<T> instrumentReceive(Publisher<T> original) {
            return original.beforeOnNext(__ -> {
                analyzed.set(RECEIVE_DATA_ANALYZED_INDEX, true);
                verifyThread(strategy.isDataReceiveOffloaded(), "Unexpected thread requested from next.");
            });
        }

        void checkContext(HttpServiceContext context) {
            if (strategy.hasOffloads()) {
                if (context.executionContext().executor() != contextRule.executor()) {
                    errors.add(new AssertionError("Unexpected executor in context. Expected: " +
                            contextRule.executor() + ", actual: " + context.executionContext().executor()));
                }
            } else {
                Executor expectedExecutor = contextRule.executor();
                if (expectedExecutor != context.executionContext().executor()) {
                    errors.add(new AssertionError("Unexpected executor in context. Expected: " +
                            expectedExecutor + ", actual: " + context.executionContext().executor()));
                }
            }
        }

        void checkServiceInvocation() {
            analyzed.set(RECEIVE_META_ANALYZED_INDEX, true);
            verifyThread(strategy.isMetadataReceiveOffloaded(), "Unexpected thread invoked service.");
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
                verifyThread(strategy.isDataReceiveOffloaded(), "Unexpected thread for request payload onNext.");
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
                verifyThread(strategy.isSendOffloaded(), "Unexpected thread requested from response.");
            });
        }

        void verifyThread(final boolean offloadedPath, final String errMsg) {
            if (offloadedPath && testThread == currentThread()) {
                addError(errMsg + " expected: " + testThread);
            }
        }

        private void addError(final String msg) {
            errors.add(new AssertionError(msg + " Thread: " + currentThread()));
        }

        void verifySend() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            assertNoAsyncErrors(errors);
        }

        void verifyReceive() {
            assertThat("Receive data path not analyzed.", analyzed.get(RECEIVE_DATA_ANALYZED_INDEX), is(true));
            assertNoAsyncErrors(errors);
        }

        void verify() {
            assertThat("Send path not analyzed.", analyzed.get(SEND_ANALYZED_INDEX), is(true));
            assertThat("Receive metadata path not analyzed.", analyzed.get(RECEIVE_META_ANALYZED_INDEX), is(true));
            assertThat("Receive data path not analyzed.", analyzed.get(RECEIVE_DATA_ANALYZED_INDEX), is(true));
            assertNoAsyncErrors(errors);
        }
    }
}
