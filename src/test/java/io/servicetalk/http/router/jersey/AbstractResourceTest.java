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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.server.ApplicationHandler;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.TestUtil.getContentAsString;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonStringEquals;
import static org.glassfish.jersey.message.internal.CommittingOutputStream.DEFAULT_BUFFER_SIZE;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public abstract class AbstractResourceTest {
    static final String TEST_HOST = "some.fakehost.tld";

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    protected ConnectionContext ctx;

    protected DefaultRequestHandler handler;

    @Before
    public void init() {
        when(ctx.getAllocator()).thenReturn(BufferAllocators.DEFAULT.getAllocator());
        handler = new DefaultRequestHandler(new ApplicationHandler(new TestApplication()));
    }

    abstract String getResourcePath();

    @Test
    public void notFound() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(HEAD, getResourcePath() + "/not_a_resource");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, NOT_FOUND, null, "");
    }

    @Test(expected = DeliberateException.class)
    public void notTranslatedException() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/text?qp=throw-not-translated");

        handler.apply(ctx, req);
    }

    @Test
    public void translatedException() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text?qp=throw-translated");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, CONFLICT, null, "");
    }

    @Test
    public void implicitHead() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(HEAD, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, isEmptyString(), 16);
    }

    @Test
    public void implicitOptions() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(OPTIONS, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, "application/vnd.sun.wadl+xml", not(isEmptyString()), String::length);
        assertThat(res.getHeaders().get(ALLOW, "").toString().split(","),
                is(arrayContainingInAnyOrder("HEAD", "POST", "GET", "OPTIONS")));
    }

    @Test
    public void getText() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: null & null");
    }

    @Test
    public void getTextQueryParam() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text?qp=foo%20|bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo |bar & null");
    }

    @Test
    public void getTextHeaderParam() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text");
        req.getHeaders().add("hp", "bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: null & bar");
    }

    @Test
    public void postText() {
        // Small payload
        HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, getResourcePath() + "/text", ctx.getAllocator().fromUtf8("foo"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo");

        // Large payload that goes above default buffer size
        final String payload = new String(new char[2 * DEFAULT_BUFFER_SIZE]).replace('\0', 'A');
        req = newH11Request(POST, PATH + "/text", ctx.getAllocator().fromUtf8(payload));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: " + payload), $ -> null);
    }

    @Test
    public void getTextResponse() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text-response");
        req.getHeaders().add("hdr", "bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, NO_CONTENT, null, "");
        assertThat(res.getHeaders().get("X-Test"), is("bar"));
    }

    @Test
    public void getJson() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/json");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, APPLICATION_JSON, jsonStringEquals("{\"foo\":\"bar1\"}"), String::length);
    }

    @Test
    public void putJsonResponse() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(PUT, getResourcePath() + "/json-response",
                ctx.getAllocator().fromUtf8("{\"key\":\"val1\"}"));
        req.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, ACCEPTED, APPLICATION_JSON, jsonStringEquals("{\"key\":\"val1\",\"foo\":\"bar2\"}"),
                String::length);
        assertThat(res.getHeaders().get("X-Test"), is("test-header"));
    }

    static HttpRequest<HttpPayloadChunk> newH10Request(final HttpRequestMethod method,
                                                       final String requestTarget) {
        return newRequest(HTTP_1_0, method, "http://" + TEST_HOST + requestTarget);
    }

    static HttpRequest<HttpPayloadChunk> newH11Request(final HttpRequestMethod method,
                                                       final String requestTarget) {
        final HttpRequest<HttpPayloadChunk> request = newRequest(HTTP_1_1, method, requestTarget);
        request.getHeaders().add(HOST, TEST_HOST);
        return request;
    }

    static HttpRequest<HttpPayloadChunk> newH11Request(final HttpRequestMethod method,
                                                       final String requestTarget,
                                                       final Buffer content) {
        final HttpRequest<HttpPayloadChunk> request = newRequest(HTTP_1_1, method, requestTarget,
                just(newPayloadChunk(content), immediate()));

        request.getHeaders().add(HOST, TEST_HOST);
        request.getHeaders().add(CONTENT_LENGTH, Integer.toString(content.getReadableBytes()));
        return request;
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final String expectedContent) {
        assertResponse(res, expectedStatusCode, expectedContentType, is(expectedContent), $ -> expectedContent.length());
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final int expectedContentLength) {
        assertResponse(res, expectedStatusCode, expectedContentType, contentMatcher, $ -> expectedContentLength);
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final Function<String, Integer> expectedContentLengthExtractor) {
        assertResponse(res, HTTP_1_1, expectedStatusCode, expectedContentType, contentMatcher,
                expectedContentLengthExtractor);
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpProtocolVersion expectedHttpVersion,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final Function<String, Integer> expectedContentLengthExtractor) {

        assertThat(res.getVersion(), is(expectedHttpVersion));
        assertThat(res.getStatus().getCode(), is(expectedStatusCode.getCode()));
        assertThat(res.getStatus().getReasonPhrase(), is(expectedStatusCode.getReasonPhrase()));

        if (expectedContentType != null) {
            assertThat(res.getHeaders().get(CONTENT_TYPE), is(expectedContentType));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_TYPE), is(false));
        }

        final String contentAsString = getContentAsString(res);

        @Nullable
        final Integer expectedContentLength = expectedContentLengthExtractor.apply(contentAsString);
        if (expectedContentLength != null) {
            assertThat(res.getHeaders().get(CONTENT_LENGTH), is(Integer.toString(expectedContentLength)));
            res.getHeaders().getAll(TRANSFER_ENCODING)
                    .forEachRemaining(h -> assertThat(h.toString(), equalToIgnoringCase("chunked")));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_LENGTH), is(false));
            assertThat(res.getHeaders().get(TRANSFER_ENCODING), is(CHUNKED));
        }

        assertThat(contentAsString, contentMatcher);
    }
}
