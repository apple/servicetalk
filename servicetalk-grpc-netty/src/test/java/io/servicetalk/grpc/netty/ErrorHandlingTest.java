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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterServiceFilter;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import com.google.rpc.Status;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class ErrorHandlingTest {
    private static final StreamingHttpClientFilterFactory IDENTITY_CLIENT_FILTER =
            c -> new StreamingHttpClientFilter(c) { };
    private static final StreamingHttpServiceFilterFactory IDENTITY_FILTER =
            s -> new StreamingHttpServiceFilter(s) { };

    private final TestMode testMode;
    private final TestResponse cannedResponse;
    private final BlockingTesterClient blockingClient;

    private enum TestMode {
        HttpClientFilterThrows,
        HttpClientFilterThrowsGrpcException,
        HttpClientFilterEmitsError,
        HttpClientFilterEmitsGrpcException,
        HttpFilterThrows,
        HttpFilterThrowsGrpcException,
        HttpFilterEmitsError,
        HttpFilterEmitsGrpcException,
        FilterThrows,
        FilterThrowsGrpcException,
        FilterEmitsError,
        FilterEmitsGrpcException,
        ServiceThrows,
        ServiceThrowsGrpcException,
        ServiceOperatorThrows,
        ServiceOperatorThrowsGrpcException,
        ServiceEmitsError,
        ServiceEmitsGrpcException,
        ServiceEmitsDataThenError,
        ServiceEmitsDataThenGrpcException,
        BlockingServiceThrows,
        BlockingServiceThrowsGrpcException,
        BlockingServiceWritesThenThrows,
        BlockingServiceWritesThenThrowsGrpcException
    }

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final GrpcStatusException cannedException =
            GrpcStatusException.of(Status.newBuilder().setCode(GrpcStatusCode.ABORTED.value())
                    .setMessage("Deliberate abort").build());
    private final ServerContext serverContext;
    private final TesterClient client;

    public ErrorHandlingTest(TestMode testMode) throws Exception {
        this.testMode = testMode;
        cannedResponse = TestResponse.newBuilder().setMessage("foo").build();
        ServiceFactory serviceFactory;
        TesterService filter = mockTesterService();
        StreamingHttpServiceFilterFactory serviceFilterFactory = IDENTITY_FILTER;
        StreamingHttpClientFilterFactory clientFilterFactory = IDENTITY_CLIENT_FILTER;
        switch (testMode) {
            case HttpClientFilterThrows:
                clientFilterFactory = new ErrorProducingClientFilter(true, DELIBERATE_EXCEPTION);
                serviceFactory = setupForSuccess();
                break;
            case HttpClientFilterThrowsGrpcException:
                clientFilterFactory = new ErrorProducingClientFilter(true, cannedException);
                serviceFactory = setupForSuccess();
                break;
            case HttpClientFilterEmitsError:
                clientFilterFactory = new ErrorProducingClientFilter(false, DELIBERATE_EXCEPTION);
                serviceFactory = setupForSuccess();
                break;
            case HttpClientFilterEmitsGrpcException:
                clientFilterFactory = new ErrorProducingClientFilter(false, cannedException);
                serviceFactory = setupForSuccess();
                break;
            case HttpFilterThrows:
                serviceFilterFactory = new ErrorProducingSvcFilter(true, DELIBERATE_EXCEPTION);
                serviceFactory = setupForSuccess();
                break;
            case HttpFilterThrowsGrpcException:
                serviceFilterFactory = new ErrorProducingSvcFilter(true, cannedException);
                serviceFactory = setupForSuccess();
                break;
            case HttpFilterEmitsError:
                serviceFilterFactory = new ErrorProducingSvcFilter(false, DELIBERATE_EXCEPTION);
                serviceFactory = setupForSuccess();
                break;
            case HttpFilterEmitsGrpcException:
                serviceFilterFactory = new ErrorProducingSvcFilter(false, cannedException);
                serviceFactory = setupForSuccess();
                break;
            case FilterThrows:
                setupForServiceThrows(filter, DELIBERATE_EXCEPTION);
                serviceFactory = configureFilter(filter);
                break;
            case FilterThrowsGrpcException:
                setupForServiceThrows(filter, cannedException);
                serviceFactory = configureFilter(filter);
                break;
            case FilterEmitsError:
                setupForServiceEmitsError(filter, DELIBERATE_EXCEPTION);
                serviceFactory = configureFilter(filter);
                break;
            case FilterEmitsGrpcException:
                setupForServiceEmitsError(filter, cannedException);
                serviceFactory = configureFilter(filter);
                break;
            case ServiceThrows:
                serviceFactory = setupForServiceThrows(DELIBERATE_EXCEPTION);
                break;
            case ServiceThrowsGrpcException:
                serviceFactory = setupForServiceThrows(cannedException);
                break;
            case ServiceOperatorThrows:
                serviceFactory = setupForServiceOperatorThrows(DELIBERATE_EXCEPTION);
                break;
            case ServiceOperatorThrowsGrpcException:
                serviceFactory = setupForServiceOperatorThrows(cannedException);
                break;
            case ServiceEmitsError:
                serviceFactory = setupForServiceEmitsError(DELIBERATE_EXCEPTION);
                break;
            case ServiceEmitsGrpcException:
                serviceFactory = setupForServiceEmitsError(cannedException);
                break;
            case ServiceEmitsDataThenError:
                serviceFactory = setupForServiceEmitsDataThenError(DELIBERATE_EXCEPTION);
                break;
            case ServiceEmitsDataThenGrpcException:
                serviceFactory = setupForServiceEmitsDataThenError(cannedException);
                break;
            case BlockingServiceThrows:
                serviceFactory = setupForBlockingServiceThrows(DELIBERATE_EXCEPTION);
                break;
            case BlockingServiceThrowsGrpcException:
                serviceFactory = setupForBlockingServiceThrows(cannedException);
                break;
            case BlockingServiceWritesThenThrows:
                serviceFactory = setupForBlockingServiceWritesThenThrows(DELIBERATE_EXCEPTION);
                break;
            case BlockingServiceWritesThenThrowsGrpcException:
                serviceFactory = setupForBlockingServiceWritesThenThrows(cannedException);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + testMode);
        }
        serverContext = GrpcServers.forAddress(localAddress(0)).appendHttpServiceFilter(serviceFilterFactory)
                .listenAndAwait(serviceFactory);
        GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(serverContext)).appendHttpClientFilter(clientFilterFactory);
        client = clientBuilder.build(new ClientFactory());
        blockingClient = clientBuilder.buildBlocking(new ClientFactory());
    }

    private ServiceFactory configureFilter(final TesterService filter) {
        final ServiceFactory serviceFactory;
        final TesterService service = mockTesterService();
        serviceFactory = new ServiceFactory(service);
        serviceFactory.appendServiceFilter(original ->
                new ErrorSimulatingTesterServiceFilter(original, filter));
        return serviceFactory;
    }

    private ServiceFactory setupForSuccess() {
        return new ServiceFactory(new TesterService() {
            @Override
            public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                          final Publisher<TestRequest> request) {
                return request.map(testRequest -> TestResponse.newBuilder().setMessage(testRequest.getName()).build());
            }

            @Override
            public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                          final Publisher<TestRequest> request) {
                return request.collect(StringBuilder::new, (names, testRequest) -> names.append(testRequest.getName()))
                        .map(names -> TestResponse.newBuilder().setMessage(names.toString()).build());
            }

            @Override
            public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
                return from(TestResponse.newBuilder().setMessage(request.getName()).build());
            }

            @Override
            public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
                return Single.succeeded(TestResponse.newBuilder().setMessage(request.getName()).build());
            }
        });
    }

    private ServiceFactory setupForServiceThrows(final Throwable toThrow) {
        final TesterService service = mockTesterService();
        setupForServiceThrows(service, toThrow);
        return new ServiceFactory(service);
    }

    private ServiceFactory setupForServiceOperatorThrows(final Throwable toThrow) {
        final TesterService service = mockTesterService();
        setupForServiceOperatorThrows(service, toThrow);
        return new ServiceFactory(service);
    }

    private void setupForServiceThrows(final TesterService service, final Throwable toThrow) {
        when(service.test(any(), any())).thenThrow(toThrow);
        when(service.testBiDiStream(any(), any())).thenThrow(toThrow);
        when(service.testRequestStream(any(), any())).thenThrow(toThrow);
        when(service.testResponseStream(any(), any())).thenThrow(toThrow);
    }

    private void setupForServiceOperatorThrows(final TesterService service, final Throwable toThrow) {
        when(service.test(any(), any())).thenThrow(toThrow);
        doAnswer((Answer<Publisher<TestResponse>>) invocation -> {
            Publisher<TestRequest> request = invocation.getArgument(1);
            return request.map(req -> {
               throwException(toThrow);
               return null;
            });
        }).when(service).testBiDiStream(any(), any());
        doAnswer(invocation -> {
            Publisher<TestRequest> request = invocation.getArgument(1);
            return request.collect(ArrayList::new, (list, req) -> {
                throwException(toThrow);
                return null;
            });
        }).when(service).testRequestStream(any(), any());
        when(service.testResponseStream(any(), any())).thenThrow(toThrow);
    }

    private ServiceFactory setupForServiceEmitsError(final Throwable toThrow) {
        final TesterService service = mockTesterService();
        setupForServiceEmitsError(service, toThrow);
        return new ServiceFactory(service);
    }

    private void setupForServiceEmitsError(final TesterService service, final Throwable toThrow) {
        when(service.test(any(), any())).thenReturn(Single.failed(toThrow));
        when(service.testBiDiStream(any(), any())).thenReturn(Publisher.failed(toThrow));
        when(service.testRequestStream(any(), any())).thenReturn(Single.failed(toThrow));
        when(service.testResponseStream(any(), any())).thenReturn(Publisher.failed(toThrow));
    }

    private ServiceFactory setupForServiceEmitsDataThenError(final Throwable toThrow) {
        final TesterService service = mockTesterService();
        setupForServiceEmitsDataThenError(service, toThrow);
        return new ServiceFactory(service);
    }

    private void setupForServiceEmitsDataThenError(final TesterService service, final Throwable toThrow) {
        when(service.test(any(), any())).thenReturn(Single.failed(toThrow));
        when(service.testBiDiStream(any(), any()))
                .thenReturn(from(cannedResponse).concat(Publisher.failed(toThrow)));
        when(service.testRequestStream(any(), any())).thenReturn(Single.failed(toThrow));
        when(service.testResponseStream(any(), any()))
                .thenReturn(from(cannedResponse).concat(Publisher.failed(toThrow)));
    }

    private ServiceFactory setupForBlockingServiceThrows(final Throwable toThrow) throws Exception {
        final BlockingTesterService blockingService = mock(BlockingTesterService.class);
        setupForBlockingServiceThrows(blockingService, toThrow);
        return new ServiceFactory(blockingService);
    }

    private void setupForBlockingServiceThrows(final BlockingTesterService blockingService, final Throwable toThrow)
            throws Exception {
        when(blockingService.test(any(), any())).thenThrow(toThrow);
        doThrow(toThrow).when(blockingService).testBiDiStream(any(), any(), any());
        when(blockingService.testRequestStream(any(), any())).thenThrow(toThrow);
        doThrow(toThrow).when(blockingService).testResponseStream(any(), any(), any());
    }

    private ServiceFactory setupForBlockingServiceWritesThenThrows(final Throwable toThrow) throws Exception {
        final BlockingTesterService blockingService = mock(BlockingTesterService.class);
        setupForBlockingServiceWritesThenThrows(blockingService, toThrow);
        return new ServiceFactory(blockingService);
    }

    private void setupForBlockingServiceWritesThenThrows(final BlockingTesterService blockingService,
                                                         final Throwable toThrow) throws Exception {
        when(blockingService.test(any(), any())).thenThrow(toThrow);
        doAnswer(invocation -> {
            GrpcPayloadWriter<TestResponse> responseWriter = invocation.getArgument(2);
            responseWriter.write(cannedResponse);
            throw toThrow;
        }).when(blockingService).testBiDiStream(any(), any(), any());
        when(blockingService.testRequestStream(any(), any())).thenThrow(toThrow);
        doAnswer(invocation -> {
            GrpcPayloadWriter<TestResponse> responseWriter = invocation.getArgument(2);
            responseWriter.write(cannedResponse);
            throw toThrow;
        }).when(blockingService).testResponseStream(any(), any(), any());
    }

    @Parameterized.Parameters(name = "{index}: mode = {0}")
    public static Collection<TestMode> data() {
        return asList(TestMode.values());
    }

    @After
    public void tearDown() throws Exception {
        try {
            blockingClient.close();
        } finally {
            newCompositeCloseable().appendAll(client, serverContext).close();
        }
    }

    @Test
    public void scalar() throws Exception {
        verifyException(client.test(TestRequest.newBuilder().build()).toFuture());
    }

    @Test
    public void bidiStreaming() throws Exception {
        verifyStreamingResponse(client.testBiDiStream(from(TestRequest.newBuilder().build())));
    }

    @Test
    public void requestStreaming() {
        verifyException(client.testRequestStream(from(TestRequest.newBuilder().build())).toFuture());
    }

    @Test
    public void responseStreaming() throws Exception {
        verifyStreamingResponse(client.testResponseStream(TestRequest.newBuilder().build()));
    }

    @Test
    public void scalarFromBlockingClient() throws Exception {
        try {
            blockingClient.test(TestRequest.newBuilder().build());
            fail("Expected blocking scalar response to fail.");
        } catch (GrpcStatusException e) {
            assertThat("Unexpected grpc status.", e.status().code(), equalTo(expectedStatus()));
        }
    }

    @Test
    public void bidiStreamingFromBlockingClient() throws Exception {
        try {
            BlockingIterator<TestResponse> resp =
                    blockingClient.testBiDiStream(singletonList(TestRequest.newBuilder().setName("foo").build()))
                            .iterator();
            verifyStreamingResponse(resp);
        } catch (GrpcStatusException e) {
            assertThat("Unexpected grpc status.", e.status().code(), equalTo(expectedStatus()));
        }
    }

    @Test
    public void requestStreamingFromBlockingClient() throws Exception {
        try {
            blockingClient.testRequestStream(singletonList(TestRequest.newBuilder().setName("foo").build()));
            fail("Expected blocking request streaming to fail.");
        } catch (GrpcStatusException e) {
            assertThat("Unexpected grpc status.", e.status().code(), equalTo(expectedStatus()));
        }
    }

    @Test
    public void responseStreamingFromBlockingClient() throws Exception {
        try {
            BlockingIterator<TestResponse> resp =
                    blockingClient.testResponseStream(TestRequest.newBuilder().build()).iterator();
            verifyStreamingResponse(resp);
        } catch (GrpcStatusException e) {
            assertThat("Unexpected grpc status.", e.status().code(), equalTo(expectedStatus()));
        }
    }

    @Test
    public void bidiStreamingServerFailClientRequestNeverComplete() {
        // The response publisher is merged with the write publisher in order to provide status in the event of a write
        // failure. We must fail the read publisher internally at the appropriate time so the merge operator will
        // propagate the expected status (e.g. not wait for transport failure like stream reset or channel closed).
        verifyException(client.testBiDiStream(from(TestRequest.newBuilder().build()).concat(never())).toFuture());
    }

    @Test
    public void requestStreamingServerFailClientRequestNeverComplete() {
        verifyException(client.testRequestStream(from(TestRequest.newBuilder().build()).concat(never())).toFuture());
    }

    private TesterService mockTesterService() {
        TesterService filter = mock(TesterService.class);
        when(filter.closeAsync()).thenReturn(completed());
        when(filter.closeAsyncGracefully()).thenReturn(completed());
        return filter;
    }

    private void verifyStreamingResponse(final BlockingIterator<TestResponse> resp) {
        switch (testMode) {
            case ServiceEmitsDataThenError:
            case ServiceEmitsDataThenGrpcException:
            case BlockingServiceWritesThenThrows:
            case BlockingServiceWritesThenThrowsGrpcException:
                assertThat("Unexpected response.", resp.next(), equalTo(cannedResponse));
                resp.next(); // should throw
                fail("Expected streaming response to fail");
                break;
            case HttpClientFilterThrows:
            case HttpClientFilterThrowsGrpcException:
            case HttpClientFilterEmitsError:
            case HttpClientFilterEmitsGrpcException:
            case HttpFilterThrows:
            case HttpFilterThrowsGrpcException:
            case HttpFilterEmitsError:
            case HttpFilterEmitsGrpcException:
            case FilterThrows:
            case FilterThrowsGrpcException:
            case FilterEmitsError:
            case FilterEmitsGrpcException:
            case ServiceThrows:
            case ServiceThrowsGrpcException:
            case ServiceOperatorThrows:
            case ServiceOperatorThrowsGrpcException:
            case ServiceEmitsError:
            case ServiceEmitsGrpcException:
            case BlockingServiceThrows:
            case BlockingServiceThrowsGrpcException:
                resp.next(); // should throw
                fail("Expected streaming response to fail");
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + testMode);
        }
    }

    private void verifyStreamingResponse(final Publisher<TestResponse> resp) throws Exception {
        TestPublisherSubscriber<TestResponse> subscriber = new TestPublisherSubscriber<>();
        CountDownLatch terminationLatch = new CountDownLatch(1);
        toSource(resp.afterFinally(terminationLatch::countDown)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        terminationLatch.await();
        Throwable cause;
        switch (testMode) {
            case ServiceEmitsDataThenError:
            case ServiceEmitsDataThenGrpcException:
            case BlockingServiceWritesThenThrows:
            case BlockingServiceWritesThenThrowsGrpcException:
                List<TestResponse> items = subscriber.takeOnNext(1);
                assertThat("Unexpected response.", items, hasSize(1));
                assertThat("Unexpected response.", items, contains(cannedResponse));
                cause = subscriber.awaitOnError();
                assertThat("Unexpected termination.", cause, is(notNullValue()));
                verifyException(cause);
                break;
            case HttpClientFilterThrows:
            case HttpClientFilterThrowsGrpcException:
            case HttpClientFilterEmitsError:
            case HttpClientFilterEmitsGrpcException:
            case HttpFilterThrows:
            case HttpFilterThrowsGrpcException:
            case HttpFilterEmitsError:
            case HttpFilterEmitsGrpcException:
            case FilterThrows:
            case FilterThrowsGrpcException:
            case FilterEmitsError:
            case FilterEmitsGrpcException:
            case ServiceThrows:
            case ServiceThrowsGrpcException:
            case ServiceOperatorThrows:
            case ServiceOperatorThrowsGrpcException:
            case ServiceEmitsError:
            case ServiceEmitsGrpcException:
            case BlockingServiceThrows:
            case BlockingServiceThrowsGrpcException:
                cause = subscriber.awaitOnError();
                assertThat("Unexpected termination.", cause, is(notNullValue()));
                verifyException(cause);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + testMode);
        }
    }

    private void verifyException(final Future<?> result) {
        verifyException(assertThrows(ExecutionException.class, result::get).getCause());
    }

    private void verifyException(final Throwable cause) {
        assertNotNull(cause);
        assertThat(assertThrows(GrpcStatusException.class, () -> {
            throw cause;
        }).status().code(), equalTo(expectedStatus()));
    }

    private GrpcStatusCode expectedStatus() {
        switch (testMode) {
            case HttpClientFilterThrows:
            case HttpClientFilterEmitsError:
            case HttpFilterThrows:
            case HttpFilterEmitsError:
            case FilterThrows:
            case FilterEmitsError:
            case ServiceThrows:
            case ServiceOperatorThrows:
            case BlockingServiceThrows:
            case ServiceEmitsError:
            case ServiceEmitsDataThenError:
            case BlockingServiceWritesThenThrows:
                return GrpcStatusCode.UNKNOWN;
            case HttpClientFilterThrowsGrpcException:
            case HttpClientFilterEmitsGrpcException:
            case HttpFilterThrowsGrpcException:
            case HttpFilterEmitsGrpcException:
            case FilterEmitsGrpcException:
            case FilterThrowsGrpcException:
            case ServiceThrowsGrpcException:
            case ServiceOperatorThrowsGrpcException:
            case ServiceEmitsDataThenGrpcException:
            case ServiceEmitsGrpcException:
            case BlockingServiceThrowsGrpcException:
            case BlockingServiceWritesThenThrowsGrpcException:
                return cannedException.status().code();
            default:
                throw new IllegalArgumentException("Unknown mode: " + testMode);
        }
    }

    private static final class ErrorSimulatingTesterServiceFilter extends TesterServiceFilter {
        private final TesterService simulator;

        ErrorSimulatingTesterServiceFilter(final TesterService original, final TesterService simulator) {
            super(original);
            this.simulator = simulator;
        }

        @Override
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            return simulator.test(ctx, request);
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return simulator.testBiDiStream(ctx, request);
        }

        @Override
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx,
                                                          final TestRequest request) {
            return simulator.testResponseStream(ctx, request);
        }

        @Override
        public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return simulator.testRequestStream(ctx, request);
        }
    }

    private static final class ErrorProducingClientFilter implements StreamingHttpClientFilterFactory {

        private final boolean throwEx;
        private final Throwable cause;

        ErrorProducingClientFilter(final boolean throwEx, final Throwable cause) {
            this.throwEx = throwEx;
            this.cause = cause;
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    if (throwEx) {
                        return throwException(cause);
                    }
                    return Single.failed(cause);
                }
            };
        }
    }

    private static final class ErrorProducingSvcFilter implements StreamingHttpServiceFilterFactory {

        private final boolean throwEx;
        private final Throwable cause;

        ErrorProducingSvcFilter(final boolean throwEx, final Throwable cause) {
            this.throwEx = throwEx;
            this.cause = cause;
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    if (throwEx) {
                        return throwException(cause);
                    }
                    return Single.failed(cause);
                }
            };
        }
    }
}
