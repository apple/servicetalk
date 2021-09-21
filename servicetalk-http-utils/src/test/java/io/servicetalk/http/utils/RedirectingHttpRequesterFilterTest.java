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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
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
import static io.servicetalk.http.api.HttpResponseStatus.CONTINUE;
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
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectingHttpRequesterFilterTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR = ExecutorExtension.withCachedExecutor();

    private static final String CUSTOM_HEADER = "custom-header";
    private static final String CUSTOM_TRAILER = "custom-trailer";
    private static final String CUSTOM_VALUE = "custom-value";
    private static final String REQUEST_PAYLOAD = "request-payload";
    private static final int MAX_REDIRECTS = 5;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    private final StreamingHttpRequester httpClient = mock(StreamingHttpRequester.class);
    private final Queue<TestPublisher<Buffer>> redirectResponsePayloads = new LinkedBlockingDeque<>();

    private static StreamingHttpRequest newRequest(StreamingHttpRequestFactory reqFactory, HttpRequestMethod method) {
        return reqFactory.newRequest(method, "/path")
                .setHeader(HOST, "servicetalk.io")
                .setHeader(CUSTOM_HEADER, CUSTOM_VALUE)
                .setHeader(TRANSFER_ENCODING, CHUNKED)
                .payloadBody(from(DEFAULT_ALLOCATOR.fromAscii(REQUEST_PAYLOAD)))
                .transform(new StatelessTrailersTransformer<Buffer>() {
                    @Override
                    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                        trailers.set(CUSTOM_TRAILER, CUSTOM_VALUE);
                        return trailers;
                    }
                });
    }

    private static Single<StreamingHttpResponse> okResponse() {
        return succeeded(reqRespFactory.ok());
    }

    private Single<StreamingHttpResponse> redirectResponse(HttpResponseStatus status) {
        return redirectResponse(status, 1);
    }

    private Single<StreamingHttpResponse> redirectResponse(HttpResponseStatus status, int n) {
        return redirectResponse(status, null, n);
    }

    private Single<StreamingHttpResponse> redirectResponse(HttpResponseStatus status, String locationPrefix) {
        return redirectResponse(status, locationPrefix, 1);
    }

    private Single<StreamingHttpResponse> redirectResponse(HttpResponseStatus status, @Nullable String locationPrefix,
                                                           int n) {
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        redirectResponsePayloads.add(payloadBody);
        return succeeded(reqRespFactory.newResponse(status)
                .setHeader(LOCATION, (locationPrefix == null ? "" : locationPrefix) + "/location-" + n)
                .payloadBody(payloadBody.afterOnSubscribe(ignore -> EXECUTOR.executor()
                        .execute(payloadBody::onComplete))));
    }

    private StreamingHttpClient newClient(RedirectingHttpRequesterFilter.Builder redirectBuilder,
                                          StreamingHttpClientFilterFactory... other) {
        StreamingHttpClientFilterFactory result = redirectBuilder.build();
        for (StreamingHttpClientFilterFactory next : other) {
            result = appendClientFilterFactory(result, next);
        }
        StreamingHttpClientFilterFactory mockResponse = client -> new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return httpClient.request(strategy, request);
                    }
                };

        return from(reqRespFactory, mock(HttpExecutionContext.class), appendClientFilterFactory(result, mockResponse));
    }

    @Test
    void requestWithZeroMaxRedirects() throws Exception {
        testNoRedirectWasDone(0, GET, MOVED_PERMANENTLY);
    }

    @Test
    void notModifiedStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, NOT_MODIFIED);
    }

    @Test
    void useProxyStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, USE_PROXY);
    }

    @Test
    void status306DoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(306, ""));
    }

    @Test
    void status309DoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(309, ""));
    }

    @Test
    void status399DoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, HttpResponseStatus.of(399, ""));
    }

    @Test
    void continueStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, CONTINUE);
    }

    @Test
    void okStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, OK);
    }

    @Test
    void badRequestStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, BAD_REQUEST);
    }

    @Test
    void internalServerErrorStatusDoesntCauseRedirect() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, INTERNAL_SERVER_ERROR);
    }

    @Test
    void nonGetHeadMethodsDoNotRedirectByDefault() throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, POST, SEE_OTHER);
        testNoRedirectWasDone(MAX_REDIRECTS, PUT, SEE_OTHER);
        testNoRedirectWasDone(MAX_REDIRECTS, DELETE, SEE_OTHER);
        testNoRedirectWasDone(MAX_REDIRECTS, PATCH, SEE_OTHER);
        testNoRedirectWasDone(MAX_REDIRECTS, TRACE, SEE_OTHER);
        testNoRedirectWasDone(MAX_REDIRECTS, OPTIONS, SEE_OTHER);
    }

    @Test
    void connectRequestsDoesNotRedirectByDefault() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(SEE_OTHER), okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());

        StreamingHttpRequest request = client.newRequest(CONNECT, "servicetalk.io")
                .setHeader(HOST, "servicetalk.io");
        verifyDoesNotRedirect(client, request, SEE_OTHER);
        verifyRedirectResponsePayloadsDrained(false);
    }

    @Test
    void redirectResponseWithEmptyLocation() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                succeeded(reqRespFactory.newResponse(MOVED_PERMANENTLY)),   // no "location" header returned
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());

        verifyResponse(client, newRequest(client, GET), MOVED_PERMANENTLY, -1, 1, GET);
    }

    private void testNoRedirectWasDone(final int maxRedirects,
                                       final HttpRequestMethod method,
                                       final HttpResponseStatus requestedStatus) throws Exception {
        testNoRedirectWasDone(method, requestedStatus, new RedirectingHttpRequesterFilter.Builder()
                .maxRedirects(maxRedirects));
    }

    private void testNoRedirectWasDone(final HttpRequestMethod method,
                                       final HttpResponseStatus status,
                                       final RedirectingHttpRequesterFilter.Builder builder) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(status), okResponse());
        StreamingHttpClient client = newClient(builder);

        verifyDoesNotRedirect(client, newRequest(client, method), status);
        clearInvocations(httpClient);
        ignoreStubs(httpClient);
    }

    @Test
    void maxRedirectsReached() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any(), any()))
                .thenAnswer(invocation -> redirectResponse(MOVED_PERMANENTLY, counter.incrementAndGet()));

        final int maxRedirects = MAX_REDIRECTS;
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .maxRedirects(maxRedirects));

        verifyResponse(client, newRequest(client, GET), MOVED_PERMANENTLY, maxRedirects + 1, maxRedirects + 1, GET);
    }

    @Test
    void requestWithNullResponse() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(succeeded(null));
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());

        assertThat(client.request(newRequest(client, GET)).toFuture().get(), nullValue());
    }

    @Test
    void http10WithoutHostHeader() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());

        StreamingHttpRequest request = newRequest(client, GET).version(HTTP_1_0);
        request.headers().remove(HOST);
        verifyRedirected(client, request, true, false);
    }

    @Test
    void multipleChoicesRedirected() throws Exception {
        testRedirected(GET, MULTIPLE_CHOICES);
        testRedirected(HEAD, MULTIPLE_CHOICES);
    }

    @Test
    void movedPermanentlyRedirected() throws Exception {
        testRedirected(GET, MOVED_PERMANENTLY);
        testRedirected(HEAD, MOVED_PERMANENTLY);
    }

    @Test
    void foundRedirected() throws Exception {
        testRedirected(GET, FOUND);
        testRedirected(HEAD, FOUND);
    }

    @Test
    void seeOtherRedirected() throws Exception {
        testRedirected(GET, SEE_OTHER);
        testRedirected(HEAD, SEE_OTHER);
    }

    @Test
    void temporaryRedirectRedirected() throws Exception {
        testRedirected(GET, TEMPORARY_REDIRECT);
        testRedirected(HEAD, TEMPORARY_REDIRECT);
    }

    @Test
    void permanentRedirectRedirected() throws Exception {
        testRedirected(GET, PERMANENT_REDIRECT);
        testRedirected(HEAD, PERMANENT_REDIRECT);
    }

    private void testRedirected(final HttpRequestMethod method,
                                final HttpResponseStatus status) throws Exception {
        testRedirected(method, status, new RedirectingHttpRequesterFilter.Builder());
    }

    private void testRedirected(final HttpRequestMethod method,
                                final HttpResponseStatus status,
                                final RedirectingHttpRequesterFilter.Builder builder) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(status), okResponse());
        StreamingHttpClient client = newClient(builder);

        verifyRedirected(client, newRequest(client, method), true, false);
        clearInvocations(httpClient);
        ignoreStubs(httpClient);
    }

    @Test
    void multipleFollowUpRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, 1),
                redirectResponse(MOVED_PERMANENTLY, 2),
                redirectResponse(MOVED_PERMANENTLY, 3),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());

        verifyResponse(client, newRequest(client, GET), OK, -1, 4, GET);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void getRequestForRedirectWithAbsoluteFormRequestTargetToRelativeLocation(
            boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects));

        StreamingHttpRequest request = newRequest(client, GET).requestTarget("http://servicetalk.io/path");
        verifyRedirected(client, request, true, allowNonRelativeRedirects);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectWithAbsoluteFormRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects));

        verifyRedirected(client, newRequest(client, GET).requestTarget("http://servicetalk.io/path"), true,
                allowNonRelativeRedirects);
    }

    @Test
    void redirectForOnlyRelativeWithAbsoluteFormRelativeLocationWithPort() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://servicetalk.io:80"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(false));

        verifyRedirected(client, newRequest(client, GET).setHeader(HOST, "servicetalk.io:80"), true, false);
    }

    @Test
    void allowNonRelativeRedirectsWithRelativeLocation() throws Exception {
        testRedirected(GET, MOVED_PERMANENTLY, new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromHttpToHttps(boolean allowNonRelativeRedirects) throws Exception {
        crossSchemeRedirects("http", "https", allowNonRelativeRedirects);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromHttpsToHttp(boolean allowNonRelativeRedirects) throws Exception {
        crossSchemeRedirects("https", "http", allowNonRelativeRedirects);
    }

    private void crossSchemeRedirects(String fromScheme, String toScheme,
                                      boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, toScheme + "://servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects));

        StreamingHttpRequest request = newRequest(client, GET).requestTarget(fromScheme + "://servicetalk.io/path");
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromRelativeFormToAbsoluteFormNonRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects));

        StreamingHttpRequest request = newRequest(client, GET);
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromAbsoluteFormToAbsoluteFormNonRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects));

        StreamingHttpRequest request = newRequest(client, GET).requestTarget("http://servicetalk.io/path");
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false);
        }
    }

    @Test
    void overrideAllowedMethods() throws Exception {
        RedirectingHttpRequesterFilter.Builder builder = new RedirectingHttpRequesterFilter.Builder()
                .allowedMethods(POST);
        testRedirected(POST, MOVED_PERMANENTLY, builder);
        testNoRedirectWasDone(GET, MOVED_PERMANENTLY, builder);
        testNoRedirectWasDone(HEAD, MOVED_PERMANENTLY, builder);
    }

    @Test
    void shouldRedirectReturnsFalse() throws Exception {
        RedirectingHttpRequesterFilter.Builder builder = new RedirectingHttpRequesterFilter.Builder()
                .shouldRedirect((req, resp) -> false);
        testNoRedirectWasDone(GET, MOVED_PERMANENTLY, builder);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(ints = {301, 302})
    void changePostToGet(int statusCode) throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(HttpResponseStatus.of(statusCode, "")),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowedMethods(POST).changePostToGet(true));

        StreamingHttpRequest request = newRequest(client, POST);
        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, OK, -1, 2, GET);
        assertThat("Request didn't change", request, not(sameInstance(redirectedRequest)));
        verifyHeadersAndMessageBodyRedirected(redirectedRequest);
        verifyRedirectResponsePayloadsDrained(true);
    }

    @Test
    void configureRedirectOfHeadersAndMessageBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect(CUSTOM_HEADER, TRANSFER_ENCODING)
                .redirectPayloadBody(true)
                .trailersToRedirect(CUSTOM_TRAILER));

        verifyRedirected(client, newRequest(client, GET), true, true);
    }

    @Test
    void configureRedirectOfPayloadBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect(CONTENT_LENGTH)
                .redirectPayloadBody(true)
                .allowedMethods(POST));

        StreamingHttpRequest request = client.post("/path")
                .setHeader(HOST, "servicetalk.io")
                .setHeader(CONTENT_LENGTH, valueOf(REQUEST_PAYLOAD.length()))
                .payloadBody(from(DEFAULT_ALLOCATOR.fromAscii(REQUEST_PAYLOAD)));

        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, OK, -1, 2, request.method());
        assertThat("Request didn't change", request, not(sameInstance(redirectedRequest)));

        assertThat("Unexpected " + CONTENT_LENGTH, redirectedRequest.headers().get(CONTENT_LENGTH),
                contentEqualTo(valueOf(REQUEST_PAYLOAD.length())));
        assertThat("Unexpected payload body", redirectedRequest.payloadBody().collect(StringBuilder::new,
                (sb, chunk) -> sb.append(chunk.toString(US_ASCII)))
                .toFuture().get().toString(), contentEqualTo(REQUEST_PAYLOAD));
        verifyRedirectResponsePayloadsDrained(true);
    }

    @Test
    void misConfigureRedirectOfHeadersAndMessageBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect("Unknown-header")
                .redirectPayloadBody(false)
                .trailersToRedirect("Unknown-trailer"));

        verifyRedirected(client, newRequest(client, GET), false, true);
    }

    @Test
    void manuallyRedirectHeadersAndMessageBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true)
                .prepareRequest((relative, original, response, redirect) -> {
                    redirect.setHeaders(original.headers());
                    redirect.transformMessageBody(p -> p.ignoreElements().concat(original.messageBody()));
                    // Use `transform` to update PayloadInfo, assuming trailers may be included in the message body
                    redirect.transform(new StatelessTrailersTransformer<>());
                    return redirect;
                }));

        verifyRedirected(client, newRequest(client, GET), true, true);
    }

    @Test
    void prepareRequestThrows() {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .prepareRequest((relative, original, response, redirect) -> {
                    throw DELIBERATE_EXCEPTION;
                }));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void shouldRedirectThrows() {
        when(httpClient.request(any(), any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .shouldRedirect((request, response) -> {
                    throw DELIBERATE_EXCEPTION;
                }));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void invalidLocation() {
        when(httpClient.request(any(), any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder()
                .allowNonRelativeRedirects(true));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void failedResponse() {
        when(httpClient.request(any(), any())).thenReturn(failed(DELIBERATE_EXCEPTION));
        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void indexesOfPayloadBodyAreNotMoved() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any(), any())).thenAnswer(invocation -> {
            if (counter.incrementAndGet() == 1) {
                StreamingHttpRequest request = invocation.getArgument(1);
                // Simulate payload body write:
                request.payloadBody().map(buffer -> buffer.skipBytes(buffer.readableBytes())).toFuture().get();
                return redirectResponse(MOVED_PERMANENTLY);
            } else {
                return okResponse();
            }
        });

        StreamingHttpClient client = newClient(new RedirectingHttpRequesterFilter.Builder());
        verifyRedirected(client, newRequest(client, GET), true, false);
    }

    private StreamingHttpRequest verifyDoesNotRedirect(StreamingHttpClient client,
                                                       StreamingHttpRequest request,
                                                       HttpResponseStatus status) throws Exception {
        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, status, 1, 1, request.method());
        assertThat("Request has changed unexpectedly", request, sameInstance(redirectedRequest));
        return redirectedRequest;
    }

    private StreamingHttpRequest verifyRedirected(StreamingHttpClient client,
                                                  StreamingHttpRequest request,
                                                  boolean redirectsHeadersAndMessageBody,
                                                  boolean absoluteForm) throws Exception {
        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, OK, -1, 2, request.method());
        assertThat("Request didn't change", request, not(sameInstance(redirectedRequest)));
        if (redirectsHeadersAndMessageBody) {
            verifyHeadersAndMessageBodyRedirected(redirectedRequest);
        } else {
            verifyHeadersAndMessageBodyAreNotRedirected(redirectedRequest);
        }
        if (absoluteForm) {
            String scheme = redirectedRequest.scheme();
            assertThat("Unexpected scheme on redirected request", scheme, notNullValue());
            assertThat("Unexpected request-target of redirected request",
                    redirectedRequest.requestTarget(), startsWith(scheme));
        } else {
            assertThat("Unexpected scheme on redirected request", redirectedRequest.scheme(), nullValue());
            assertThat("Unexpected request-target of redirected request",
                    redirectedRequest.requestTarget(), startsWith("/"));
        }
        verifyRedirectResponsePayloadsDrained(true);
        return redirectedRequest;
    }

    private void verifyRedirectResponsePayloadsDrained(boolean drained) {
        int n = 0;
        for (TestPublisher<Buffer> payload : redirectResponsePayloads) {
            assertThat("Redirect response payload (/location-" + ++n +
                            (drained ? ") was not drained" : ") was unexpectedly drained"),
                    payload.isSubscribed(), is(drained));
        }
    }

    private static void verifyHeadersAndMessageBodyRedirected(StreamingHttpRequest request) throws Exception {
        HttpHeaders headers = request.headers();
        assertThat("Unexpected " + CUSTOM_HEADER, headers.get(CUSTOM_HEADER), contentEqualTo(CUSTOM_VALUE));
        assertThat("Unexpected " + TRANSFER_ENCODING, headers.get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        request.transform(new StatelessTrailersTransformer<Buffer>() {
            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                assertThat("Unexpected " + CUSTOM_TRAILER, trailers.get(CUSTOM_TRAILER), contentEqualTo(CUSTOM_VALUE));
                return trailers;
            }
        });
        assertThat("Unexpected payload body", request.payloadBody().collect(StringBuilder::new,
                (sb, chunk) -> sb.append(chunk.toString(US_ASCII)))
                .toFuture().get().toString(), contentEqualTo(REQUEST_PAYLOAD));
    }

    private static void verifyHeadersAndMessageBodyAreNotRedirected(StreamingHttpRequest request) throws Exception {
        assertThat("Unexpected headers", request.headers(), emptyIterable());
        request.transform(new StatelessTrailersTransformer<Buffer>() {
            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                assertThat("Unexpected non-empty trailers", trailers, emptyIterable());
                return trailers;
            }
        });
        assertThat("Unexpected payload body", request.payloadBody().collect(StringBuilder::new,
                (sb, chunk) -> sb.append(chunk.toString(US_ASCII)))
                .toFuture().get().toString(), contentEqualTo(""));
    }

    private StreamingHttpRequest verifyResponse(StreamingHttpClient client, StreamingHttpRequest request,
                                                HttpResponseStatus expectedStatus,
                                                int expectedLocationN,
                                                int numberOfInvocations,
                                                @Nullable HttpRequestMethod expectedMethod) throws Exception {
        ArgumentCaptor<StreamingHttpRequest> requestCaptor = forClass(StreamingHttpRequest.class);
        StreamingHttpResponse response = client.request(defaultStrategy(), request).toFuture().get();
        assertThat(response, is(notNullValue()));
        assertThat("Unexpected response status", response.status(), is(expectedStatus));
        if (expectedLocationN > 0) {
            CharSequence location = response.headers().get(LOCATION);
            assertThat("Unexpected location", location, notNullValue());
            assertThat("Unexpected location", location.toString(), endsWith("/location-" + expectedLocationN));
        } else {
            assertThat("Unexpected location", response.headers().get(LOCATION), nullValue());
        }
        verify(httpClient, times(numberOfInvocations)).request(any(), requestCaptor.capture());
        StreamingHttpRequest redirectedRequest = requestCaptor.getValue();
        if (expectedMethod != null) {
            assertThat("Unexpected request method", redirectedRequest.method(), is(expectedMethod));
        }
        return redirectedRequest;
    }
}
