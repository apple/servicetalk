/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SslAndNonSslConnectionsTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    // HTTP server
    private static final StreamingHttpService STREAMING_HTTP_SERVICE = mock(StreamingHttpService.class);
    @Nullable
    private static ServerContext serverCtx;
    private static String requestTarget;

    // HTTPS server
    private static final StreamingHttpService SECURE_STREAMING_HTTP_SERVICE = mock(StreamingHttpService.class);
    @Nullable
    private static ServerContext secureServerCtx;
    private static String secureRequestTarget;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_LENGTH, ZERO);

        // Configure HTTP server
        when(STREAMING_HTTP_SERVICE.handle(any(), any(), any())).thenAnswer(
                (Answer<Single<StreamingHttpResponse>>) invocation -> {
                    StreamingHttpResponseFactory factory = invocation.getArgument(2);
                    StreamingHttpResponse resp = factory.ok();
                    resp.headers().set(httpHeaders);
                    return succeeded(resp);
                });
        when(STREAMING_HTTP_SERVICE.closeAsync()).thenReturn(completed());
        when(STREAMING_HTTP_SERVICE.closeAsyncGracefully()).thenReturn(completed());
        serverCtx = HttpServers.forAddress(localAddress(0))
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait(STREAMING_HTTP_SERVICE);
        final String serverHostHeader = hostHeader(serverHostAndPort(serverCtx));
        requestTarget = "http://" + serverHostHeader + "/";

        // Configure HTTPS server
        when(SECURE_STREAMING_HTTP_SERVICE.handle(any(), any(), any())).thenAnswer(
                invocation -> {
                    StreamingHttpResponseFactory factory = invocation.getArgument(2);
                    StreamingHttpResponse resp = factory.ok();
                    resp.headers().set(httpHeaders);
                    return succeeded(resp);
                });
        when(SECURE_STREAMING_HTTP_SERVICE.closeAsync()).thenReturn(completed());
        when(SECURE_STREAMING_HTTP_SERVICE.closeAsyncGracefully()).thenReturn(completed());
        secureServerCtx = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build())
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait(SECURE_STREAMING_HTTP_SERVICE);
        final String secureServerHostHeader = hostHeader(serverHostAndPort(secureServerCtx));
        secureRequestTarget = "https://" + secureServerHostHeader + "/";
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (serverCtx != null) {
            serverCtx.close();
        }
        if (secureServerCtx != null) {
            secureServerCtx.close();
        }
    }

    @After
    public void resetMocks() {
        clearInvocations(STREAMING_HTTP_SERVICE, SECURE_STREAMING_HTTP_SERVICE);
    }

    @Test
    public void nonSecureClientToSecureServerClosesConnection() throws Exception {
        assert secureServerCtx != null;
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(secureServerCtx))
                .buildBlocking()) {
            expectedException.expect(instanceOf(ClosedChannelException.class));
            client.request(client.get("/"));
        }
    }

    @Test
    public void secureClientToNonSecureServerClosesConnection() throws Exception {
        assert serverCtx != null;
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverCtx))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .buildBlocking()) {
            expectedException.expect(instanceOf(ClosedChannelException.class));
            client.request(client.get("/"));
        }
    }

    @Test
    public void defaultSingleAddressClientToNonSecureServer() throws Exception {
        assert serverCtx != null;
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverCtx)).buildBlocking()) {
            testRequestResponse(client, "/", false);
        }
    }

    @Test
    public void defaultMultiAddressClientToNonSecureServer() throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl().buildBlocking()) {
            testRequestResponse(client, requestTarget, false);
        }
    }

    @Test
    public void singleAddressClientWithSslToSecureServer() throws Exception {
        assert secureServerCtx != null;
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(secureServerCtx))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .buildBlocking()) {
            testRequestResponse(client, "/", true);
        }
    }

    @Test
    public void hostNameVerificationIsEnabledByDefault() throws Exception {
        assert secureServerCtx != null;
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(secureServerCtx))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build())
                .buildBlocking()) {
            expectedException.expect(instanceOf(SSLHandshakeException.class));
            // Hostname verification failure
            expectedException.expectCause(instanceOf(CertificateException.class));
            testRequestResponse(client, "/", true);
        }
    }

    @Test
    public void multiAddressClientWithSslToSecureServer() throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl()
                .initializer((scheme, address, builder) ->
                        builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname()).build()).buildStreaming())
                .buildBlocking()) {
            testRequestResponse(client, secureRequestTarget, true);
        }
    }

    @Test
    public void multiAddressClientToSecureServerThenToNonSecureServer() throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl()
                .initializer((scheme, address, builder) -> {
                    if (scheme.equalsIgnoreCase("https")) {
                        builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname()).build());
                    }
                }).buildBlocking()) {
            testRequestResponse(client, secureRequestTarget, true);
            resetMocks();
            testRequestResponse(client, requestTarget, false);
        }
    }

    @Test
    public void multiAddressClientToNonSecureServerThenToSecureServer() throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl()
                .initializer((scheme, address, builder) -> {
                    if (scheme.equalsIgnoreCase("https")) {
                        builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname()).build());
                    }
                }).buildBlocking()) {
            testRequestResponse(client, requestTarget, false);
            resetMocks();
            testRequestResponse(client, secureRequestTarget, true);
        }
    }

    private static void testRequestResponse(final BlockingHttpClient client, final String requestTarget,
                                            final boolean secure) throws Exception {
        final HttpResponse response = client.request(client.get(requestTarget));
        assertThat(response.status(), is(OK));
        verify(STREAMING_HTTP_SERVICE, !secure ? times(1) : never()).handle(any(), any(), any());
        verify(SECURE_STREAMING_HTTP_SERVICE, secure ? times(1) : never()).handle(any(), any(), any());
    }
}
