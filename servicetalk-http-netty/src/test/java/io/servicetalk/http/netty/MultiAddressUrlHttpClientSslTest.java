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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.SslConfigBuilder;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.HttpServers.newHttpServerBuilder;
import static io.servicetalk.http.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.netty.SslConfigProviders.secureByDefault;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultiAddressUrlHttpClientSslTest {

    private static final String HOSTNAME = "localhost";

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    // HTTP server
    private static final StreamingHttpService STREAMING_HTTP_SERVICE = mock(StreamingHttpService.class);
    @Nullable
    private static ServerContext serverCtx;
    private static String serverHostHeader;

    // HTTPS server
    private static final StreamingHttpService SECURE_STREAMING_HTTP_SERVICE = mock(StreamingHttpService.class);
    @Nullable
    private static ServerContext secureServerCtx;
    private static String secureServerHostHeader;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_LENGTH, ZERO);

        // Configure HTTP server
        when(STREAMING_HTTP_SERVICE.handle(any(), any(), any())).thenAnswer(
                (Answer<Single<StreamingHttpResponse>>) invocation -> {
                    StreamingHttpResponseFactory factory = invocation.getArgument(2);
                    StreamingHttpResponse resp = factory.ok();
                    resp.headers().set(httpHeaders);
                    return success(resp);
                });
        when(STREAMING_HTTP_SERVICE.closeAsync()).thenReturn(completed());
        when(STREAMING_HTTP_SERVICE.closeAsyncGracefully()).thenReturn(completed());
        serverCtx = newHttpServerBuilder(new InetSocketAddress(HOSTNAME, 0))
                .executionContext(CTX)
                .listenStreamingAndAwait(STREAMING_HTTP_SERVICE);
        serverHostHeader = HostAndPort.of(HOSTNAME,
                ((InetSocketAddress) serverCtx.getListenAddress()).getPort()).toString();

        // Configure HTTPS server
        when(SECURE_STREAMING_HTTP_SERVICE.handle(any(), any(), any())).thenAnswer(
                invocation -> {
                    StreamingHttpResponseFactory factory = invocation.getArgument(2);
                    StreamingHttpResponse resp = factory.ok();
                    resp.headers().set(httpHeaders);
                    return success(resp);
                });
        when(SECURE_STREAMING_HTTP_SERVICE.closeAsync()).thenReturn(completed());
        when(SECURE_STREAMING_HTTP_SERVICE.closeAsyncGracefully()).thenReturn(completed());
        secureServerCtx = newHttpServerBuilder(new InetSocketAddress(HOSTNAME, 0))
                .sslConfig(SslConfigBuilder.forServer(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build())
                .executionContext(CTX)
                .listenStreamingAndAwait(SECURE_STREAMING_HTTP_SERVICE);
        secureServerHostHeader = HostAndPort.of(HOSTNAME,
                ((InetSocketAddress) secureServerCtx.getListenAddress()).getPort()).toString();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (serverCtx != null) {
            awaitIndefinitely(serverCtx.closeAsync());
        }
        if (secureServerCtx != null) {
            awaitIndefinitely(secureServerCtx.closeAsync());
        }
    }

    @After
    public void resetMocks() {
        clearInvocations(STREAMING_HTTP_SERVICE, SECURE_STREAMING_HTTP_SERVICE);
    }

    @Test(expected = ExecutionException.class)
    public void nonSecureClientToSecureServer() throws Exception {
        HttpRequester requester = HttpClients.forMultiAddressUrl().executionContext(CTX).build();

        HttpRequest request = requester.get("/");
        request.headers().add(HOST, secureServerHostHeader);
        request.headers().add(CONTENT_LENGTH, ZERO);
        await(requester.request(request), 2, SECONDS);
    }

    @Test(expected = TimeoutException.class)
    public void secureClientToNonSecureServer() throws Exception {
        HttpRequester requester = HttpClients.forMultiAddressUrl().sslConfigProvider(secureByDefault())
                .executionContext(CTX).build();

        HttpRequest request = requester.get("/");
        request.headers().add(HOST, serverHostHeader);
        request.headers().add(CONTENT_LENGTH, ZERO);
        await(requester.request(request), 2, SECONDS);
    }

    @Test
    public void requesterWithDefaultSslConfigProvider() throws Exception {
        try (BlockingHttpRequester requester = HttpClients.forMultiAddressUrl()
                .executionContext(CTX).buildBlocking()) {
            testOnlyNonSecureRequestTargets(requester);
        }
    }

    @Test
    public void requesterWithPlainSslConfigProvider() throws Exception {
        try (BlockingHttpRequester requester = HttpClients.forMultiAddressUrl().sslConfigProvider(plainByDefault())
                .executionContext(CTX).buildBlocking()) {
            testOnlyNonSecureRequestTargets(requester);
        }
    }

    @Test
    public void requesterWithSecureSslConfigProvider() throws Exception {
        SslConfigProvider sslConfigProvider = new SslConfigProvider() {
            @Override
            public int defaultPort(final HttpScheme scheme, @Nullable final String effectiveHost) {
                return secureByDefault().defaultPort(scheme, effectiveHost);
            }

            @Override
            public SslConfig forHostAndPort(final HostAndPort hostAndPort) {
                return forClient(hostAndPort)
                        // required for generated certificates
                        .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                        .build();
            }
        };
        try (BlockingHttpRequester requester = HttpClients.forMultiAddressUrl().sslConfigProvider(sslConfigProvider)
                .executionContext(CTX).buildBlocking()) {
            testAllFormsOfRequestTargetWithSecureByDefault(requester);
        }
    }

    private static void testOnlyNonSecureRequestTargets(final BlockingHttpRequester requester)
            throws Exception {

        requestAndValidate(requester, "/", serverHostHeader);
        requestAndValidate(requester, format("http://%s/", serverHostHeader), serverHostHeader);
        // Do not test default SslConfig, because our generated certificates require custom trust manager

        verify(STREAMING_HTTP_SERVICE, times(2)).handle(any(), any(), any());
        verify(SECURE_STREAMING_HTTP_SERVICE, never()).handle(any(), any(), any());
    }

    private static void testAllFormsOfRequestTargetWithSecureByDefault(final BlockingHttpRequester requester)
            throws Exception {

        requestAndValidate(requester, "/", secureServerHostHeader);
        requestAndValidate(requester, format("http://%s/", serverHostHeader), serverHostHeader);
        requestAndValidate(requester, format("https://%s/", secureServerHostHeader), secureServerHostHeader);

        verify(STREAMING_HTTP_SERVICE).handle(any(), any(), any());
        verify(SECURE_STREAMING_HTTP_SERVICE, times(2)).handle(any(), any(), any());
    }

        private static void requestAndValidate(final BlockingHttpRequester requester,
                                           final String requestTarget, final String hostHeader) throws Exception {
        HttpRequest request = requester.get(requestTarget);
        request.headers().add(HOST, hostHeader);
        request.headers().add(CONTENT_LENGTH, ZERO);
        assertEquals(OK, requester.request(request).status());
    }
}
