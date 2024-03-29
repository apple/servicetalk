/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcExchangeObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcRequestObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcResponseObserver;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.grpc.api.GrpcStatusCode.OK;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.grpc.utils.GrpcLifecycleObservers.combine;
import static io.servicetalk.grpc.utils.GrpcLifecycleObservers.logging;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcLifecycleObserverTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String CONTENT = "content";
    private static final GrpcLifecycleObserver LOGGING = logging("servicetalk-tests-lifecycle-observer-logger", TRACE);

    // To avoid flaky behavior await for both exchanges to terminate before starting verification:
    private final CountDownLatch bothTerminate = new CountDownLatch(2);

    @Mock
    private GrpcLifecycleObserver clientLifecycleObserver;
    @Mock
    private GrpcExchangeObserver clientExchangeObserver;
    @Mock
    private GrpcRequestObserver clientRequestObserver;
    @Mock
    private GrpcResponseObserver clientResponseObserver;
    private InOrder clientInOrder;
    private InOrder clientRequestInOrder;

    @Mock
    private GrpcLifecycleObserver serverLifecycleObserver;
    @Mock
    private GrpcExchangeObserver serverExchangeObserver;
    @Mock
    private GrpcRequestObserver serverRequestObserver;
    @Mock
    private GrpcResponseObserver serverResponseObserver;
    private InOrder serverInOrder;
    private InOrder serverRequestInOrder;

    @Nullable
    private BlockingTesterClient client;
    @Nullable
    private ServerContext server;

    private void setUp(boolean error) throws Exception {
        when(clientLifecycleObserver.onNewExchange()).thenReturn(clientExchangeObserver);
        when(clientExchangeObserver.onRequest(any())).thenReturn(clientRequestObserver);
        when(clientExchangeObserver.onResponse(any())).thenReturn(clientResponseObserver);
        doAnswer(__ -> {
            bothTerminate.countDown();
            return null;
        }).when(clientExchangeObserver).onExchangeFinally();
        clientInOrder = inOrder(clientLifecycleObserver, clientExchangeObserver, clientResponseObserver);
        clientRequestInOrder = inOrder(clientRequestObserver);

        when(serverLifecycleObserver.onNewExchange()).thenReturn(serverExchangeObserver);
        when(serverExchangeObserver.onRequest(any())).thenReturn(serverRequestObserver);
        when(serverExchangeObserver.onResponse(any())).thenReturn(serverResponseObserver);
        doAnswer(__ -> {
            bothTerminate.countDown();
            return null;
        }).when(serverExchangeObserver).onExchangeFinally();
        serverInOrder = inOrder(serverLifecycleObserver, serverExchangeObserver, serverResponseObserver);
        serverRequestInOrder = inOrder(serverRequestObserver);

        server = forAddress(localAddress(0))
                .initializeHttp(builder -> builder
                        .ioExecutor(SERVER_CTX.ioExecutor())
                        .executor(SERVER_CTX.executor())
                        .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                        .protocols(h2().enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true)
                                .build())
                )
                .lifecycleObserver(combine(serverLifecycleObserver, LOGGING))
                .listenAndAwait(new EchoService(error));

        client = GrpcClients.forAddress(serverHostAndPort(server))
                .initializeHttp(builder ->
                    builder.ioExecutor(CLIENT_CTX.ioExecutor())
                            .executor(CLIENT_CTX.executor())
                            .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                            .protocols(h2().enableFrameLogging("servicetalk-tests-h2-frame-logger",
                                    TRACE, () -> true).build())
                            .appendClientFilter(new GrpcLifecycleObserverRequesterFilter(
                                    combine(clientLifecycleObserver, LOGGING))))
                .buildBlocking(new ClientFactory());
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
                client = null;
            }
        } finally {
            if (server != null) {
                server.close();
                server = null;
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void test(boolean error) throws Exception {
        runTest(() -> client.test(newRequest()).getMessage(), error, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testRequestStream(boolean error) throws Exception {
        runTest(() -> client.testRequestStream(singletonList(newRequest())).getMessage(), error, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testResponseStream(boolean error) throws Exception {
        runTest(() -> client.testResponseStream(newRequest()).iterator().next().getMessage(), error, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testBiDiStream(boolean error) throws Exception {
        runTest(() -> client.testBiDiStream(singletonList(newRequest())).iterator().next().getMessage(), error, false);
    }

    private void runTest(Callable<String> executeRequest, boolean error, boolean aggregated) throws Exception {
        setUp(error);

        if (error) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, executeRequest::call);
            assertThat(e.status().code(), is(UNKNOWN));
        } else {
            assertThat(executeRequest.call(), equalTo(CONTENT));
        }

        // Await full termination to make sure no more callbacks will be invoked.
        bothTerminate.await();
        tearDown();

        verifyObservers(true, error, aggregated, clientLifecycleObserver, clientExchangeObserver,
                clientRequestObserver, clientResponseObserver, clientInOrder, clientRequestInOrder);
        verifyObservers(false, error, aggregated, serverLifecycleObserver, serverExchangeObserver,
                serverRequestObserver, serverResponseObserver, serverInOrder, serverRequestInOrder);
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName(CONTENT).build();
    }

    private static void verifyObservers(boolean client, boolean error, boolean aggregated,
                                        GrpcLifecycleObserver lifecycle, GrpcExchangeObserver exchange,
                                        GrpcRequestObserver request, GrpcResponseObserver response,
                                        InOrder inOrder, InOrder requestInOrder) {
        inOrder.verify(lifecycle).onNewExchange();
        if (client) {
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
        } else {
            inOrder.verify(exchange).onConnectionSelected(any(ConnectionInfo.class));
            inOrder.verify(exchange).onRequest(any(StreamingHttpRequest.class));
        }
        inOrder.verify(exchange).onResponse(any(StreamingHttpResponse.class));
        verify(response, atLeastOnce()).onResponseDataRequested(anyLong());
        if (!error) {
            inOrder.verify(response).onResponseData(any(Buffer.class));
            inOrder.verify(response).onResponseTrailers(any(HttpHeaders.class));
            verifyGrpcStatus(inOrder, response, OK);
            inOrder.verify(response).onResponseComplete();
        } else {
            if (!aggregated) {
                inOrder.verify(response).onResponseTrailers(any(HttpHeaders.class));
            }
            verifyGrpcStatus(inOrder, response, UNKNOWN);
            if (client) {
                if (aggregated) {
                    inOrder.verify(response, never()).onResponseTrailers(any(HttpHeaders.class));
                    inOrder.verify(response).onResponseComplete();
                } else {
                    inOrder.verify(response).onResponseError(any(GrpcStatusException.class));
                }
            } else {
                inOrder.verify(response).onResponseComplete();
            }
        }

        verify(request, atLeastOnce()).onRequestDataRequested(anyLong());
        requestInOrder.verify(request).onRequestData(any(Buffer.class));
        requestInOrder.verify(request, never()).onRequestTrailers(any(HttpHeaders.class));
        requestInOrder.verify(request).onRequestComplete();

        inOrder.verify(exchange).onExchangeFinally();
        verifyNoMoreInteractions(request, response, exchange, lifecycle);
    }

    private static void verifyGrpcStatus(InOrder inOrder, GrpcResponseObserver response, GrpcStatusCode expectedCode) {
        ArgumentCaptor<GrpcStatus> grpcStatus = ArgumentCaptor.forClass(GrpcStatus.class);
        inOrder.verify(response).onGrpcStatus(grpcStatus.capture());
        assertThat("Unexpected gRPC status", grpcStatus.getValue().code(), is(expectedCode));
    }

    private static final class EchoService implements TesterService {

        private final boolean error;

        EchoService(boolean error) {
            this.error = error;
        }

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            if (error) {
                return Single.failed(DELIBERATE_EXCEPTION);
            }
            return succeeded(TestResponse.newBuilder().setMessage(request.getName()).build());
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            if (error) {
                return request.ignoreElements().concat(Single.failed(DELIBERATE_EXCEPTION));
            }
            return request.collect(StringBuilder::new, (sb, testRequest) -> sb.append(testRequest.getName()))
                    .map(sb -> TestResponse.newBuilder().setMessage(sb.toString()).build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            if (error) {
                return Publisher.failed(DELIBERATE_EXCEPTION);
            }
            return from(TestResponse.newBuilder().setMessage(request.getName()).build());
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            if (error) {
                return request.ignoreElements().concat(Publisher.failed(DELIBERATE_EXCEPTION));
            }
            return request.map(testRequest -> TestResponse.newBuilder().setMessage(testRequest.getName()).build());
        }
    }
}
