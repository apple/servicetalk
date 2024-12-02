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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatus.StatusClass;
import io.servicetalk.http.api.RedirectConfig;
import io.servicetalk.http.api.RedirectConfigBuilder;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.FilterFactoryUtils.appendClientFilterFactory;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
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
import static java.util.Arrays.asList;
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
    static final ExecutorExtension<Executor> EXECUTOR = ExecutorExtension.withCachedExecutor().setClassLevel(true);

    private static final String CUSTOM_HEADER = "custom-header";
    private static final String CUSTOM_TRAILER = "custom-trailer";
    private static final String CUSTOM_VALUE = "custom-value";
    private static final String REQUEST_PAYLOAD = "request-payload";
    private static final int MAX_REDIRECTS = 5;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);
    private static final ContextMap.Key<String> UUID_KEY = newKey("UUID_KEY", String.class);

    private final StreamingHttpRequester httpClient = mock(StreamingHttpRequester.class);
    private final Queue<TestPublisher<Buffer>> redirectResponsePayloads = new LinkedBlockingDeque<>();

    private static StreamingHttpRequest newRequest(StreamingHttpRequestFactory reqFactory, HttpRequestMethod method) {
        StreamingHttpRequest request = reqFactory.newRequest(method, "/path")
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
        request.context().put(UUID_KEY, UUID.randomUUID().toString());
        return request;
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

    private StreamingHttpClient newClient(RedirectConfig config,
                                          StreamingHttpClientFilterFactory... other) {
        StreamingHttpClientFilterFactory result = new RedirectingHttpRequesterFilter(config);
        for (StreamingHttpClientFilterFactory next : other) {
            result = appendClientFilterFactory(result, next);
        }
        StreamingHttpClientFilterFactory mockResponse = client -> new StreamingHttpClientFilter(client) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final StreamingHttpRequest request) {
                        return httpClient.request(request);
                    }
                };

        return from(reqRespFactory, mock(HttpExecutionContext.class), appendClientFilterFactory(result, mockResponse));
    }

    @Test
    void requestWithZeroMaxRedirects() throws Exception {
        testNoRedirectWasDone(0, GET, MOVED_PERMANENTLY);
    }

    private static List<HttpResponseStatus> notAllowedStatusesByDefault() {
        return asList(NOT_MODIFIED, USE_PROXY, HttpResponseStatus.of(306, "Switch Proxy"),
                HttpResponseStatus.of(309, "Custom Min Range"),
                HttpResponseStatus.of(399, "Custom Max Range"),
                CONTINUE, OK, BAD_REQUEST, INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest(name = "{displayName} [{index}] status={0}")
    @MethodSource("notAllowedStatusesByDefault")
    void doesNotFollowRedirectForStatusByDefault(HttpResponseStatus status) throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, GET, status);
    }

    @ParameterizedTest(name = "{displayName} [{index}] method={0}")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH", "TRACE", "OPTIONS"})
    void nonGetHeadMethodsDoNotRedirectByDefault(HttpRequestMethod method) throws Exception {
        testNoRedirectWasDone(MAX_REDIRECTS, method, SEE_OTHER);
    }

    @Test
    void connectRequestsDoesNotRedirectByDefault() throws Exception {
        when(httpClient.request(any())).thenReturn(redirectResponse(SEE_OTHER), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());

        StreamingHttpRequest request = client.newRequest(CONNECT, "servicetalk.io")
                .setHeader(HOST, "servicetalk.io");
        verifyDoesNotRedirect(client, request, SEE_OTHER);
        verifyRedirectResponsePayloadsDrained(false, false);
    }

    @Test
    void redirectResponseWithEmptyLocation() throws Exception {
        when(httpClient.request(any())).thenReturn(
                succeeded(reqRespFactory.newResponse(MOVED_PERMANENTLY)),   // no "location" header returned
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());

        verifyResponse(client, newRequest(client, GET), MOVED_PERMANENTLY, -1, 1, GET);
    }

    private void testNoRedirectWasDone(final int maxRedirects,
                                       final HttpRequestMethod method,
                                       final HttpResponseStatus requestedStatus) throws Exception {
        testNoRedirectWasDone(method, requestedStatus, new RedirectConfigBuilder().maxRedirects(maxRedirects).build());
    }

    private void testNoRedirectWasDone(final HttpRequestMethod method,
                                       final HttpResponseStatus status,
                                       final RedirectConfig config) throws Exception {
        when(httpClient.request(any())).thenReturn(redirectResponse(status), okResponse());
        StreamingHttpClient client = newClient(config);

        verifyDoesNotRedirect(client, newRequest(client, method), status);
        clearInvocations(httpClient);
        ignoreStubs(httpClient);
    }

    @Test
    void maxRedirectsReached() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any()))
                .thenAnswer(invocation -> redirectResponse(MOVED_PERMANENTLY, counter.incrementAndGet()));

        final int maxRedirects = MAX_REDIRECTS;
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().maxRedirects(maxRedirects).build());

        verifyResponse(client, newRequest(client, GET), MOVED_PERMANENTLY, maxRedirects + 1, maxRedirects + 1, GET);
    }

    @Test
    void requestWithNullResponse() throws Exception {
        when(httpClient.request(any())).thenReturn(succeeded(null));
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());

        assertThat(client.request(newRequest(client, GET)).toFuture().get(), nullValue());
    }

    @Test
    void http10WithoutHostHeader() throws Exception {
        when(httpClient.request(any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());

        StreamingHttpRequest request = newRequest(client, GET).version(HTTP_1_0);
        request.headers().remove(HOST);
        verifyRedirected(client, request, true, false);
    }

    private static List<HttpResponseStatus> allowedStatusesByDefault() {
        return asList(MULTIPLE_CHOICES, MOVED_PERMANENTLY, FOUND, SEE_OTHER, TEMPORARY_REDIRECT, PERMANENT_REDIRECT);
    }

    @ParameterizedTest(name = "{displayName} [{index}] status={0}")
    @MethodSource("allowedStatusesByDefault")
    void followsRedirectForStatusByDefault(HttpResponseStatus status) throws Exception {
        testRedirected(GET, status);
        testRedirected(HEAD, status);
    }

    private void testRedirected(final HttpRequestMethod method,
                                final HttpResponseStatus status) throws Exception {
        testRedirected(method, status, new RedirectConfigBuilder().build());
    }

    private void testRedirected(final HttpRequestMethod method,
                                final HttpResponseStatus status,
                                final RedirectConfig config) throws Exception {
        when(httpClient.request(any())).thenReturn(redirectResponse(status), okResponse());
        StreamingHttpClient client = newClient(config);

        verifyRedirected(client, newRequest(client, method), true, false);
        clearInvocations(httpClient);
        ignoreStubs(httpClient);
    }

    @Test
    void multipleFollowUpRedirects() throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, 1),
                redirectResponse(MOVED_PERMANENTLY, 2),
                redirectResponse(MOVED_PERMANENTLY, 3),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());

        verifyResponse(client, newRequest(client, GET), OK, -1, 4, GET);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void getRequestForRedirectWithAbsoluteFormRequestTargetToRelativeLocation(
            boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects).build());

        StreamingHttpRequest request = newRequest(client, GET).requestTarget("http://servicetalk.io/path");
        verifyRedirected(client, request, true, allowNonRelativeRedirects);
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectWithAbsoluteFormRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects).build());

        verifyRedirected(client, newRequest(client, GET).requestTarget("http://servicetalk.io/path"), true,
                allowNonRelativeRedirects);
    }

    @Test
    void redirectForOnlyRelativeWithAbsoluteFormRelativeLocationWithPort() throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://servicetalk.io:80"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(false).build());

        verifyRedirected(client, newRequest(client, GET).setHeader(HOST, "servicetalk.io:80"), true, false);
    }

    @Test
    void allowNonRelativeRedirectsWithRelativeLocation() throws Exception {
        testRedirected(GET, MOVED_PERMANENTLY, new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true).build());
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
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, toScheme + "://servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects).build());

        StreamingHttpRequest request = newRequest(client, GET).requestTarget(fromScheme + "://servicetalk.io/path");
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false, false);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromRelativeFormToAbsoluteFormNonRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects).build());

        StreamingHttpRequest request = newRequest(client, GET);
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false, false);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(booleans = {true, false})
    void redirectFromAbsoluteFormToAbsoluteFormNonRelativeLocation(boolean allowNonRelativeRedirects) throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(allowNonRelativeRedirects).build());

        StreamingHttpRequest request = newRequest(client, GET).requestTarget("http://servicetalk.io/path");
        if (allowNonRelativeRedirects) {
            verifyRedirected(client, request, false, true);
        } else {
            verifyDoesNotRedirect(client, request, MOVED_PERMANENTLY);
            verifyRedirectResponsePayloadsDrained(false, false);
        }
    }

    @Test
    void overrideAllowedStatuses() throws Exception {
        HttpResponseStatus[] allowedStatuses = notAllowedStatusesByDefault().stream()
                .filter(StatusClass.REDIRECTION_3XX::contains)
                .toArray(HttpResponseStatus[]::new);

        RedirectConfig config = new RedirectConfigBuilder().allowedStatuses(allowedStatuses).build();
        for (HttpResponseStatus status : allowedStatuses) {
            testRedirected(GET, status, config);
        }
        for (HttpResponseStatus status : allowedStatusesByDefault()) {
            testNoRedirectWasDone(GET, status, config);
        }
    }

    @Test
    void overrideAllowedMethods() throws Exception {
        RedirectConfig config = new RedirectConfigBuilder().allowedMethods(POST).build();
        testRedirected(POST, MOVED_PERMANENTLY, config);
        testNoRedirectWasDone(GET, MOVED_PERMANENTLY, config);
        testNoRedirectWasDone(HEAD, MOVED_PERMANENTLY, config);
    }

    @Test
    void customLocationMapper() throws Exception {
        String customLocationHeader = "x-location";
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY).map(response -> {
                    CharSequence location = response.headers().get(LOCATION, "/");
                    response.setHeader(customLocationHeader, location);
                    response.headers().remove(LOCATION);
                    return response;
                }),
                okResponse());
        AtomicBoolean locationMapperInvoked = new AtomicBoolean();
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .locationMapper((req, resp) -> {
                    locationMapperInvoked.set(true);
                    return resp.headers().get(customLocationHeader, "").toString();
                }).build());

        StreamingHttpRequest request = newRequest(client, GET);
        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, OK, -1, 2, GET);
        assertThat("Request didn't change", request, not(sameInstance(redirectedRequest)));
        verifyHeadersAndMessageBodyRedirected(redirectedRequest);
        verifyRedirectResponsePayloadsDrained(true, false);
        assertThat("LocationMapper was not invoked", locationMapperInvoked.get(), is(true));
    }

    @Test
    void locationMapperReturnsNull() throws Exception {
        testNoRedirectWasDone(GET, MOVED_PERMANENTLY, new RedirectConfigBuilder()
                .locationMapper((req, resp) -> null).build());
    }

    @Test
    void redirectPredicateReturnsFalse() throws Exception {
        testNoRedirectWasDone(GET, MOVED_PERMANENTLY, new RedirectConfigBuilder()
                .redirectPredicate((relative, cnt, req, resp) -> false).build());
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowNonRelativeRedirects={0}")
    @ValueSource(ints = {301, 302})
    void changePostToGet(int statusCode) throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(HttpResponseStatus.of(statusCode, "")),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowedMethods(POST).changePostToGet(true).build());

        StreamingHttpRequest request = newRequest(client, POST);
        StreamingHttpRequest redirectedRequest = verifyResponse(client, request, OK, -1, 2, GET);
        assertThat("Request didn't change", request, not(sameInstance(redirectedRequest)));
        verifyHeadersAndMessageBodyRedirected(redirectedRequest);
        verifyRedirectResponsePayloadsDrained(true, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] manyHeaders={0}")
    @ValueSource(booleans = {false, true})
    void configureRedirectOfHeadersAndMessageBodyForNonRelativeRedirects(boolean manyHeaders) throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        final CharSequence[] headersToRedirect = manyHeaders ? new CharSequence[]{CUSTOM_HEADER, TRANSFER_ENCODING,
                CONTENT_LENGTH, HOST, ACCEPT_ENCODING, CONTENT_TYPE} :
                new CharSequence[]{CUSTOM_HEADER, TRANSFER_ENCODING};
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect(headersToRedirect)
                .redirectPayloadBody(true)
                .trailersToRedirect(CUSTOM_TRAILER)
                .build());

        verifyRedirected(client, newRequest(client, GET), true, true);
    }

    @Test
    void configureRedirectOfPayloadBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect(CONTENT_LENGTH)
                .redirectPayloadBody(true)
                .allowedMethods(POST)
                .build());

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
        verifyRedirectResponsePayloadsDrained(true, false);
    }

    @Test
    void misConfigureRedirectOfHeadersAndMessageBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true)
                .headersToRedirect("Unknown-header")
                .redirectPayloadBody(false)
                .trailersToRedirect("Unknown-trailer")
                .build());

        verifyRedirected(client, newRequest(client, GET), false, true);
    }

    @Test
    void manuallyRedirectHeadersAndMessageBodyForNonRelativeRedirects() throws Exception {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "http://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true)
                .redirectRequestTransformer((relative, original, response, redirect) -> {
                    redirect.setHeaders(original.headers());
                    redirect.transformMessageBody(p -> p.ignoreElements().concat(original.messageBody()));
                    // Use `transform` to update PayloadInfo, assuming trailers may be included in the message body
                    redirect.transform(new StatelessTrailersTransformer<>());
                    return redirect;
                }).build());

        verifyRedirected(client, newRequest(client, GET), true, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] cancel={0}")
    @ValueSource(booleans = {false, true})
    void redirectRequestTransformerThrows(boolean cancel) {
        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        when(httpClient.request(any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .redirectRequestTransformer((relative, original, response, redirect) -> {
                    if (cancel) {
                        cancellable.get().cancel();
                    }
                    throw DELIBERATE_EXCEPTION;
                }).build());

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).whenOnSubscribe(cancellable::set).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        verifyRedirectResponsePayloadsDrained(true, cancel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] cancel={0}")
    @ValueSource(booleans = {false, true})
    void redirectPredicateThrows(boolean cancel) {
        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        when(httpClient.request(any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .redirectPredicate((relative, count, request, response) -> {
                    if (cancel) {
                        cancellable.get().cancel();
                    }
                    throw DELIBERATE_EXCEPTION;
                }).build());

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).whenOnSubscribe(cancellable::set).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        verifyRedirectResponsePayloadsDrained(true, cancel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] cancel={0}")
    @ValueSource(booleans = {false, true})
    void locationMapperThrows(boolean cancel) {
        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        when(httpClient.request(any())).thenReturn(redirectResponse(MOVED_PERMANENTLY), okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .locationMapper((request, response) -> {
                    if (cancel) {
                        cancellable.get().cancel();
                    }
                    throw DELIBERATE_EXCEPTION;
                }).build());

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).whenOnSubscribe(cancellable::set).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        verifyRedirectResponsePayloadsDrained(true, cancel);
    }

    @Test
    void invalidLocation() {
        when(httpClient.request(any())).thenReturn(
                redirectResponse(MOVED_PERMANENTLY, "://non-relative.servicetalk.io"),
                okResponse());
        StreamingHttpClient client = newClient(new RedirectConfigBuilder()
                .allowNonRelativeRedirects(true).build());

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void failedResponse() {
        when(httpClient.request(any())).thenReturn(failed(DELIBERATE_EXCEPTION));
        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> client.request(newRequest(client, GET)).toFuture().get());
        assertThat(e.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void indexesOfPayloadBodyAreNotMoved() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        when(httpClient.request(any())).thenAnswer(invocation -> {
            if (counter.incrementAndGet() == 1) {
                StreamingHttpRequest request = invocation.getArgument(0);
                // Simulate payload body write:
                request.payloadBody().map(buffer -> buffer.skipBytes(buffer.readableBytes())).toFuture().get();
                return redirectResponse(MOVED_PERMANENTLY);
            } else {
                return okResponse();
            }
        });

        StreamingHttpClient client = newClient(new RedirectConfigBuilder().build());
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
        verifyRedirectResponsePayloadsDrained(true, false);
        return redirectedRequest;
    }

    private void verifyRedirectResponsePayloadsDrained(boolean drained, boolean cancelled) {
        int n = 0;
        for (TestPublisher<Buffer> payload : redirectResponsePayloads) {
            assertThat("Redirect (/location-" + ++n + ") response payload was " +
                            (drained ? "not" : "unexpectedly") + " drained",
                    payload.isSubscribed(), is(drained));

            if (drained) {
                TestSubscription subscription = new TestSubscription();
                payload.onSubscribe(subscription);
                assertThat("Redirect (/location-" + ++n + ") response payload subscription was " +
                                (cancelled ? "not" : "unexpectedly") + " cancelled",
                        subscription.isCancelled(), is(cancelled));
            }
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
        StreamingHttpResponse response = client.request(request).toFuture().get();
        assertThat(response, is(notNullValue()));
        assertThat("Unexpected response status", response.status(), is(expectedStatus));
        if (expectedLocationN > 0) {
            CharSequence location = response.headers().get(LOCATION);
            assertThat("Unexpected location", location, notNullValue());
            assertThat("Unexpected location", location.toString(), endsWith("/location-" + expectedLocationN));
        } else {
            assertThat("Unexpected location", response.headers().get(LOCATION), nullValue());
        }
        verify(httpClient, times(numberOfInvocations)).request(requestCaptor.capture());
        StreamingHttpRequest redirectedRequest = requestCaptor.getValue();
        if (expectedMethod != null) {
            assertThat("Unexpected request method", redirectedRequest.method(), is(expectedMethod));
        }
        assertThat("Request context not preserved after redirect",
                redirectedRequest.context().get(UUID_KEY), is(sameInstance(request.context().get(UUID_KEY))));
        return redirectedRequest;
    }
}
