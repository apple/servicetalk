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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Single;

import org.junit.Before;
import org.junit.Test;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequesterType.Client;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.SecurityType.Insecure;
import static io.servicetalk.http.api.AbstractHttpRequesterFilterTest.SecurityType.Secure;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This is a test-case for the {@link AbstractHttpRequesterFilterTest} HTTP request filter test utilities.
 */
public class SimpleHttpRequesterFilterTest extends AbstractHttpRequesterFilterTest {

    private SSLSession session;

    public SimpleHttpRequesterFilterTest(final RequesterType type, final SecurityType security) {
        super(type, security);
    }

    @Before
    public void setUp() {
        session = mock(SSLSession.class);
    }

    @Override
    protected SSLSession sslSession() {
        return session;
    }

    private static final class HeaderEnrichingRequestFilter implements StreamingHttpClientFilterFactory,
                                                                       StreamingHttpConnectionFilterFactory {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return HeaderEnrichingRequestFilter.this.request(delegate, null, strategy, request);
                }

                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                    return delegate().reserveConnection(strategy, metaData).map(r ->
                            new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                protected Single<StreamingHttpResponse> request(
                                        final StreamingHttpRequester delegate,
                                        final HttpExecutionStrategy strategy,
                                        final StreamingHttpRequest request) {
                                    return HeaderEnrichingRequestFilter.this.request(
                                            delegate, connectionContext(), strategy, request);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return HeaderEnrichingRequestFilter.this.request(delegate(), connectionContext(), strategy,
                            request);
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                      @Nullable final HttpConnectionContext context,
                                                      final HttpExecutionStrategy strategy,
                                                      final StreamingHttpRequest request) {
            request.setHeader("X-Unit", "Test");
            if (context != null) {
                request.setHeader("X-Local", context.localAddress().toString());
                request.setHeader("X-Remote", context.remoteAddress().toString());
                if (context.sslSession() != null) {
                    request.setHeader("X-Secure", "True");
                }
            }
            return delegate.request(strategy, request);
        }
    }

    @Test
    public void headersEnrichedByFilter() {
        StreamingHttpRequester filter = createFilter(new HeaderEnrichingRequestFilter());
        StreamingHttpRequest request = filter.get("/");
        filter.request(defaultStrategy(), request);

        assertThat(request.headers().get("X-Unit"), hasToString("Test"));
        if (type != Client) {
            assertThat(request.headers().get("X-Local"), hasToString("127.0.1.2:28080"));
            assertThat(request.headers().get("X-Remote"), hasToString("127.0.1.1:80"));
            if (security == Secure) {
                assertThat(request.headers().get("X-Secure"), hasToString("True"));
            }
        }
    }

    private static final class InterceptingRequestFilter
            implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

        AtomicInteger requestCalls = new AtomicInteger();

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return InterceptingRequestFilter.this.request(delegate);
                }

                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                    return delegate().reserveConnection(strategy, metaData)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                protected Single<StreamingHttpResponse> request(
                                        final StreamingHttpRequester delegate,
                                        final HttpExecutionStrategy strategy,
                                        final StreamingHttpRequest request) {
                                    return InterceptingRequestFilter.this.request(delegate);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return InterceptingRequestFilter.this.request(delegate());
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate) {
            requestCalls.incrementAndGet();
            return succeeded(delegate.httpResponseFactory().ok());
        }
    }

    @Test
    public void requestInterceptedByFilter() {
        InterceptingRequestFilter filterFactory = new InterceptingRequestFilter();
        StreamingHttpRequester filter = createFilter(
                (respFactory, request) -> {
                    fail("Filter should have intercepted this request() call");
                    return null;
                },
                (respFactory, context, request) -> {
                    fail("Filter should have intercepted this request() call");
                    return null;
                }, filterFactory);
        filter.request(defaultStrategy(), filter.get("/"));
        assertThat(filterFactory.requestCalls.get(), equalTo(1));
    }

    /**
     * Simple SSL {@link Principal} verifying filter that should be applied as both connection-filter and client-filter
     * at the same time to ensure full coverage of all code paths.
     */
    private static final class SecurityEnforcingFilter implements StreamingHttpClientFilterFactory,
                                                                  StreamingHttpConnectionFilterFactory {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                    return delegate().reserveConnection(strategy, metaData)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                protected Single<StreamingHttpResponse> request(
                                        final StreamingHttpRequester delegate,
                                        final HttpExecutionStrategy strategy,
                                        final StreamingHttpRequest request) {
                                    return SecurityEnforcingFilter.this.request(
                                            delegate, connectionContext(), strategy, request);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return SecurityEnforcingFilter.this.request(delegate(), connectionContext(), strategy, request);
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                      final HttpConnectionContext context,
                                                      final HttpExecutionStrategy strategy,
                                                      final StreamingHttpRequest request) {
            try {
                final SSLSession sslSession = context.sslSession();
                if (sslSession != null && sslSession.getPeerPrincipal() != null
                        && sslSession.getPeerPrincipal().getName().equals("unit.test.auth")) {
                    // proper SSL Session established, continue with delegation
                    return delegate.request(strategy, request);
                }
            } catch (SSLPeerUnverifiedException e) {
                return failed(e);
            }

            return succeeded(delegate.httpResponseFactory().unauthorized());
        }
    }

    @Test
    public void unauthorizedConnectionRefusingFilterWithInvalidPrincipal() throws Exception {

        BlockingHttpRequester filter = asBlockingRequester(createFilter(new SecurityEnforcingFilter()));
        HttpResponse resp = filter.request(defaultStrategy(), filter.get("/"));

        if (type == Client) {
            return; // Clients don't carry SSL Context
        }

        assertThat(resp.status(), equalTo(HttpResponseStatus.UNAUTHORIZED));
    }

    @Test
    public void unauthorizedConnectionRefusingFilterWithValidPrincipal() throws Exception {

        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("unit.test.auth");
        when(session.getPeerPrincipal()).thenReturn(principal);

        BlockingHttpRequester filter = asBlockingRequester(createFilter(new SecurityEnforcingFilter()));
        HttpResponse resp = filter.request(defaultStrategy(), filter.get("/"));

        if (type == Client) {
            return; // Clients don't carry SSL Context
        }

        if (security == Insecure) {
            assertThat(resp.status(), equalTo(HttpResponseStatus.UNAUTHORIZED));
        } else {
            assertThat(resp.status(), equalTo(HttpResponseStatus.OK));
        }
    }
}
