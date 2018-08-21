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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.AggregatedHttpRequest;
import io.servicetalk.http.api.AggregatedHttpRequester;
import io.servicetalk.http.api.BlockingAggregatedHttpRequester;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpService;
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

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.netty.SslConfigProviders.secureByDefault;
import static io.servicetalk.test.resources.DefaultTestCerts.loadMutualAuthCaPem;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerKey;
import static io.servicetalk.test.resources.DefaultTestCerts.loadServerPem;
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
    private static final HttpService httpService = mock(HttpService.class);
    @Nullable
    private static ServerContext serverCtx;
    private static String serverHostHeader;

    // HTTPS server
    private static final HttpService secureHttpService = mock(HttpService.class);
    @Nullable
    private static ServerContext secureServerCtx;
    private static String secureServerHostHeader;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_LENGTH, ZERO);

        // Configure HTTP server
        when(httpService.handle(any(), any())).thenReturn(success(newResponse(OK, httpHeaders)));
        when(httpService.closeAsync()).thenReturn(completed());
        when(httpService.closeAsyncGracefully()).thenReturn(completed());
        serverCtx = awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .start(CTX, new InetSocketAddress(HOSTNAME, 0), httpService));
        serverHostHeader = HostAndPort.of(HOSTNAME,
                ((InetSocketAddress) serverCtx.getListenAddress()).getPort()).toString();

        // Configure HTTPS server
        when(secureHttpService.handle(any(), any())).thenReturn(success(newResponse(OK, httpHeaders)));
        when(secureHttpService.closeAsync()).thenReturn(completed());
        when(secureHttpService.closeAsyncGracefully()).thenReturn(completed());
        secureServerCtx = awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .setSslConfig(SslConfigBuilder.forServer(() -> loadServerPem(), () -> loadServerKey()).build())
                .start(CTX, new InetSocketAddress(HOSTNAME, 0), secureHttpService));
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
        clearInvocations(httpService, secureHttpService);
    }

    @Test(expected = ExecutionException.class)
    public void nonSecureClientToSecureServer() throws Exception {
        AggregatedHttpRequester requester = HttpClients.forMultiAddressUrl()
                .buildAggregated(CTX);

        AggregatedHttpRequest<HttpPayloadChunk> request = newRequest(GET, "/");
        request.getHeaders().add(HOST, secureServerHostHeader);
        request.getHeaders().add(CONTENT_LENGTH, ZERO);
        await(requester.request(request), 2, SECONDS);
    }

    @Test(expected = TimeoutException.class)
    public void secureClientToNonSecureServer() throws Exception {
        AggregatedHttpRequester requester = HttpClients.forMultiAddressUrl().setSslConfigProvider(secureByDefault())
                .buildAggregated(CTX);

        AggregatedHttpRequest<HttpPayloadChunk> request = newRequest(GET, "/");
        request.getHeaders().add(HOST, serverHostHeader);
        request.getHeaders().add(CONTENT_LENGTH, ZERO);
        await(requester.request(request), 2, SECONDS);
    }

    @Test
    public void requesterWithDefaultSslConfigProvider() throws Exception {
        try (BlockingAggregatedHttpRequester requester = HttpClients.forMultiAddressUrl()
                .buildBlockingAggregated(CTX)) {
            testOnlyNonSecureRequestTargets(requester);
        }
    }

    @Test
    public void requesterWithPlainSslConfigProvider() throws Exception {
        try (BlockingAggregatedHttpRequester requester = HttpClients.forMultiAddressUrl().setSslConfigProvider(plainByDefault())
                .buildBlockingAggregated(CTX)) {
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
                        .trustManager(() -> loadMutualAuthCaPem())
                        .build();
            }
        };
        try (BlockingAggregatedHttpRequester requester = HttpClients.forMultiAddressUrl().setSslConfigProvider(sslConfigProvider)
                .buildBlockingAggregated(CTX)) {
            testAllFormsOfRequestTargetWithSecureByDefault(requester);
        }
    }

    private static void testOnlyNonSecureRequestTargets(final BlockingAggregatedHttpRequester requester)
            throws Exception {

        requestAndValidate(requester, "/", serverHostHeader);
        requestAndValidate(requester, format("http://%s/", serverHostHeader), serverHostHeader);
        // Do not test default SslConfig, because our generated certificates require custom trust manager

        verify(httpService, times(2)).handle(any(), any());
        verify(secureHttpService, never()).handle(any(), any());
    }

    private static void testAllFormsOfRequestTargetWithSecureByDefault(final BlockingAggregatedHttpRequester requester)
            throws Exception {

        requestAndValidate(requester, "/", secureServerHostHeader);
        requestAndValidate(requester, format("http://%s/", serverHostHeader), serverHostHeader);
        requestAndValidate(requester, format("https://%s/", secureServerHostHeader), secureServerHostHeader);

        verify(httpService).handle(any(), any());
        verify(secureHttpService, times(2)).handle(any(), any());
    }

        private static void requestAndValidate(final BlockingAggregatedHttpRequester requester,
                                           final String requestTarget, final String hostHeader) throws Exception {
        AggregatedHttpRequest<HttpPayloadChunk> request = newRequest(GET, requestTarget);
        request.getHeaders().add(HOST, hostHeader);
        request.getHeaders().add(CONTENT_LENGTH, ZERO);
        assertEquals(OK, requester.request(request).getStatus());
    }
}
