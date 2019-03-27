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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatus.MULTIPLE_CHOICES;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatus.TEMPORARY_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.USE_PROXY;
import static io.servicetalk.http.api.TestStreamingHttpClient.from;
import static java.lang.Integer.parseUnsignedInt;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedirectingHttpRequesterFilterTest {

    private static final String REQUESTED_STATUS = "Requested-Status";
    private static final String REQUESTED_LOCATION = "Requested-Location";
    private static final int MAX_REDIRECTS = 5;
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final StreamingHttpRequester httpClient = mock(StreamingHttpRequester.class);

    @Before
    public void setUp() {
        when(httpClient.request(any(), any())).thenAnswer(a -> {
            try {
                StreamingHttpRequest request = a.getArgument(1);
                CharSequence statusHeader = request.headers().get(REQUESTED_STATUS);
                HttpResponseStatus status = statusHeader == null ? OK
                        : HttpResponseStatus.of(parseUnsignedInt(statusHeader.toString()), EMPTY_BUFFER);
                StreamingHttpResponse response = reqRespFactory.newResponse(status);
                CharSequence redirectLocation = request.headers().get(REQUESTED_LOCATION);
                response.headers().set(LOCATION, redirectLocation);
                return succeeded(response);
            } catch (Throwable t) {
                return failed(t);
            }
        });
    }

    private StreamingHttpClient newClient(HttpClientFilterFactory customFilter) {
        HttpClientFilterFactory mockResponse = (client, __) ->
                new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return httpClient.request(strategy, request);
                    }
                };

        return from(reqRespFactory, mock(ExecutionContext.class), customFilter.append(mockResponse));
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
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(306, EMPTY_BUFFER), "/new-location");
    }

    @Test
    public void requestWith309Status() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(309, EMPTY_BUFFER), "/new-location");
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
                                       @Nullable final CharSequence requestedLocation) throws Exception {

        StreamingHttpClient redirectingRequester =
                newClient(new RedirectingHttpRequesterFilter(false, maxRedirects));

        StreamingHttpRequest request = redirectingRequester.newRequest(method, "/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(requestedStatus.code()));
        if (requestedLocation != null) {
            request.headers().set(REQUESTED_LOCATION, requestedLocation);
        }

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(requestedStatus, response.status());
        assertEquals(requestedLocation, response.headers().get(LOCATION));
        verify(httpClient).request(any(), any());
        clearInvocations(httpClient);
    }

    @Test
    public void maxRedirectsReached() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any(), any())).thenAnswer(a -> createRedirectResponse(counter.incrementAndGet()));

        final int maxRedirects = MAX_REDIRECTS;
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(false, maxRedirects));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(MOVED_PERMANENTLY, response.status());
        assertEquals("/location-" + (maxRedirects + 1), response.headers().get(LOCATION));
        verify(httpClient, times(maxRedirects + 1)).request(any(), any());
    }

    @Test
    public void requestWithNullResponse() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(succeeded(null));

        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(MOVED_PERMANENTLY.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
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

        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = redirectingRequester.newRequest(method, "/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(requestedStatus.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(2)).request(any(), any());
        clearInvocations(httpClient);
    }

    @Test
    public void multipleFollowUpRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                createRedirectResponse(1),
                createRedirectResponse(2),
                createRedirectResponse(3),
                succeeded(reqRespFactory.ok()));

        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(4)).request(any(), any());
    }

    private static Single<StreamingHttpResponse> createRedirectResponse(final int i) {
        StreamingHttpResponse response = reqRespFactory.newResponse(MOVED_PERMANENTLY);
        response.headers().set(LOCATION, "/location-" + i);
        return succeeded(response);
    }

    @Test
    public void getRequestForRedirectWithAbsoluteFormRequestTarget() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = redirectingRequester.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(2)).request(any(), any());
    }

    @Test
    public void redirectForOnlyRelativeWithAbsoluteRelativeLocation() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(2)).request(any(), any());
    }

    @Test
    public void redirectForOnlyRelativeWithAbsoluteRelativeLocationWithPortDoesntRedirect() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io:80/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(SEE_OTHER, response.status());
        assertThat(response.headers().get(LOCATION), equalTo("http://servicetalk.io:80/new-location"));
        verify(httpClient, times(1)).request(any(), any());
    }

    @Test
    public void redirectForOnlyRelativeWithRelativeLocation() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(2)).request(any(), any());
    }

    @Test
    public void redirectForOnlyRelativeWithAbsoluteNonRelativeLocationDoesntRedirect() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://non-relative.servicetalk.io/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(SEE_OTHER, response.status());
        assertThat(response.headers().get(LOCATION), equalTo("http://non-relative.servicetalk.io/new-location"));
        verify(httpClient, times(1)).request(any(), any());
    }

    @Test
    public void absoluteTargetRedirectForOnlyRelativeWithAbsoluteRelativeLocation() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(LOCATION));
        verify(httpClient, times(2)).request(any(), any());
    }

    @Test
    public void absoluteTargetRedirectForOnlyRelativeWithNonRelativeLocationDoesntRedirect() throws Exception {
        StreamingHttpClient redirectingRequester = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = redirectingRequester.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://non-relative.servicetalk.io/new-location");

        StreamingHttpResponse response = redirectingRequester.request(defaultStrategy(), request).toFuture().get();
        assertNotNull(response);
        assertEquals(SEE_OTHER, response.status());
        assertThat(response.headers().get(LOCATION), equalTo("http://non-relative.servicetalk.io/new-location"));
        verify(httpClient, times(1)).request(any(), any());
    }
}
