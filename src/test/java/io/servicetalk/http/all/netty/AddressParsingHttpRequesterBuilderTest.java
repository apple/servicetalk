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
package io.servicetalk.http.all.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.AggregatedHttpRequester;
import io.servicetalk.http.api.BlockingAggregatedHttpRequester;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatuses;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
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

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpRequestMethods.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpRequests.newRequest;
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
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.api.HttpService.fromAsync;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AddressParsingHttpRequesterBuilderTest {

    private static final String HOSTNAME = "localhost";
    private static final String X_REQUESTED_LOCATION = "X-Requested-Location";

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final MockedSingleListenerRule<HttpResponse<HttpPayloadChunk>> listener = new MockedSingleListenerRule<>();

    private static final CompositeCloseable afterClassCloseables = newCompositeCloseable();
    private static HttpService httpService;
    private static ServerContext serverCtx;
    private static int serverPort;
    private static String hostHeader;
    private static HttpRequester requester;

    @BeforeClass
    public static void beforeClass() throws Exception {
        requester = afterClassCloseables.merge(new AddressParsingHttpRequesterBuilder().build(CTX));

        httpService = fromAsync((ctx, request) -> {
            if (request.getMethod() == OPTIONS || request.getMethod() == CONNECT) {
                return success(newResponse(OK));
            }
            try {
                HttpResponseStatuses status = HttpResponseStatuses.valueOf(request.getPath().substring(1));
                final HttpResponse<HttpPayloadChunk> response = newResponse(status);
                final CharSequence locationHeader = request.getHeaders().get(X_REQUESTED_LOCATION);
                if (locationHeader != null) {
                    response.getHeaders().set(LOCATION, locationHeader);
                }
                return success(response);
            } catch (Exception e) {
                return success(newResponse(BAD_REQUEST));
            }
        });
        serverCtx = afterClassCloseables.merge(startNewLocalServer(httpService));
        serverPort = ((InetSocketAddress) serverCtx.getListenAddress()).getPort();
        hostHeader = HOSTNAME + ':' + serverPort;
    }

    @AfterClass
    public static void afterClass() throws Exception {
        afterClassCloseables.close();
    }

    @Test
    public void buildWithDefaults() throws Exception {
        HttpRequester newRequester = new AddressParsingHttpRequesterBuilder()
                .build(CTX);
        assertNotNull(newRequester);
        awaitIndefinitely(newRequester.closeAsync());
    }

    @Test
    public void buildAggregatedWithDefaults() throws Exception {
        AggregatedHttpRequester newAggregatedRequester = new AddressParsingHttpRequesterBuilder()
                .buildAggregated(CTX);
        assertNotNull(newAggregatedRequester);
        awaitIndefinitely(newAggregatedRequester.closeAsync());
    }

    @Test
    public void buildBlockingWithDefaults() throws Exception {
        BlockingHttpRequester newBlockingRequester = new AddressParsingHttpRequesterBuilder()
                .buildBlocking(CTX);
        assertNotNull(newBlockingRequester);
        newBlockingRequester.close();
    }

    @Test
    public void buildBlockingAggregatedWithDefaults() throws Exception {
        BlockingAggregatedHttpRequester newBlockingAggregatedRequester = new AddressParsingHttpRequesterBuilder()
                .buildBlockingAggregated(CTX);
        assertNotNull(newBlockingAggregatedRequester);
        newBlockingAggregatedRequester.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildWithProvidedServiceDiscoverer() throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress> mockedServiceDiscoverer = mock(ServiceDiscoverer.class);

        HttpRequester newRequester = new AddressParsingHttpRequesterBuilder()
                .setServiceDiscoverer(mockedServiceDiscoverer)
                .build(CTX);
        awaitIndefinitely(newRequester.closeAsync());
        verify(mockedServiceDiscoverer, never()).closeAsync();
    }

    // Tests for AddressParsingHttpRequester:

    @Test
    public void requestWithRelativeFormRequestTargetWithHostHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/OK?param=value");
        request.getHeaders().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithRelativeFormRequestTargetWithoutHostHeader() {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/OK?param=value");
        // no host header set
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test(expected = ExecutionException.class)
    @Ignore("LoadBalancerReadySubscriber will never complete for a wrong host") // FIXME: remove @Ignore annotation
    public void requestWithRelativeFormRequestTargetWithInvalidHostInHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/OK?param=value");
        request.getHeaders().set(HOST, "invalid.:" + serverPort);
        awaitIndefinitely(requester.request(request));
    }

    @Test(expected = ExecutionException.class)
    public void requestWithRelativeFormRequestTargetWithWrongPortInHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/OK?param=value");
        request.getHeaders().set(HOST, format("%s:%d", HOSTNAME, serverPort + 1));
        awaitIndefinitely(requester.request(request));
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithHostHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request =
                newRequest(GET, format("http://%s/OK?param=value#tag", hostHeader));
        request.getHeaders().set(HOST, "wrong-server.com:8080");    // value in HOST header should be ignored
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAbsoluteFormRequestTargetWithoutHostHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request =
                newRequest(GET, format("http://%s/OK?param=value#tag", hostHeader));
        requestAndValidate(request, OK);
    }

    @Test(expected = ExecutionException.class)
    @Ignore("LoadBalancerReadySubscriber will never complete for a wrong host") // FIXME: remove @Ignore annotation
    public void requestWithAbsoluteFormRequestTargetWithInvalidHost() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET,
                format("http://invalid.:%d/OK?param=value#tag", serverPort));
        awaitIndefinitely(requester.request(request));
    }

    @Test(expected = ExecutionException.class)
    public void requestWithAbsoluteFormRequestTargetWithWrongPort() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET,
                format("http://%s:%d/OK?param=value#tag", HOSTNAME, serverPort + 1));
        awaitIndefinitely(requester.request(request));
    }

    @Test
    public void requestWithIncorrectPortInAbsoluteFormRequestTarget() {
        HttpRequest<HttpPayloadChunk> request =
                newRequest(GET, format("http://%s:-1/OK?param=value#tag", HOSTNAME));
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void requestWithAuthorityFormRequestTargetWithHostHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(CONNECT, hostHeader);
        request.getHeaders().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAsteriskFormRequestTargetWithHostHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(OPTIONS, "*");
        request.getHeaders().set(HOST, hostHeader);
        requestAndValidate(request, OK);
    }

    @Test
    public void requestWithAsteriskFormRequestTargetWithoutHostHeader() {
        HttpRequest<HttpPayloadChunk> request = newRequest(OPTIONS, "*");
        // no host header set
        listener.listen(requester.request(request))
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void requestWithRedirect() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/MOVED_PERMANENTLY");
        request.getHeaders().set(HOST, hostHeader);
        request.getHeaders().set(X_REQUESTED_LOCATION, "/OK");  // Location for redirect
        requestAndValidate(request, OK);
    }

    @Test
    public void multipleRequestsToMultipleServers() throws Exception {
        try (CompositeCloseable closeables = newCompositeCloseable()) {
            ServerContext serverCtx2 = closeables.merge(startNewLocalServer(httpService));
            ServerContext serverCtx3 = closeables.merge(startNewLocalServer(httpService));

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
        final HttpRequest<HttpPayloadChunk> request =
                newRequest(GET, format("http:/%s/%s?param=value#tag", serverCtx.getListenAddress(), status));
        requestAndValidate(request, status);
    }

    private static void requestAndValidate(HttpRequest<HttpPayloadChunk> request,
                                           HttpResponseStatus expectedStatus) throws Exception {
        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(requester.request(request));
        assertThat(response.getStatus(), is(expectedStatus));
    }

    private static ServerContext startNewLocalServer(final HttpService httpService) throws Exception {
        return awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .start(CTX, new InetSocketAddress(HOSTNAME, 0), httpService));
    }
}
