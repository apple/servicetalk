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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.DefaultGroupKey;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpClientGroups.newHttpClientGroup;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpRequestMethods.DELETE;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpRequestMethods.PATCH;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatuses.MULTIPLE_CHOICES;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatuses.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatuses.TEMPORARY_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatuses.USE_PROXY;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static java.lang.Integer.parseUnsignedInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedirectingHttpClientGroupTest {

    private static final String REQUESTED_STATUS = "Requested-Status";
    private static final String REQUESTED_LOCATION = "Requested-Location";
    private static final int MAX_REDIRECTS = 5;

    private static final ExecutionContext executionContext = mock(ExecutionContext.class);

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @SuppressWarnings("unchecked")
    private final HttpClient httpClient = mock(HttpClient.class);

    private HttpClientGroup<String> clientGroup;

    @Before
    public void setUp() {
        when(httpClient.request(any())).thenAnswer(a -> {
            try {
                HttpRequest<String> request = a.getArgument(0);
                CharSequence statusHeader = request.getHeaders().get(REQUESTED_STATUS);
                HttpResponseStatus status = statusHeader == null ? OK
                        : getResponseStatus(parseUnsignedInt(statusHeader.toString()), EMPTY_BUFFER);
                HttpResponse<String> response = newResponse(status);
                CharSequence redirectLocation = request.getHeaders().get(REQUESTED_LOCATION);
                response.getHeaders().set(LOCATION, redirectLocation);
                return success(response);
            } catch (Throwable t) {
                return error(t);
            }
        });
        when(httpClient.closeAsync()).thenReturn(completed());
        clientGroup = newHttpClientGroup((groupKey, metaData) -> httpClient);
    }

    @After
    public void tearDown() throws Exception {
        awaitIndefinitely(clientGroup.closeAsync());
    }

    @Test(expected = NullPointerException.class)
    public void createWithoutClientGroup() {
        new RedirectingHttpClientGroup<>(null, request -> null, executionContext);
    }

    @Test(expected = NullPointerException.class)
    public void createWithoutRequestToGroupKey() {
        new RedirectingHttpClientGroup<>(clientGroup, null, executionContext);
    }

    @Test(expected = NullPointerException.class)
    public void createWithoutExecutionContext() {
        new RedirectingHttpClientGroup<>(clientGroup, request -> null, null);
    }

    @Test
    public void requestWithNegativeMaxRedirects() throws Exception {
        testNoRedirectWasDone(-1, GET, MOVED_PERMANENTLY, "/new-location");
    }

    @Test
    public void requestWithZeroMaxRedirects() throws Exception {
        testNoRedirectWasDone(0, GET, MOVED_PERMANENTLY, "/new-location");
    }

    @Test
    public void requestWithOkStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, OK, "/new-location");
    }

    @Test
    public void requestWithNotModifiedStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, NOT_MODIFIED, "/new-location");
    }

    @Test
    public void requestWithUseProxyStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, USE_PROXY, "/new-location");
    }

    @Test
    public void requestWith306Status() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, getResponseStatus(306, EMPTY_BUFFER), "/new-location");
    }

    @Test
    public void requestWith309Status() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, getResponseStatus(309, EMPTY_BUFFER), "/new-location");
    }

    @Test
    public void requestWithBadRequestStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, BAD_REQUEST, "/new-location");
    }

    @Test
    public void requestWithInternalServerErrorStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, INTERNAL_SERVER_ERROR, "/new-location");
    }

    @Test
    public void requestWithEmptyLocation() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, MOVED_PERMANENTLY, null);
    }

    @Test
    public void request307or308WithEmptyLocation() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, TEMPORARY_REDIRECT, null);
        testNoRedirectWasDone(MAX_REDIRECTS, GET, PERMANENT_REDIRECT, null);
    }

    @Test
    public void nonGetOrHeadRequestWithTemporaryRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, POST, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PUT, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PATCH, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, DELETE, TEMPORARY_REDIRECT, "/new-location");
    }

    @Test
    public void nonGetOrHeadRequestWithPermanentRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, POST, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PUT, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PATCH, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, DELETE, PERMANENT_REDIRECT, "/new-location");
    }

    private void testNoRedirectWasDone(final int maxRedirects,
                                       final HttpRequestMethod method,
                                       final HttpResponseStatus requestedStatus,
                                       final CharSequence requestedLocation) throws Exception {

        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext, maxRedirects)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(method, "/path");
        request.getHeaders().set(HOST, "servicetalk.io");
        request.getHeaders().set(REQUESTED_STATUS, String.valueOf(requestedStatus.getCode()));
        request.getHeaders().set(REQUESTED_LOCATION, requestedLocation);

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNotNull(response);
        assertEquals(requestedStatus.getCode(), response.getStatus().getCode());
        assertEquals(requestedLocation, response.getHeaders().get(LOCATION));
        verify(httpClient).request(any());
        clearInvocations(httpClient);
    }

    @Test
    public void maxRedirectsReached() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any())).thenAnswer(a -> createRedirectResponse(counter.incrementAndGet()));

        final int maxRedirects = MAX_REDIRECTS;
        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext, maxRedirects)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(HOST, "servicetalk.io");

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNotNull(response);
        assertEquals(MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/location-" + (maxRedirects + 1), response.getHeaders().get(LOCATION));
        verify(httpClient, times(maxRedirects + 1)).request(any());
    }

    @Test
    public void requestWithNullResponse() throws Exception {
        when(httpClient.request(any())).thenReturn(success(null));

        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(HOST, "servicetalk.io");
        request.getHeaders().set(REQUESTED_STATUS, String.valueOf(MOVED_PERMANENTLY.getCode()));
        request.getHeaders().set(REQUESTED_LOCATION, "/new-location");

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNull(response);
    }

    @Test
    public void requestForRedirectWithMultipleChoices() throws Exception {
        testRequestForRedirect(GET, MULTIPLE_CHOICES);
        testRequestForRedirect(HEAD, MULTIPLE_CHOICES);
    }

    @Test
    public void requestForRedirectWithMovedPermanently() throws Exception {
        testRequestForRedirect(GET, MOVED_PERMANENTLY);
        testRequestForRedirect(HEAD, MOVED_PERMANENTLY);
    }

    @Test
    public void requestForRedirectWithFound() throws Exception {
        testRequestForRedirect(GET, FOUND);
        testRequestForRedirect(HEAD, FOUND);
    }

    @Test
    public void requestForRedirectWithSeeOther() throws Exception {
        testRequestForRedirect(GET, SEE_OTHER);
        testRequestForRedirect(HEAD, SEE_OTHER);
    }

    @Test
    public void requestForRedirectWithTemporaryRedirect() throws Exception {
        testRequestForRedirect(GET, TEMPORARY_REDIRECT);
        testRequestForRedirect(HEAD, TEMPORARY_REDIRECT);
    }

    @Test
    public void requestForRedirectWithPermanentRedirect() throws Exception {
        testRequestForRedirect(GET, PERMANENT_REDIRECT);
        testRequestForRedirect(HEAD, PERMANENT_REDIRECT);
    }

    private void testRequestForRedirect(final HttpRequestMethod method,
                                        final HttpResponseStatus requestedStatus) throws Exception {

        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(method, "/path");
        request.getHeaders().set(HOST, "servicetalk.io");
        request.getHeaders().set(REQUESTED_STATUS, String.valueOf(requestedStatus.getCode()));
        request.getHeaders().set(REQUESTED_LOCATION, "/new-location");

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertNull(response.getHeaders().get(LOCATION));
        verify(httpClient, times(2)).request(any());
        clearInvocations(httpClient);
    }

    @Test
    public void multipleFollowUpRedirects() throws Exception {
        when(httpClient.request(any())).thenReturn(
                createRedirectResponse(1),
                createRedirectResponse(2),
                createRedirectResponse(3),
                success(newResponse(OK)));

        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(HOST, "servicetalk.io");

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertNull(response.getHeaders().get(LOCATION));
        verify(httpClient, times(4)).request(any());
    }

    private static <O> Single<HttpResponse<O>> createRedirectResponse(final int i) {
        HttpResponse<O> response = newResponse(MOVED_PERMANENTLY);
        response.getHeaders().set(LOCATION, "/location-" + i);
        return success(response);
    }

    @Test
    public void getRequestForRedirectWithAbsoluteFormRequestTarget() throws Exception {
        HttpRequester redirectingRequester = new RedirectingHttpClientGroup<>(clientGroup,
                RedirectingHttpClientGroupTest::createGroupKey, executionContext)
                .asRequester(RedirectingHttpClientGroupTest::createGroupKey, executionContext);

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "http://servicetalk.io/path");
        request.getHeaders().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.getCode()));
        request.getHeaders().set(REQUESTED_LOCATION, "/new-location");

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(redirectingRequester.request(request));
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertNull(response.getHeaders().get(LOCATION));
        verify(httpClient, times(2)).request(any());
    }

    private static <I> GroupKey<String> createGroupKey(HttpRequest<I> request) {
        final String host = request.getEffectiveHost();
        if (host == null) {
            throw new IllegalArgumentException("HttpRequest does not contain information about target server address");
        }
        return new DefaultGroupKey<>(host, executionContext);
    }
}
