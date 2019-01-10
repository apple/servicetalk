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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.Before;
import org.junit.Test;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.AbstractHttpRequestFilterTest.RequesterType.Client;
import static io.servicetalk.http.api.AbstractHttpRequestFilterTest.SecurityType.Insecure;
import static io.servicetalk.http.api.AbstractHttpRequestFilterTest.SecurityType.Secure;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This is a test-case for the {@link AbstractHttpRequestFilterTest} HTTP request filter test utilities.
 */
public class SimpleHttpRequestFilterTest extends AbstractHttpRequestFilterTest {

    private SSLSession session;

    public SimpleHttpRequestFilterTest(final RequesterType type, final SecurityType security) {
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

    private static final class HeaderEnrichingRequestFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {
        @Override
        public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return HeaderEnrichingRequestFilter.this.request(delegate, null, strategy, request);
                }

                @Override
                protected Single<? extends ReservedStreamingHttpConnection> reserveConnection(
                        final StreamingHttpClient delegate,
                        final HttpExecutionStrategy strategy,
                        final StreamingHttpRequest request) {
                    return delegate.reserveConnection(strategy, request).map(r ->
                            new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                             final StreamingHttpRequest request) {
                                    return HeaderEnrichingRequestFilter.this.request(
                                            delegate, connectionContext(), strategy, request);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return HeaderEnrichingRequestFilter.this.request(
                            delegate(), connectionContext(), strategy, request);
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                      @Nullable final ConnectionContext context,
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
        filter.request(request);

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
            implements HttpClientFilterFactory, HttpConnectionFilterFactory {

        AtomicInteger requestCalls = new AtomicInteger();

        @Override
        public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return InterceptingRequestFilter.this.request(delegate, null, strategy, request);
                }

                @Override
                protected Single<? extends ReservedStreamingHttpConnection> reserveConnection(
                        final StreamingHttpClient delegate,
                        final HttpExecutionStrategy strategy,
                        final StreamingHttpRequest request) {
                    return delegate.reserveConnection(strategy, request)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                             final StreamingHttpRequest request) {
                                    return InterceptingRequestFilter.this.request(
                                            delegate, connectionContext(), strategy, request);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return InterceptingRequestFilter.this.request(
                            delegate(), connectionContext(), strategy, request);
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                      @Nullable final ConnectionContext context,
                                                      final HttpExecutionStrategy strategy,
                                                      final StreamingHttpRequest request) {
            requestCalls.incrementAndGet();
            return success(delegate.httpResponseFactory().ok());
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
        filter.request(filter.get("/"));
        assertThat(filterFactory.requestCalls.get(), equalTo(1));
    }

    /**
     * Simple SSL {@link Principal} verifying filter that should be applied as both connection-filter and client-filter
     * at the same time to ensure full coverage of all code paths.
     */
    private static final class SecurityEnforcingFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {
        @Override
        public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<? extends ReservedStreamingHttpConnection> reserveConnection(
                        final StreamingHttpClient delegate,
                        final HttpExecutionStrategy strategy,
                        final StreamingHttpRequest request) {
                    return delegate.reserveConnection(strategy, request)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                             final StreamingHttpRequest request) {
                                    return SecurityEnforcingFilter.this.request(
                                            delegate, connectionContext(), strategy, request);
                                }
                            });
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
                    return SecurityEnforcingFilter.this.request(
                            delegate(), connectionContext(), strategy, request);
                }
            };
        }

        private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                      final ConnectionContext context,
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
                return error(e);
            }

            return success(delegate.httpResponseFactory().unauthorized());
        }
    }

    @Test
    public void unauthorizedConnectionRefusingFilterWithInvalidPrincipal() throws Exception {

        BlockingHttpRequester filter = createFilter(new SecurityEnforcingFilter()).asBlockingRequester();
        HttpResponse resp = filter.request(filter.get("/"));

        if (type == Client) {
            return; // Clients don't carry SSL Context
        }

        assertThat(resp.status(), equalTo(HttpResponseStatuses.UNAUTHORIZED));
    }

    @Test
    public void unauthorizedConnectionRefusingFilterWithValidPrincipal() throws Exception {

        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("unit.test.auth");
        when(session.getPeerPrincipal()).thenReturn(principal);

        BlockingHttpRequester filter = createFilter(new SecurityEnforcingFilter()).asBlockingRequester();
        HttpResponse resp = filter.request(filter.get("/"));

        if (type == Client) {
            return; // Clients don't carry SSL Context
        }

        if (security == Insecure) {
            assertThat(resp.status(), equalTo(HttpResponseStatuses.UNAUTHORIZED));
        } else {
            assertThat(resp.status(), equalTo(HttpResponseStatuses.OK));
        }
    }
}
