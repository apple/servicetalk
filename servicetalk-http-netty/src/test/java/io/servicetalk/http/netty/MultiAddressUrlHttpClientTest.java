/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestSingleSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.CREATED;
import static io.servicetalk.http.api.HttpResponseStatus.FORBIDDEN;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.CONNECT_TIMEOUT;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class MultiAddressUrlHttpClientTest {

    private static final String X_REQUESTED_LOCATION = "X-Requested-Location";
    private static final String X_RECEIVED_REQUEST_TARGET = "X-Received-Request-Target";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static CompositeCloseable afterClassCloseables;
    private static StreamingHttpService httpService;
    private static String serverHost;
    private static int serverPort;
    private static String hostHeader;
    private static StreamingHttpClient client;

    private final TestSingleSubscriber<StreamingHttpResponse> subscriber = new TestSingleSubscriber<>();

    @BeforeClass
    public static void beforeClass() throws Exception {
        afterClassCloseables = newCompositeCloseable();

        client = afterClassCloseables.append(HttpClients.forMultiAddressUrl()
                .socketOption(CONNECT_TIMEOUT, 1) // windows default connect timeout is seconds, we want to fail fast.
                .buildStreaming());

        httpService = (ctx, request, factory) -> {
            if (HTTP_1_1.equals(request.version()) && !request.headers().contains(HOST)) {
                return succeeded(factory.badRequest().setHeader(CONTENT_LENGTH, ZERO));
            }

            if (OPTIONS.equals(request.method()) || CONNECT.equals(request.method())) {
                return succeeded(factory.ok().setHeader(CONTENT_LENGTH, ZERO));
            }

            StreamingHttpResponse response;
            try {
                HttpResponseStatus status = HttpResponseStatus.of(parseInt(request.path().substring(1)), "");
                response = factory.newResponse(status);
                final CharSequence locationHeader = request.headers().get(X_REQUESTED_LOCATION);
                if (locationHeader != null) {
                    response.headers().set(LOCATION, locationHeader);
                }
            } catch (Exception e) {
                response = factory.badRequest();
            }
            return succeeded(response.setHeader(CONTENT_LENGTH, ZERO)
                    .setHeader(X_RECEIVED_REQUEST_TARGET, request.requestTarget()));
        };
        final ServerContext serverCtx = startNewLocalServer(httpService, afterClassCloseables);

        final HostAndPort serverHostAndPort = serverHostAndPort(serverCtx);
        serverHost = serverHostAndPort.hostName();
        serverPort = serverHostAndPort.port();
        hostHeader = hostHeader(serverHostAndPort);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        afterClassCloseables.close();
    }

    @Test
    public void requestWithRelativeFormRequestTarget() {
        StreamingHttpRequest request = client.get("/200?param=value");
        // no host header
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.error(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void requestWithRelativeFormRequestTargetAndHostHeader() {
        StreamingHttpRequest request = client.get("/200?param=value");
        request.headers().set(HOST, hostHeader);
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.error(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void requestWithRequestTargetWithoutScheme() {
        StreamingHttpRequest request = client.get(format("%s/200?param=value#tag", hostHeader));
        // no host header
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.error(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/200?param=value#tag", hostHeader));
        request.headers().set(HOST, "invalid.:8080");    // value in the HOST header should be ignored
        requestAndValidate(request, OK, "/200?param=value#tag");
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithoutHostHeader() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/200?param=value#tag", hostHeader));
        requestAndValidate(request, OK, "/200?param=value#tag");
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithoutPath() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s", hostHeader));
        requestAndValidate(request, BAD_REQUEST, "/");
    }

    @Test(expected = ExecutionException.class)
    @Ignore("LoadBalancerReadySubscriber will never complete for a wrong host") // FIXME: remove @Ignore annotation
    public void requestWithAbsoluteFormRequestTargetWithInvalidHost() throws Exception {
        StreamingHttpRequest request = client.get(
                format("http://invalid.:%d/200?param=value#tag", serverPort));
        client.request(request).toFuture().get();
    }

    @Test(expected = ExecutionException.class)
    public void requestWithAbsoluteFormRequestTargetWithWrongPort() throws Exception {
        StreamingHttpRequest request = client.get(
                format("http://%s:%d/200?param=value#tag", serverHost, serverPort + 1));
        client.request(request).toFuture().get();
    }

    @Test
    public void requestWithIncorrectPortInAbsoluteFormRequestTarget() {
        StreamingHttpRequest request = client.get(format("http://%s:-1/200?param=value#tag", serverHost));
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.error(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void requestWithAsteriskFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = client.newRequest(OPTIONS, "*")
                .setHeader(HOST, hostHeader);
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.error(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void requestWithRedirect() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/301", hostHeader))
                .setHeader(X_REQUESTED_LOCATION, format("http://%s/200", hostHeader));  // Location for redirect
        requestAndValidate(request, OK, "/200");
    }

    @Test
    public void requestWithRelativeRedirect() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/301", hostHeader))
                .setHeader(X_REQUESTED_LOCATION, "/200");  // Location for redirect
        requestAndValidate(request, OK, "/200");
    }

    @Test
    public void multipleRequestsToMultipleServers() throws Exception {
        try (CompositeCloseable closeables = newCompositeCloseable()) {
            ServerContext serverCtx2 = startNewLocalServer(httpService, closeables);
            String hostHeader2 = hostHeader(serverHostAndPort(serverCtx2));
            ServerContext serverCtx3 = startNewLocalServer(httpService, closeables);
            String hostHeader3 = hostHeader(serverHostAndPort(serverCtx3));

            List<HttpResponseStatus> statuses = asList(OK, CREATED, ACCEPTED,
                    MOVED_PERMANENTLY, SEE_OTHER, PERMANENT_REDIRECT,
                    BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
                    INTERNAL_SERVER_ERROR, NOT_IMPLEMENTED, BAD_GATEWAY);
            for (HttpResponseStatus status : statuses) {
                makeGetRequestAndValidate(hostHeader, status);
                makeGetRequestAndValidate(hostHeader2, status);
                makeGetRequestAndValidate(hostHeader3, status);
            }
        }
    }

    private static void makeGetRequestAndValidate(final String hostHeader, final HttpResponseStatus status)
            throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/%d?param=value#tag", hostHeader, status.code()));
        requestAndValidate(request, status, format("/%d?param=value#tag", status.code()));
    }

    private static void requestAndValidate(final StreamingHttpRequest request,
                                           final HttpResponseStatus expectedStatus,
                                           final String expectedRequestTarget) throws Exception {
        StreamingHttpResponse response = awaitIndefinitelyNonNull(client.request(request));
        assertThat(response.status(), is(expectedStatus));
        final CharSequence receivedRequestTarget = response.headers().get(X_RECEIVED_REQUEST_TARGET);
        assertThat(receivedRequestTarget, is(notNullValue()));
        assertThat(receivedRequestTarget.toString(), is(expectedRequestTarget));
    }

    private static ServerContext startNewLocalServer(final StreamingHttpService httpService,
                                                     final CompositeCloseable closeables) throws Exception {
        return closeables.append(HttpServers.forAddress(localAddress(0)).listenStreamingAndAwait(httpService));
    }
}
