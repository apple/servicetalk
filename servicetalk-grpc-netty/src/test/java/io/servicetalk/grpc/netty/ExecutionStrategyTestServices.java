/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * Each service method responds with {@link TestResponse} that encodes:
 * {@code <handle-thread-name>#<handle-executor-name>#<request-thread-name>#<response-thread-name>}.
 * <p>
 * Note: {@code <request-thread-name>/<response-thread-name>} is not {@code null} only when request/response is
 * asynchronous.
 * <p>
 * Use {@link ThreadInfo#parse(TestResponse)} to parse {@link TestResponse}.
 */
public final class ExecutionStrategyTestServices {

    static final String NULL = "null";

    static final TesterService DEFAULT_STRATEGY_ASYNC_SERVICE = new DefaultStrategyAsyncService();
    static final TesterService CLASS_EXEC_ID_STRATEGY_ASYNC_SERVICE = new ClassExecIdStrategyAsyncService();
    static final TesterService CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE = new ClassNoOffloadsStrategyAsyncService();
    static final TesterService METHOD_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE = new MethodNoOffloadsStrategyAsyncService();

    static final BlockingTesterService DEFAULT_STRATEGY_BLOCKING_SERVICE = new DefaultStrategyBlockingService();
    static final BlockingTesterService CLASS_EXEC_ID_STRATEGY_BLOCKING_SERVICE =
            new ClassExecIdStrategyBlockingService();
    static final BlockingTesterService CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE =
            new ClassNoOffloadsStrategyBlockingService();
    static final BlockingTesterService METHOD_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE =
            new MethodNoOffloadsStrategyBlockingService();

    private ExecutionStrategyTestServices() {
        // No instances
    }

    private static String threadName() {
        return Thread.currentThread().getName();
    }

    static final class ThreadInfo {
        final String handleExecutorName;
        final String handleThreadName;
        String requestOnSubscribeThreadName = NULL;
        String requestOnNextThreadName = NULL;
        String responseOnSubscribeThreadName = NULL;
        String responseOnNextThreadName = NULL;

        ThreadInfo(final GrpcServiceContext ctx) {
            this.handleExecutorName = ctx.executionContext().executor().toString();
            this.handleThreadName = threadName();
        }

        ThreadInfo(final String handleExecutorName, final String handleThreadName,
                   final String requestOnSubscribeThreadName, final String requestOnNextThreadName,
                   final String responseOnSubscribeThreadName, final String responseOnNextThreadName) {
            this.handleExecutorName = handleExecutorName;
            this.handleThreadName = handleThreadName;
            this.requestOnSubscribeThreadName = requestOnSubscribeThreadName;
            this.requestOnNextThreadName = requestOnNextThreadName;
            this.responseOnSubscribeThreadName = responseOnSubscribeThreadName;
            this.responseOnNextThreadName = responseOnNextThreadName;
        }

        TestResponse encode() {
            return TestResponse.newBuilder().setMessage(handleExecutorName + '#' + handleThreadName + '#' +
                    requestOnSubscribeThreadName + '#' + requestOnNextThreadName + '#' +
                    responseOnSubscribeThreadName + '#' + responseOnNextThreadName).build();
        }

        static ThreadInfo parse(final TestResponse response) {
            final String[] components = response.getMessage().split("#");
            assert components.length == 6;
            return new ThreadInfo(components[0], components[1], components[2], components[3], components[4],
                    components[5]);
        }
    }

    /// Async API:

    private static class EsAsyncService implements TesterService {

        @Override
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            final ThreadInfo threadInfo = new ThreadInfo(ctx);
            return succeeded(threadInfo)
                    .afterOnSubscribe(__ -> threadInfo.responseOnSubscribeThreadName = threadName())
                    .map(EsAsyncService::captureOnNextThreadAndMap);
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            final ThreadInfo threadInfo = new ThreadInfo(ctx);
            return request.whenOnSubscribe(__ -> threadInfo.requestOnSubscribeThreadName = threadName())
                    .whenOnNext(__ -> threadInfo.requestOnNextThreadName = threadName())
                    .ignoreElements().concat(succeeded(threadInfo).toPublisher())
                    .whenOnSubscribe(__ -> threadInfo.responseOnSubscribeThreadName = threadName())
                    .map(EsAsyncService::captureOnNextThreadAndMap);
        }

        @Override
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
            final ThreadInfo threadInfo = new ThreadInfo(ctx);
            return succeeded(threadInfo).toPublisher()
                    .whenOnSubscribe(__ -> threadInfo.responseOnSubscribeThreadName = threadName())
                    .map(EsAsyncService::captureOnNextThreadAndMap);
        }

        @Override
        public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            final ThreadInfo threadInfo = new ThreadInfo(ctx);
            return request.whenOnSubscribe(__ -> threadInfo.requestOnSubscribeThreadName = threadName())
                    .whenOnNext(__ -> threadInfo.requestOnNextThreadName = threadName())
                    .ignoreElements().concat(succeeded(threadInfo))
                    .afterOnSubscribe(__ -> threadInfo.responseOnSubscribeThreadName = threadName())
                    .map(EsAsyncService::captureOnNextThreadAndMap);
        }

        private static TestResponse captureOnNextThreadAndMap(final ThreadInfo threadInfo) {
            threadInfo.responseOnNextThreadName = threadName();
            return threadInfo.encode();
        }
    }

    private static final class DefaultStrategyAsyncService extends EsAsyncService {
        // Nothing to override
    }

    @RouteExecutionStrategy(id = "route")
    private static class ClassExecIdStrategyAsyncService extends EsAsyncService {
        // Nothing to override
    }

    @NoOffloadsRouteExecutionStrategy
    private static class ClassNoOffloadsStrategyAsyncService extends EsAsyncService {
        // Nothing to override
    }

    private static final class MethodNoOffloadsStrategyAsyncService extends ClassExecIdStrategyAsyncService {
        @Override
        @NoOffloadsRouteExecutionStrategy
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            return super.test(ctx, request);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return super.testBiDiStream(ctx, request);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
            return super.testResponseStream(ctx, request);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return super.testRequestStream(ctx, request);
        }
    }

    /// Blocking API:

    private static class EsBlockingService implements BlockingTesterService {

        @Override
        public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) {
            return new ThreadInfo(ctx).encode();
        }

        @Override
        public void testBiDiStream(final GrpcServiceContext ctx, final BlockingIterable<TestRequest> request,
                                   final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            request.forEach(__ -> { /* ignore */ });
            responseWriter.write(new ThreadInfo(ctx).encode());
            responseWriter.close();
        }

        @Override
        public void testResponseStream(final GrpcServiceContext ctx, final TestRequest request,
                                       final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            responseWriter.write(new ThreadInfo(ctx).encode());
            responseWriter.close();
        }

        @Override
        public TestResponse testRequestStream(final GrpcServiceContext ctx,
                                              final BlockingIterable<TestRequest> request) throws Exception {
            request.forEach(__ -> { /* ignore */ });
            return new ThreadInfo(ctx).encode();
        }
    }

    private static final class DefaultStrategyBlockingService extends EsBlockingService {
        // Nothing to override
    }

    @RouteExecutionStrategy(id = "route")
    private static class ClassExecIdStrategyBlockingService extends EsBlockingService {
        // Nothing to override
    }

    @NoOffloadsRouteExecutionStrategy
    private static class ClassNoOffloadsStrategyBlockingService extends EsBlockingService {
        // Nothing to override
    }

    private static final class MethodNoOffloadsStrategyBlockingService extends ClassExecIdStrategyBlockingService {
        @Override
        @NoOffloadsRouteExecutionStrategy
        public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) {
            return super.test(ctx, request);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public void testBiDiStream(
                final GrpcServiceContext ctx, final BlockingIterable<TestRequest> request,
                final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            super.testBiDiStream(ctx, request, responseWriter);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public void testResponseStream(
                final GrpcServiceContext ctx, final TestRequest request,
                final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            super.testResponseStream(ctx, request, responseWriter);
        }

        @Override
        @NoOffloadsRouteExecutionStrategy
        public TestResponse testRequestStream(
                final GrpcServiceContext ctx, final BlockingIterable<TestRequest> request) throws Exception {
            return super.testRequestStream(ctx, request);
        }
    }
}
