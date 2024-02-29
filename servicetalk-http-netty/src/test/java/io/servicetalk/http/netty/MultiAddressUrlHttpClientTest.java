/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.RedirectConfigBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers.globalARecordsDnsServiceDiscoverer;
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
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiAddressUrlHttpClientTest {

    private static final String ID = MultiAddressUrlHttpClientTest.class.getSimpleName();
    private static final String INVALID_HOSTNAME = "invalid.";
    private static final String X_REQUESTED_LOCATION = "X-Requested-Location";
    private static final String X_RECEIVED_REQUEST_TARGET = "X-Received-Request-Target";
    private static final String X_RECEIVED_HOST_HEADER = "X-Received-Host-Header";

    private static CompositeCloseable afterClassCloseables;
    private static StreamingHttpService httpService;
    private static String serverHost;
    private static int serverPort;
    private static String hostHeader;
    private static StreamingHttpClient client;

    private final TestSingleSubscriber<StreamingHttpResponse> subscriber = new TestSingleSubscriber<>();

    @BeforeAll
    static void beforeClass() throws Exception {
        afterClassCloseables = newCompositeCloseable();

        client = afterClassCloseables.append(HttpClients.forMultiAddressUrl(ID)
                .followRedirects(new RedirectConfigBuilder().allowNonRelativeRedirects(true).build())
                .initializer((scheme, address, builder) -> builder.serviceDiscoverer(sdThatSupportsInvalidHostname()))
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
            if (request.headers().contains(HOST)) {
                response.setHeader(X_RECEIVED_HOST_HEADER, request.headers().get(HOST));
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

    @AfterAll
    static void afterClass() throws Exception {
        afterClassCloseables.close();
    }

    private static ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    sdThatSupportsInvalidHostname() {
        return new ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>() {
            @Override
            public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                    final HostAndPort hostAndPort) {
                if (INVALID_HOSTNAME.equalsIgnoreCase(hostAndPort.hostName())) {
                    return Publisher.failed(new UnknownHostException(
                            "Special domain name \"" + INVALID_HOSTNAME + "\" always returns NXDOMAIN"));
                }
                return globalARecordsDnsServiceDiscoverer().discover(hostAndPort);
            }

            @Override
            public Completable onClose() {
                return completed();
            }

            @Override
            public Completable closeAsync() {
                return completed();
            }
        };
    }

    @Test
    void requestWithRelativeFormRequestTarget() {
        StreamingHttpRequest request = client.get("/200?param=value");
        // no host header
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(MalformedURLException.class)));
    }

    @Test
    void requestWithRelativeFormRequestTargetAndHostHeader() {
        StreamingHttpRequest request = client.get("/200?param=value");
        request.headers().set(HOST, hostHeader);
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(MalformedURLException.class)));
    }

    @Test
    void requestWithRequestTargetWithoutScheme() {
        StreamingHttpRequest request = client.get(format("%s/200?param=value#tag", hostHeader));
        // no host header
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(MalformedURLException.class)));
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithHostHeader() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/200?param=value#tag", hostHeader));
        // value in the HOST header should be ignored:
        request.headers().set(HOST, format("%s:%d", INVALID_HOSTNAME, 8080));
        requestAndValidate(client, request, OK, "/200?param=value#tag", INVALID_HOSTNAME + ":" + 8080);
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithoutHostHeader() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/200?param=value#tag", hostHeader));
        requestAndValidate(client, request, OK, "/200?param=value#tag", hostHeader);
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithoutPath() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s", hostHeader));
        requestAndValidate(client, request, BAD_REQUEST, "/", hostHeader);
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithoutPathWithParams() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s?param=value", hostHeader));
        requestAndValidate(client, request, BAD_REQUEST, "/?param=value", hostHeader);
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithInvalidHost() {
        // Verify it fails multiple times:
        requestWithInvalidHost();
        requestWithInvalidHost();
    }

    private void requestWithInvalidHost() {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> {
            client.request(client.get(format("http://%s:%d/200?param=value#tag", INVALID_HOSTNAME, serverPort)))
                    .toFuture().get();
        });
        assertThat(ee.getCause(), is(instanceOf(UnknownHostException.class)));
    }

    @Test
    void requestWithAbsoluteFormRequestTargetWithWrongPort() {
        StreamingHttpRequest request = client.get(
                format("http://%s:%d/200?param=value#tag", serverHost, serverPort + 1));
        assertThrows(ExecutionException.class, () -> client.request(request).toFuture().get());
    }

    @Test
    void requestWithIncorrectPortInAbsoluteFormRequestTarget() {
        StreamingHttpRequest request = client.get(format("http://%s:-1/200?param=value#tag", serverHost));
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    void requestWithAsteriskFormRequestTargetWithHostHeader() {
        StreamingHttpRequest request = client.newRequest(OPTIONS, "*")
                .setHeader(HOST, hostHeader);
        toSource(client.request(request)).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(MalformedURLException.class)));
    }

    @Test
    void requestWithRedirect() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/301", hostHeader))
                .setHeader(X_REQUESTED_LOCATION, format("http://%s/200", hostHeader));  // Location for redirect
        requestAndValidate(client, request, OK, "/200", hostHeader);
    }

    @Test
    void requestWithRelativeRedirect() throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/301", hostHeader))
                .setHeader(X_REQUESTED_LOCATION, "/200");  // Location for redirect
        requestAndValidate(client, request, OK, "/200", hostHeader);
    }

    @Test
    void requestWithCustomDefaultHttpPort() throws Exception {
        try (StreamingHttpClient client = HttpClients
                .forMultiAddressUrl(ID).defaultHttpPort(serverPort).buildStreaming()) {
            StreamingHttpRequest request = client.get(format("http://%s/200", serverHost));
            requestAndValidate(client, request, OK, "/200", serverHost);
        }
    }

    @Test
    void requestWithExplicitPortTakesPrecedenceOverCustomDefaultHttpPort() throws Exception {
        int illegalPort = availablePort();
        try (StreamingHttpClient client = HttpClients
                .forMultiAddressUrl(ID).defaultHttpPort(illegalPort).buildStreaming()) {
            StreamingHttpRequest request = client.get(format("http://%s:%d/200", serverHost, serverPort));
            // the host header should exactly match the authority provided.
            requestAndValidate(client, request, OK, "/200", serverHost + ":" + serverPort);
        }
    }

    @Test
    void multipleRequestsToMultipleServers() throws Exception {
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
                makeGetRequestAndValidate(client, hostHeader, status);
                makeGetRequestAndValidate(client, hostHeader2, status);
                makeGetRequestAndValidate(client, hostHeader3, status);
            }
        }
    }

    private static void makeGetRequestAndValidate(final StreamingHttpClient client, final String hostHeader,
                                                  final HttpResponseStatus status) throws Exception {
        StreamingHttpRequest request = client.get(format("http://%s/%d?param=value#tag", hostHeader, status.code()));
        requestAndValidate(client, request, status, format("/%d?param=value#tag", status.code()), hostHeader);
    }

    private static void requestAndValidate(final StreamingHttpClient client, final StreamingHttpRequest request,
                                           final HttpResponseStatus expectedStatus, final String expectedRequestTarget,
                                           final String expectedHostHeader) throws Exception {
        StreamingHttpResponse response = awaitIndefinitelyNonNull(client.request(request));
        // Our request should be modified with the host header at this point.
        assertThat(response.status(), is(expectedStatus));
        final CharSequence receivedRequestTarget = response.headers().get(X_RECEIVED_REQUEST_TARGET);
        assertThat(receivedRequestTarget, is(notNullValue()));
        assertThat(receivedRequestTarget.toString(), is(expectedRequestTarget));
        final CharSequence receivedHostHeader = response.headers().get(X_RECEIVED_HOST_HEADER);
            assertThat(receivedHostHeader.toString(), is(expectedHostHeader));
    }

    private static ServerContext startNewLocalServer(final StreamingHttpService httpService,
                                                     final CompositeCloseable closeables) throws Exception {
        return closeables.append(HttpServers.forAddress(localAddress(0)).listenStreamingAndAwait(httpService));
    }

    private static int availablePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        int port = s.getLocalPort();
        s.close();
        return port;
    }
}
