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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectingHttpRequesterFilterTest {

    private static final String REQUESTED_STATUS = "Requested-Status";
    private static final String REQUESTED_LOCATION = "Requested-Location";
    private static final String RECEIVED_SCHEME = "Received-Scheme";
    private static final int MAX_REDIRECTS = 5;
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);


    private final StreamingHttpRequester httpClient = mock(StreamingHttpRequester.class);

    @BeforeEach
    void setUp() {
        when(httpClient.request(any(), any())).thenAnswer(a -> {
            try {
                StreamingHttpRequest request = a.getArgument(1);
                CharSequence statusHeader = request.headers().get(REQUESTED_STATUS);
                HttpResponseStatus status = statusHeader == null ? OK
                        : HttpResponseStatus.of(parseUnsignedInt(statusHeader.toString()), "");
                StreamingHttpResponse response = reqRespFactory.newResponse(status);
                CharSequence redirectLocation = request.headers().get(REQUESTED_LOCATION);
                if (redirectLocation != null) {
                    response.headers().set(LOCATION, redirectLocation);
                }
                final String scheme = request.scheme();
                if (scheme != null) {
                    response.setHeader(RECEIVED_SCHEME, scheme);
                }
                return succeeded(response);
            } catch (Throwable t) {
                return failed(t);
            }
        });
    }

    private StreamingHttpClient newClient(StreamingHttpClientFilterFactory customFilter) {
        StreamingHttpClientFilterFactory mockResponse = client -> new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return httpClient.request(strategy, request);
                    }
                };

        return from(reqRespFactory, mock(HttpExecutionContext.class),
                appendClientFilterFactory(customFilter, mockResponse));
    }

    @Test
    void requestWithNegativeMaxRedirects() throws Exception {
        testNoRedirectWasDone(-1, GET, MOVED_PERMANENTLY, "/new-location");
    }

    @Test
    void requestWithZeroMaxRedirects() throws Exception {
        testNoRedirectWasDone(0, GET, MOVED_PERMANENTLY, "/new-location");
    }

    @Test
    void requestWithOkStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, OK, "/new-location");
    }

    @Test
    void requestWithNotModifiedStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, NOT_MODIFIED, "/new-location");
    }

    @Test
    void requestWithUseProxyStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, USE_PROXY, "/new-location");
    }

    @Test
    void requestWith306Status() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(306, ""), "/new-location");
    }

    @Test
    void requestWith309Status() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(309, ""), "/new-location");
    }

    @Test
    void requestWithBadRequestStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, BAD_REQUEST, "/new-location");
    }

    @Test
    void requestWithInternalServerErrorStatus() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, INTERNAL_SERVER_ERROR, "/new-location");
    }

    @Test
    void requestWithEmptyLocation() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, MOVED_PERMANENTLY, null);
    }

    @Test
    void request307or308WithEmptyLocation() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, TEMPORARY_REDIRECT, null);
        testNoRedirectWasDone(MAX_REDIRECTS, GET, PERMANENT_REDIRECT, null);
    }

    @Test
    void nonGetOrHeadRequestWithTemporaryRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, POST, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PUT, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PATCH, TEMPORARY_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, DELETE, TEMPORARY_REDIRECT, "/new-location");
    }

    @Test
    void nonGetOrHeadRequestWithPermanentRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, POST, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PUT, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, PATCH, PERMANENT_REDIRECT, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, DELETE, PERMANENT_REDIRECT, "/new-location");
    }

    @Test
    void traceOptionsRequestsDoesNotRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, TRACE, SEE_OTHER, "/new-location");
        testNoRedirectWasDone(MAX_REDIRECTS, OPTIONS, SEE_OTHER, "/new-location");
    }

    @Test
    void connectRequestsDoesNotRedirect() {
        try {
            testNoRedirectWasDone(MAX_REDIRECTS, CONNECT, SEE_OTHER, "/new-location");
            Assertions.fail();
        } catch (Exception e) {
            assertThat(e, instanceOf(ExecutionException.class));
            assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
        }
    }

    private void testNoRedirectWasDone(final int maxRedirects,
                                       final HttpRequestMethod method,
                                       final HttpResponseStatus requestedStatus,
                                       @Nullable final CharSequence requestedLocation) throws Exception {

        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(false, maxRedirects));

        StreamingHttpRequest request = client.newRequest(method, "/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(requestedStatus.code()));
        if (requestedLocation != null) {
            request.headers().set(REQUESTED_LOCATION, requestedLocation);
        }

        verifyResponse(client, request, requestedStatus, requestedLocation, 1);
        clearInvocations(httpClient);
    }

    @Test
    void maxRedirectsReached() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any(), any())).thenAnswer(a -> createRedirectResponse(counter.incrementAndGet()));

        final int maxRedirects = MAX_REDIRECTS;
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(maxRedirects));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");

        verifyResponse(client, request, MOVED_PERMANENTLY, "/location-" + (maxRedirects + 1), maxRedirects + 1);
    }

    @Test
    void requestWithNullResponse() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(succeeded(null));

        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(MOVED_PERMANENTLY.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        StreamingHttpResponse response = client.request(defaultStrategy(), request).toFuture().get();
        assertNull(response);
    }

    @Test
    void requestForRedirectWithMultipleChoices() throws Exception {
        testRequestForRedirect(GET, MULTIPLE_CHOICES);
        testRequestForRedirect(HEAD, MULTIPLE_CHOICES);
    }

    @Test
    void requestForRedirectWithMovedPermanently() throws Exception {
        testRequestForRedirect(GET, MOVED_PERMANENTLY);
        testRequestForRedirect(HEAD, MOVED_PERMANENTLY);
    }

    @Test
    void requestForRedirectWithFound() throws Exception {
        testRequestForRedirect(GET, FOUND);
        testRequestForRedirect(HEAD, FOUND);
    }

    @Test
    void requestForRedirectWithSeeOther() throws Exception {
        testRequestForRedirect(GET, SEE_OTHER);
        testRequestForRedirect(HEAD, SEE_OTHER);
    }

    @Test
    void requestForRedirectWithTemporaryRedirect() throws Exception {
        testRequestForRedirect(GET, TEMPORARY_REDIRECT);
        testRequestForRedirect(HEAD, TEMPORARY_REDIRECT);
    }

    @Test
    void requestForRedirectWithPermanentRedirect() throws Exception {
        testRequestForRedirect(GET, PERMANENT_REDIRECT);
        testRequestForRedirect(HEAD, PERMANENT_REDIRECT);
    }

    private void testRequestForRedirect(final HttpRequestMethod method,
                                        final HttpResponseStatus requestedStatus) throws Exception {

        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.newRequest(method, "/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(requestedStatus.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        verifyRedirects(client, request);
        clearInvocations(httpClient);
    }

    @Test
    void multipleFollowUpRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                createRedirectResponse(1),
                createRedirectResponse(2),
                createRedirectResponse(3),
                succeeded(reqRespFactory.ok()));

        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter());

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");

        verifyRedirects(client, request, 4);
    }

    private static Single<StreamingHttpResponse> createRedirectResponse(final int i) {
        StreamingHttpResponse response = reqRespFactory.newResponse(MOVED_PERMANENTLY);
        response.headers().set(LOCATION, "/location-" + i);
        return succeeded(response);
    }

    @Test
    void getRequestForRedirectWithAbsoluteFormRequestTarget() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter());

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        verifyRedirects(client, request);
    }

    @Test
    void redirectForOnlyRelativeWithAbsoluteRelativeLocation() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter());

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io/new-location");

        verifyRedirects(client, request);
    }

    @Test
    void redirectForOnlyRelativeWithAbsoluteRelativeLocationWithPortDoesNotRedirect() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io:80/new-location");

        verifyDoesNotRedirect(client, request, "http://servicetalk.io:80/new-location");
    }

    @Test
    void redirectForOnlyRelativeWithRelativeLocation() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        verifyRedirects(client, request);
    }

    @Test
    void redirectForNotOnlyRelativeWithRelativeLocation() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        verifyRedirects(client, request);
    }

    @Test
    void redirectFromHttpToHttps() throws Exception {
        crossSchemeRedirects("http", "https");
    }

    @Test
    void redirectFromHttpsToHttp() throws Exception {
        crossSchemeRedirects("https", "http");
    }

    private void crossSchemeRedirects(String fromScheme, String toScheme) throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = client.get(fromScheme + "://servicetalk.io/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, toScheme + "://servicetalk.io//new-location");

        StreamingHttpResponse response = verifyRedirects(client, request);
        assertThat(response.headers().get(RECEIVED_SCHEME), is(toScheme));
    }

    @Test
    void redirectForOnlyRelativeWithAbsoluteNonRelativeLocationDoesNotRedirect() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(HOST, "servicetalk.io");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://non-relative.servicetalk.io/new-location");

        verifyDoesNotRedirect(client, request, "http://non-relative.servicetalk.io/new-location");
    }

    @Test
    void doesNotRedirectIfRemoteHostIsUnknown() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "/new-location");

        verifyDoesNotRedirect(client, request, "/new-location");
    }

    @Test
    void absoluteTargetRedirectForOnlyRelativeWithAbsoluteRelativeLocation() throws Exception {
        absoluteTargetRedirectWithAbsoluteRelativeLocation(true);
    }

    @Test
    void absoluteTargetRedirectForNotOnlyRelativeWithAbsoluteRelativeLocation() throws Exception {
        absoluteTargetRedirectWithAbsoluteRelativeLocation(false);
    }

    private void absoluteTargetRedirectWithAbsoluteRelativeLocation(final boolean onlyRelative) throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(onlyRelative));

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io/new-location");

        verifyRedirects(client, request);
    }

    @Test
    void absoluteTargetRedirectForOnlyRelativeWithAbsoluteRelativeLocationWithoutPath() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://servicetalk.io");

        verifyRedirects(client, request);
    }

    @Test
    void absoluteTargetRedirectForOnlyRelativeWithNonRelativeLocationDoesNotRedirect() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(true));

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://non-relative.servicetalk.io/new-location");

        verifyDoesNotRedirect(client, request, "http://non-relative.servicetalk.io/new-location");
    }

    @Test
    void absoluteTargetRedirectForNotOnlyRelativeWithNonRelativeLocationRedirect() throws Exception {
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter(false));

        StreamingHttpRequest request = client.get("http://servicetalk.io/path");
        request.headers().set(REQUESTED_STATUS, String.valueOf(SEE_OTHER.code()));
        request.headers().set(REQUESTED_LOCATION, "http://non-relative.servicetalk.io/new-location");

        verifyRedirects(client, request);
    }

    private StreamingHttpResponse verifyRedirects(StreamingHttpClient client,
                                                  StreamingHttpRequest request) throws Exception {
        return verifyRedirects(client, request, 2);
    }

    private StreamingHttpResponse verifyRedirects(StreamingHttpClient client, StreamingHttpRequest request,
                                                  int numberOfInvocations) throws Exception {
        return verifyResponse(client, request, OK, null, numberOfInvocations);
    }

    private void verifyDoesNotRedirect(StreamingHttpClient client, StreamingHttpRequest request,
                                       CharSequence expectedLocation) throws Exception {
        verifyResponse(client, request, SEE_OTHER, expectedLocation, 1);
    }

    private StreamingHttpResponse verifyResponse(StreamingHttpClient client, StreamingHttpRequest request,
                                                 HttpResponseStatus expectedStatus,
                                                 @Nullable CharSequence expectedLocation,
                                                 int numberOfInvocations) throws Exception {
        StreamingHttpResponse response = client.request(defaultStrategy(), request).toFuture().get();
        assertThat(response, is(notNullValue()));
        assertThat(response.status(), is(expectedStatus));
        assertThat(response.headers().get(LOCATION), is(expectedLocation));
        verify(httpClient, times(numberOfInvocations)).request(any(), any());
        return response;
    }
}
