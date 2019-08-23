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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.lang.String.format;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultiAddressUrlHttpClientSslTest {

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

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
                    return succeeded(resp);
                });
        when(STREAMING_HTTP_SERVICE.closeAsync()).thenReturn(completed());
        when(STREAMING_HTTP_SERVICE.closeAsyncGracefully()).thenReturn(completed());
        serverCtx = HttpServers.forAddress(localAddress(0))
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait(STREAMING_HTTP_SERVICE);
        serverHostHeader = hostHeader(serverHostAndPort(serverCtx));

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
                .secure().commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait(SECURE_STREAMING_HTTP_SERVICE);
        secureServerHostHeader = hostHeader(serverHostAndPort(secureServerCtx));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (serverCtx != null) {
            serverCtx.closeAsync().toFuture().get();
        }
        if (secureServerCtx != null) {
            secureServerCtx.closeAsync().toFuture().get();
        }
    }

    @After
    public void resetMocks() {
        clearInvocations(STREAMING_HTTP_SERVICE, SECURE_STREAMING_HTTP_SERVICE);
    }

    @Test
    public void nonSecureClientToSecureServer() throws Exception {
        HttpClient client = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .build();

        HttpRequest request = client.get("/")
                .addHeader(HOST, secureServerHostHeader)
                .addHeader(CONTENT_LENGTH, ZERO);

        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(ClosedChannelException.class));
        awaitIndefinitely(client.request(request));
    }

    @Test
    public void secureClientToNonSecureServer() throws Exception {
        HttpClient client = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .effectiveScheme(__ -> "https")
                .executionStrategy(defaultStrategy(CTX.executor()))
                .build();

        HttpRequest request = client.get("/")
                .addHeader(HOST, serverHostHeader)
                .addHeader(CONTENT_LENGTH, ZERO);

        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(ClosedChannelException.class));
        awaitIndefinitely(client.request(request));
    }

    @Test
    public void requesterWithDefaultSslConfigProvider() throws Exception {
        try (BlockingHttpRequester client = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildBlocking()) {
            testOnlyNonSecureRequestTargets(client);
        }
    }

    @Test
    public void requesterWithPlainSslConfigProvider() throws Exception {
        try (BlockingHttpRequester client = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildBlocking()) {
            testOnlyNonSecureRequestTargets(client);
        }
    }

    @Test
    public void requesterWithSecureSslConfigProvider() throws Exception {
        try (BlockingHttpRequester client = HttpClients.forMultiAddressUrl()
                .effectiveScheme(__ -> "https")
                .secure((hap, config) -> config.disableHostnameVerification()
                        .trustManager(DefaultTestCerts::loadMutualAuthCaPem))
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildBlocking()) {
            testAllFormsOfRequestTargetWithSecureByDefault(client);
        }
    }

    private static void testOnlyNonSecureRequestTargets(final BlockingHttpRequester requester)
            throws Exception {

        requestAndValidate(requester, "/", serverHostHeader);
        requestAndValidate(requester, format("http://%s/", serverHostHeader), serverHostHeader);
        // Do not test default security config, because our generated certificates require custom trust manager

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
        HttpRequest request = requester.get(requestTarget)
                .addHeader(HOST, hostHeader)
                .addHeader(CONTENT_LENGTH, ZERO);
        assertEquals(OK, requester.request(noOffloadsStrategy(), request).status());
    }
}
