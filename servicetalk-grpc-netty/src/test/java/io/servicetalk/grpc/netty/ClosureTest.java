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

import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTestRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ClosureTest {

    private boolean closeGracefully;

    private void setUp(final boolean closeGracefully) {
        this.closeGracefully = closeGracefully;
    }

    @ParameterizedTest(name = "graceful? => {0}")
    @ValueSource(booleans = {true, false})
    void serviceImplIsClosed(final boolean param) throws Exception {
        setUp(param);
        CloseSignal signal = new CloseSignal(1);
        TesterService svc = setupCloseMock(mock(TesterService.class), signal);
        startServerAndClose(new ServiceFactory(svc), signal);
        verifyClosure(svc, 4 /* 4 rpc methods */);
        signal.verifyCloseAtLeastCount(closeGracefully);
    }

    @ParameterizedTest(name = "graceful? => {0}")
    @ValueSource(booleans = {true, false})
    void blockingServiceImplIsClosed(final boolean param) throws Exception {
        setUp(param);
        CloseSignal signal = new CloseSignal(1);
        BlockingTesterService svc = setupBlockingCloseMock(mock(BlockingTesterService.class), signal);
        startServerAndClose(new ServiceFactory(svc), signal);
        verifyClosure(svc, 4 /* 4 rpc methods */);
        signal.verifyCloseAtLeastCount(closeGracefully);
    }

    @ParameterizedTest(name = "graceful? => {0}")
    @ValueSource(booleans = {true, false})
    void rpcMethodsAreClosed(final boolean param) throws Exception {
        setUp(param);
        CloseSignal signal = new CloseSignal(4);
        TestRpc testRpc = setupCloseMock(mock(TestRpc.class), signal);
        TestRequestStreamRpc testRequestStreamRpc = setupCloseMock(mock(TestRequestStreamRpc.class), signal);
        TestResponseStreamRpc testResponseStreamRpc = setupCloseMock(mock(TestResponseStreamRpc.class), signal);
        TestBiDiStreamRpc testBiDiStreamRpc = setupCloseMock(mock(TestBiDiStreamRpc.class), signal);
        startServerAndClose(new ServiceFactory.Builder()
                .test(testRpc)
                .testRequestStream(testRequestStreamRpc)
                .testResponseStream(testResponseStreamRpc)
                .testBiDiStream(testBiDiStreamRpc)
                .build(), signal);
        verifyClosure(testRpc);
        verifyClosure(testRequestStreamRpc);
        verifyClosure(testResponseStreamRpc);
        verifyClosure(testBiDiStreamRpc);
        signal.verifyClose(closeGracefully);
    }

    @ParameterizedTest(name = "graceful? => {0}")
    @ValueSource(booleans = {true, false})
    void blockingRpcMethodsAreClosed(final boolean param) throws Exception {
        setUp(param);
        CloseSignal signal = new CloseSignal(4);
        BlockingTestRpc testRpc = setupBlockingCloseMock(mock(BlockingTestRpc.class), signal);
        BlockingTestRequestStreamRpc testRequestStreamRpc =
                setupBlockingCloseMock(mock(BlockingTestRequestStreamRpc.class), signal);
        BlockingTestResponseStreamRpc testResponseStreamRpc =
                setupBlockingCloseMock(mock(BlockingTestResponseStreamRpc.class), signal);
        BlockingTestBiDiStreamRpc testBiDiStreamRpc =
                setupBlockingCloseMock(mock(BlockingTestBiDiStreamRpc.class), signal);
        startServerAndClose(new ServiceFactory.Builder()
                .testBlocking(testRpc)
                .testRequestStreamBlocking(testRequestStreamRpc)
                .testResponseStreamBlocking(testResponseStreamRpc)
                .testBiDiStreamBlocking(testBiDiStreamRpc)
                .build(), signal);
        verifyClosure(testRpc);
        verifyClosure(testRequestStreamRpc);
        verifyClosure(testResponseStreamRpc);
        verifyClosure(testBiDiStreamRpc);
        signal.verifyClose(closeGracefully);
    }

    @ParameterizedTest(name = "graceful? => {0}")
    @ValueSource(booleans = {true, false})
    void mixedModeRpcMethodsAreClosed(final boolean param) throws Exception {
        setUp(param);
        CloseSignal signal = new CloseSignal(4);
        TestRpc testRpc = setupCloseMock(mock(TestRpc.class), signal);
        TestRequestStreamRpc testRequestStreamRpc = setupCloseMock(mock(TestRequestStreamRpc.class), signal);
        BlockingTestResponseStreamRpc testResponseStreamRpc =
                setupBlockingCloseMock(mock(BlockingTestResponseStreamRpc.class), signal);
        BlockingTestBiDiStreamRpc testBiDiStreamRpc =
                setupBlockingCloseMock(mock(BlockingTestBiDiStreamRpc.class), signal);
        startServerAndClose(new ServiceFactory.Builder()
                .test(testRpc)
                .testRequestStream(testRequestStreamRpc)
                .testResponseStreamBlocking(testResponseStreamRpc)
                .testBiDiStreamBlocking(testBiDiStreamRpc)
                .build(), signal);
        verifyClosure(testRpc);
        verifyClosure(testRequestStreamRpc);
        verifyClosure(testResponseStreamRpc);
        verifyClosure(testBiDiStreamRpc);
        signal.verifyClose(closeGracefully);
    }

    private <T extends AsyncCloseable> T setupCloseMock(final T autoCloseable, final AsyncCloseable closeSignal) {
        when(autoCloseable.closeAsync()).thenReturn(closeSignal.closeAsync());
        when(autoCloseable.closeAsyncGracefully()).thenReturn(closeSignal.closeAsyncGracefully());
        return autoCloseable;
    }

    private <T extends GracefulAutoCloseable> T setupBlockingCloseMock(final T autoCloseable,
                                                                       final AsyncCloseable closeSignal)
            throws Exception {
        doAnswer(__ -> closeSignal.closeAsync().toFuture().get()).when(autoCloseable).close();
        doAnswer(__ -> closeSignal.closeAsyncGracefully().toFuture().get()).when(autoCloseable).closeGracefully();
        return autoCloseable;
    }

    private void verifyClosure(AsyncCloseable closeable) {
        verifyClosure(closeable, 1);
    }

    private void verifyClosure(AsyncCloseable closeable, int times) {
        // Async mode both methods are called but one is subscribed.
        verify(closeable, times(times)).closeAsyncGracefully();
        verify(closeable, times(times)).closeAsync();
        verifyNoMoreInteractions(closeable);
    }

    private void verifyClosure(GracefulAutoCloseable closeable) throws Exception {
        verifyClosure(closeable, 1);
    }

    private void verifyClosure(GracefulAutoCloseable closeable, int times) throws Exception {
        if (closeGracefully) {
            verify(closeable, times(times)).closeGracefully();
        } else {
            verify(closeable, times(times)).close();
        }
        verifyNoMoreInteractions(closeable);
    }

    private void startServerAndClose(final ServiceFactory serviceFactory, final CloseSignal signal) throws Exception {
        ServerContext serverContext = forAddress(localAddress(0))
                .listenAndAwait(serviceFactory);

        if (closeGracefully) {
            serverContext.closeGracefully();
        } else {
            serverContext.close();
        }

        signal.await();
    }

    private static final class CloseSignal implements AsyncCloseable {
        private final CountDownLatch latch;
        private final int count;
        private final AtomicInteger closeCount;
        private final AtomicInteger gracefulCloseCount;
        private final Completable close;
        private final Completable closeGraceful;

        CloseSignal(final int count) {
            latch = new CountDownLatch(count);
            this.count = count;
            closeCount = new AtomicInteger();
            gracefulCloseCount = new AtomicInteger();
            close = defer(() -> {
                closeCount.incrementAndGet();
                latch.countDown();
                return completed();
            });
            closeGraceful = defer(() -> {
                gracefulCloseCount.incrementAndGet();
                latch.countDown();
                return completed();
            });
        }

        void await() throws Exception {
            latch.await();
        }

        void verifyClose(boolean graceful) {
            assertThat("Unexpected graceful closures.", gracefulCloseCount.get(),
                    equalTo(graceful ? count : 0));
            assertThat("Unexpected closures.", closeCount.get(), equalTo(graceful ? 0 : count));
        }

        void verifyCloseAtLeastCount(boolean graceful) {
            if (graceful) {
                assertThat("Unexpected graceful closures.", gracefulCloseCount.get(),
                        greaterThanOrEqualTo(count));
                assertThat("Unexpected closures.", closeCount.get(), equalTo(0));
            } else {
                assertThat("Unexpected graceful closures.", gracefulCloseCount.get(),
                        equalTo(0));
                assertThat("Unexpected closures.", closeCount.get(), greaterThanOrEqualTo(count));
            }
        }

        @Override
        public Completable closeAsync() {
            return close;
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeGraceful;
        }
    }
}
