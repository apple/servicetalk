/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequestWithContextHandler.ok;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequesterType.Client;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequesterType.Connection;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequesterType.ReservedConnection;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.SecurityType.Insecure;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.SecurityType.Secure;
import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.FilterFactoryUtils.appendConnectionFilterFactory;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * This parameterized test facilitates running HTTP requester filter tests under all calling variations: client,
 * connection, reserved connection, with and without SSL context.
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractHttpRequesterFilterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    public enum SecurityType { Secure, Insecure }

    public enum RequesterType { Client, Connection, ReservedConnection }

    private final SSLSession sslSession = mock(SSLSession.class);
    private final CompositeCloseable closeables = AsyncCloseables.newCompositeCloseable();

    @Mock
    private HttpExecutionContext mockExecutionContext;

    @Mock
    private HttpConnectionContext mockConnectionContext;

    @SuppressWarnings("unused")
    protected static Stream<Arguments> requesterTypes() {
        return Stream.of(
                Arguments.of(Client, Secure),
                Arguments.of(Client, Insecure),
                Arguments.of(Connection, Secure),
                Arguments.of(Connection, Insecure),
                Arguments.of(ReservedConnection, Secure),
                Arguments.of(ReservedConnection, Insecure));
    }

    @BeforeEach
    final void setupContext() {
        lenient().when(mockConnectionContext.remoteAddress()).thenAnswer(__ -> remoteAddress());
        lenient().when(mockConnectionContext.localAddress()).thenAnswer(__ -> localAddress());
    }

    protected void setUp(SecurityType security) {
        lenient().when(mockConnectionContext.sslSession()).thenAnswer(__ -> {
            switch (security) {
                case Secure:
                    return sslSession();
                case Insecure:
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    protected InetSocketAddress remoteAddress() {
        return InetSocketAddress.createUnresolved("127.0.1.1", 80);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    protected InetSocketAddress localAddress() {
        return InetSocketAddress.createUnresolved("127.0.1.2", 28080);
    }

    protected SSLSession sslSession() {
        return sslSession;
    }

    protected Publisher<Object> loadbalancerEvents() {
        return Publisher.empty();
    }

    @AfterEach
    final void closeClients() throws Exception {
        closeables.close();
    }

    /**
     * Creates filter with default OK request handlers.
     *
     * @param filterFactory factory used to create the filters
     * @param <FF> type capture for the filter factory
     * @return a filtered {@link StreamingHttpRequester}
     */
    protected final <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory>
        StreamingHttpRequester createFilter(RequesterType type, FF filterFactory) {
        return createFilter(type, RequestHandler.ok(), ok(), filterFactory);
    }

    /**
     * Creates filter with provided request handler.
     *
     * @param rh handler responding to requests without {@link ConnectionContext}
     * @param filterFactory factory used to create the filters
     * @param <FF> type capture for the filter factory
     * @return a filtered {@link StreamingHttpRequester}
     */
    protected final <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory>
        StreamingHttpRequester createFilter(RequesterType type, RequestHandler rh, FF filterFactory) {
        return createFilter(type, rh, rh.withContext(), filterFactory);
    }

    /**
     * Creates filter with provided request handlers.
     *
     * @param rh handler responding to requests without {@link ConnectionContext}
     * @param rwch handler responding to requests with {@link ConnectionContext}
     * @param filterFactory factory used to create the filters
     * @param <FF> type capture for the filter factory
     * @return a filtered {@link StreamingHttpRequester}
     */
    protected final <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory>
        StreamingHttpRequester createFilter(RequesterType type, RequestHandler rh,
                                            RequestWithContextHandler rwch, FF filterFactory) {
        switch (type) {
            case Client:
                return closeables.prepend(newClient(rh, rwch, filterFactory));
            case Connection:
                return closeables.prepend(newConnection(rwch, filterFactory));
            case ReservedConnection:
                try {
                    return closeables.prepend(newClient(rh, rwch, filterFactory))
                            .reserveConnection(REQ_RES_FACTORY.get("")).toFuture().get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            default:
                throw new IllegalArgumentException(type.name());
        }
    }

    protected BlockingHttpRequester asBlockingRequester(StreamingHttpRequester requester) {
        if (requester instanceof StreamingHttpClient) {
            return ((StreamingHttpClient) requester).asBlockingClient();
        } else {
            return ((StreamingHttpConnection) requester).asBlockingConnection();
        }
    }

    /**
     * Handler for {@link HttpRequester#request(HttpExecutionStrategy, HttpRequest)} calls as delegated from the filter
     * under test.
     */
    @FunctionalInterface
    public interface RequestHandler {
        /**
         * Handle requests from the filter under test.
         *
         * @param respFactory the {@link StreamingHttpResponseFactory} of the {@link StreamingHttpRequester}
         * @param request {@link StreamingHttpRequest} to handle
         * @return the {@link StreamingHttpResponse} to return to the filter
         */

        Single<StreamingHttpResponse> request(StreamingHttpResponseFactory respFactory,
                                              StreamingHttpRequest request);

        /**
         * Default OK response handler.
         * @return OK response handler.
         */
        static RequestHandler ok() {
            return (respFactory, request) -> succeeded(REQ_RES_FACTORY.ok());
        }

        /**
         * Conversion to {@link RequestWithContextHandler}.
         * @return a conversion of this handler to {@link RequestWithContextHandler}.
         */
        default RequestWithContextHandler withContext() {
            return (respFactory, context, request) -> request(respFactory, request);
        }
    }

    /**
     * Handler for {@link HttpRequester#request(HttpExecutionStrategy, HttpRequest)} calls with {@link
     * ConnectionContext} information as delegated from the filter under test.
     */
    @FunctionalInterface
    public interface RequestWithContextHandler {
        /**
         * Handle requests with {@link ConnectionContext} information from the filter under test.
         *
         * @param respFactory the {@link StreamingHttpResponseFactory} of the {@link StreamingHttpRequester}
         * @param context {@link ConnectionContext} of the {@link StreamingHttpRequester}
         * @param request {@link StreamingHttpRequest} to handle
         * @return the {@link StreamingHttpResponse} to return to the filter
         */
        Single<StreamingHttpResponse> request(StreamingHttpResponseFactory respFactory,
                                              ConnectionContext context,
                                              StreamingHttpRequest request);

        /**
         * Default OK response handler.
         * @return OK response handler.
         */
        static RequestWithContextHandler ok() {
            return (respFactory, context, request) -> succeeded(REQ_RES_FACTORY.ok());
        }
    }

    private ReservedStreamingHttpConnection newReservedConnection() {
        final StreamingHttpConnection connection = newConnection(ok(), null);
        return new ReservedStreamingHttpConnection() {
            @Override
            public ReservedHttpConnection asConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReservedBlockingHttpConnection asBlockingConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return connection.newRequest(method, requestTarget);
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return connection.request(request);
            }

            @Override
            public Completable closeAsync() {
                return connection.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return connection.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return connection.onClose();
            }

            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return connection.request(strategy, request);
            }

            @Override
            public HttpExecutionContext executionContext() {
                return connection.executionContext();
            }

            @Override
            public StreamingHttpResponseFactory httpResponseFactory() {
                return connection.httpResponseFactory();
            }

            @Override
            public void close() throws Exception {
                connection.close();
            }

            @Override
            public HttpConnectionContext connectionContext() {
                return connection.connectionContext();
            }

            @Override
            public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                return connection.transportEventStream(eventKey);
            }

            @Override
            public Completable releaseAsync() {
                return Completable.completed();
            }
        };
    }

    private StreamingHttpConnection newConnection(final RequestWithContextHandler rwch,
                                                  @Nullable final StreamingHttpConnectionFilterFactory filterFactory) {
        final StreamingHttpConnectionFilterFactory handlerFilter = conn -> new StreamingHttpConnectionFilter(conn) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return rwch.request(REQ_RES_FACTORY, connectionContext(), request);
            }
        };

        return TestStreamingHttpConnection.from(REQ_RES_FACTORY, mockExecutionContext,
                mockConnectionContext, filterFactory == null ? handlerFilter :
                        appendConnectionFilterFactory(filterFactory, handlerFilter));
    }

    private <FF extends StreamingHttpClientFilterFactory & StreamingHttpConnectionFilterFactory> StreamingHttpClient
        newClient(final RequestHandler rh, final RequestWithContextHandler rwch, final FF filterFactory) {
        StreamingHttpClientFilterFactory handlerFilter = client -> new StreamingHttpClientFilter(client) {

                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return rh.request(REQ_RES_FACTORY, request);
                    }

                    @Override
                    public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                            final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                        return succeeded(newReservedConnection()).map(rc ->
                                new ReservedStreamingHttpConnectionFilter(rc) {
                                    @Override
                                    protected Single<StreamingHttpResponse> request(
                                            final StreamingHttpRequester delegate,
                                            final HttpExecutionStrategy strategy,
                                            final StreamingHttpRequest request) {
                                        return rwch.request(delegate.httpResponseFactory(), connectionContext(),
                                                request);
                                    }
                                });
                    }
                };

        return TestStreamingHttpClient.from(REQ_RES_FACTORY, mockExecutionContext,
                appendClientFilterFactory(filterFactory, handlerFilter));
    }
}
