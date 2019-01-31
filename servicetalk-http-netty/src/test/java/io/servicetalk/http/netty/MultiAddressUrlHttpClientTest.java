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

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.CREATED;
import static io.servicetalk.http.api.HttpResponseStatuses.FORBIDDEN;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_IMPLEMENTED;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatuses.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatuses.UNAUTHORIZED;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class MultiAddressUrlHttpClientTest {

    private static final String X_REQUESTED_LOCATION = "X-Requested-Location";

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final MockedSingleListenerRule<StreamingHttpResponse> listener = new MockedSingleListenerRule<>();

    private static CompositeCloseable afterClassCloseables;
    private static StreamingHttpService httpService;
    private static ServerContext serverCtx;
    private static String serverHost;
    private static int serverPort;
    private static String hostHeader;
    private static StreamingHttpRequester requester;

    @BeforeClass
    public static void beforeClass() throws Exception {
        afterClassCloseables = newCompositeCloseable();

        requester = afterClassCloseables.append(HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .buildStreaming());

        final HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_LENGTH, ZERO);
        httpService = new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory factory) {
                if (request.version() == HTTP_1_1 && !request.headers().contains(HOST)) {
                    StreamingHttpResponse resp = factory.newResponse(BAD_REQUEST);
                    resp.headers().set(httpHeaders);
                    return success(resp);
                }

                if (request.method() == OPTIONS || request.method() == CONNECT) {
                    StreamingHttpResponse resp = factory.ok();
                    resp.headers().set(httpHeaders);
                    return success(resp);
                }
                try {
                    HttpResponseStatus status = getResponseStatus(parseInt(request.path().substring(1)), EMPTY_BUFFER);
                    StreamingHttpResponse response = factory.newResponse(status);
                    response.headers().set(httpHeaders);
                    final CharSequence locationHeader = request.headers().get(X_REQUESTED_LOCATION);
                    if (locationHeader != null) {
                        response.headers().set(LOCATION, locationHeader);
                    }
                    return success(response);
                } catch (Exception e) {
                    StreamingHttpResponse resp = factory.newResponse(BAD_REQUEST);
                    resp.headers().set(httpHeaders);
                    return success(resp);
                }
            }

            @Override
            public HttpExecutionStrategy executionStrategy() {
                return noOffloadsStrategy();
            }
        };
        serverCtx = startNewLocalServer(httpService, afterClassCloseables);

        final HostAndPort serverHostAndPort = of((InetSocketAddress) serverCtx.listenAddress());
        serverHost = serverHostAndPort.getHostName();
        serverPort = serverHostAndPort.getPort();
        hostHeader = hostHeader(serverHostAndPort);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        afterClassCloseables.close();
    }

    @Test
    public void requestWithRelativeFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = requester.get("/200?param=value");
        request.headers().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithRelativeFormRequestTargetWithoutHostHeader() {
        StreamingHttpRequest request = requester.get("/200?param=value");
        // no host header set
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test(expected = ExecutionException.class)
    @Ignore("LoadBalancerReadySubscriber will never complete for a wrong host") // FIXME: remove @Ignore annotation
    public void requestWithRelativeFormRequestTargetWithInvalidHostInHeader() throws Exception {
        StreamingHttpRequest request = requester.get("/200?param=value");
        request.headers().set(HOST, "invalid.:" + serverPort);
        awaitIndefinitely(requester.request(request));
    }

    @Test(expected = ExecutionException.class)
    public void requestWithRelativeFormRequestTargetWithWrongPortInHeader() throws Exception {
        StreamingHttpRequest request = requester.get("/200?param=value");
        request.headers().set(HOST, format("%s:%d", serverHost, serverPort + 1));
        awaitIndefinitely(requester.request(request));
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request =
                requester.get(format("http://%s/200?param=value#tag", hostHeader));
        request.headers().set(HOST, "invalid.:8080");    // value in the HOST header should be ignored
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithoutHostHeader() throws Exception {
        StreamingHttpRequest request =
                requester.get(format("http://%s/200?param=value#tag", hostHeader));
        requestAndValidate(request, OK);
    }

    @Test(expected = ExecutionException.class)
    @Ignore("LoadBalancerReadySubscriber will never complete for a wrong host") // FIXME: remove @Ignore annotation
    public void requestWithAbsoluteFormRequestTargetWithInvalidHost() throws Exception {
        StreamingHttpRequest request = requester.get(
                format("http://invalid.:%d/200?param=value#tag", serverPort));
        awaitIndefinitely(requester.request(request));
    }

    @Test(expected = ExecutionException.class)
    public void requestWithAbsoluteFormRequestTargetWithWrongPort() throws Exception {
        StreamingHttpRequest request = requester.get(
                format("http://%s:%d/200?param=value#tag", serverHost, serverPort + 1));
        awaitIndefinitely(requester.request(request));
    }

    @Test
    public void requestWithIncorrectPortInAbsoluteFormRequestTarget() {
        StreamingHttpRequest request =
                requester.get(format("http://%s:-1/200?param=value#tag", serverHost));
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void requestWithAuthorityFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = requester.newRequest(CONNECT, hostHeader);
        request.headers().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAsteriskFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = requester.newRequest(OPTIONS, "*");
        request.headers().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAsteriskFormRequestTargetWithoutHostHeader() {
        StreamingHttpRequest request = requester.newRequest(OPTIONS, "*");
        // no host header set
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void requestWithRedirect() throws Exception {
        StreamingHttpRequest request = requester.get("/301");
        request.headers().set(HOST, hostHeader);
        request.headers().set(X_REQUESTED_LOCATION, "/200");  // Location for redirect
        requestAndValidate(request, OK);
    }

    @Test
    public void multipleRequestsToMultipleServers() throws Exception {
        try (CompositeCloseable closeables = newCompositeCloseable()) {
            ServerContext serverCtx2 = startNewLocalServer(httpService, closeables);
            ServerContext serverCtx3 = startNewLocalServer(httpService, closeables);

            List<HttpResponseStatus> statuses = asList(OK, CREATED, ACCEPTED,
                    MOVED_PERMANENTLY, SEE_OTHER, PERMANENT_REDIRECT,
                    BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
                    INTERNAL_SERVER_ERROR, NOT_IMPLEMENTED, BAD_GATEWAY);
            for (HttpResponseStatus status : statuses) {
                makeGetRequestAndValidate(serverCtx, status);
                makeGetRequestAndValidate(serverCtx2, status);
                makeGetRequestAndValidate(serverCtx3, status);
            }
        }
    }

    private static void makeGetRequestAndValidate(ServerContext serverCtx, HttpResponseStatus status) throws Exception {
        final StreamingHttpRequest request =
                requester.get(format("http:/%s/%d?param=value#tag", serverCtx.listenAddress(), status.code()));
        requestAndValidate(request, status);
    }

    private static void requestAndValidate(StreamingHttpRequest request,
                                           HttpResponseStatus expectedStatus) throws Exception {
        StreamingHttpResponse response = awaitIndefinitelyNonNull(requester.request(request));
        assertThat(response.status(), is(expectedStatus));
    }

    private static ServerContext startNewLocalServer(final StreamingHttpService httpService,
                                                     final CompositeCloseable closeables) throws Exception {
        return closeables.append(HttpServers.forAddress(new InetSocketAddress(getLoopbackAddress(), 0))
                .ioExecutor(CTX.ioExecutor())
                .listenStreamingAndAwait(httpService));
    }
}
