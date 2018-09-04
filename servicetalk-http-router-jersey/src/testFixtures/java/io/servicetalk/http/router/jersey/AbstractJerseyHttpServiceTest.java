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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatuses;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.DefaultHttpServerStarter;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.internal.Await.awaitNonNull;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.router.jersey.TestUtils.getContentAsString;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glassfish.jersey.CommonProperties.getValue;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public abstract class AbstractJerseyHttpServiceTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new IoThreadFactory("stserverio")),
            () -> newCachedThreadExecutor(new DefaultThreadFactory("stserver-", true, NORM_PRIORITY)));

    private ServerContext serverContext;
    private boolean streamingJsonEnabled;
    private HttpClient httpClient;

    @Before
    public final void initServer() throws Exception {
        final HttpService router = configureBuilder(new HttpJerseyRouterBuilder()).build(getApplication());
        final Configuration config = ((DefaultJerseyHttpRouter) router).getConfiguration();
        streamingJsonEnabled = getValue(config.getProperties(), config.getRuntimeType(), JSON_FEATURE, "",
                String.class).toLowerCase().contains("servicetalk");

        serverContext = awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .start(getServerExecutionContext(), new InetSocketAddress(0), router));
    }

    @Before
    public final void initClient() {
        InetSocketAddress serverAddress = (InetSocketAddress) serverContext.getListenAddress();
        httpClient = HttpClients.forSingleAddress(of(serverAddress)).build();
    }

    protected HttpJerseyRouterBuilder configureBuilder(final HttpJerseyRouterBuilder builder) {
        return builder;
    }

    protected ExecutionContext getServerExecutionContext() {
        return SERVER_CTX;
    }

    @After
    public final void closeClient() throws Exception {
        awaitIndefinitely(httpClient.closeAsync());
    }

    @After
    public final void closeServer() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
    }

    protected abstract Application getApplication();

    protected String host() {
        return "localhost:" + ((InetSocketAddress) serverContext.getListenAddress()).getPort();
    }

    protected boolean isStreamingJsonEnabled() {
        return streamingJsonEnabled;
    }

    protected String testUri(final String path) {
        return path;
    }

    protected HttpRequest<HttpPayloadChunk> options(final String path) {
        return noPayloadRequest(OPTIONS, path);
    }

    protected HttpRequest<HttpPayloadChunk> head(final String path) {
        return noPayloadRequest(HEAD, path);
    }

    protected HttpRequest<HttpPayloadChunk> get(final String path) {
        return noPayloadRequest(GET, path);
    }

    protected HttpRequest<HttpPayloadChunk> post(final String path, final CharSequence payload,
                                                 final CharSequence contentType) {
        return payloadRequest(POST, path, payload, contentType);
    }

    protected HttpRequest<HttpPayloadChunk> put(final String path, final CharSequence payload,
                                                final CharSequence contentType) {
        return payloadRequest(PUT, path, payload, contentType);
    }

    protected HttpRequest<HttpPayloadChunk> noPayloadRequest(final HttpRequestMethod method, final String path) {
        final HttpRequest<HttpPayloadChunk> req = newRequest(method, testUri(path));
        req.getHeaders().set(HOST, host());
        return req;
    }

    protected HttpRequest<HttpPayloadChunk> payloadRequest(final HttpRequestMethod method,
                                                           final String path,
                                                           final CharSequence payload,
                                                           final CharSequence contentType) {
        final Buffer content = DEFAULT_ALLOCATOR.fromUtf8(payload);
        final HttpRequest<HttpPayloadChunk> req = newRequest(method, testUri(path), newPayloadChunk(content));
        req.getHeaders().set(HOST, host());
        req.getHeaders().set(CONTENT_TYPE, contentType);
        req.getHeaders().set(CONTENT_LENGTH, Integer.toString(content.getReadableBytes()));
        return req;
    }

    protected HttpRequest<HttpPayloadChunk> withHeader(final HttpRequest<HttpPayloadChunk> req,
                                                       final String name,
                                                       final String value) {
        req.getHeaders().set(name, value);
        return req;
    }

    protected Function<String, Integer> getJsonResponseContentLengthExtractor() {
        return isStreamingJsonEnabled() ? __ -> null : String::length;
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertNoResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                     final HttpResponseStatus expectedStatus) {
        return sendAndAssertResponse(req, expectedStatus, null, "");
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertStatusOnly(final HttpRequest<HttpPayloadChunk> req,
                                                                     final HttpResponseStatus expectedStatus) {
        final HttpResponse<HttpPayloadChunk> res =
                sendAndAssertStatus(req, HTTP_1_1, expectedStatus, DEFAULT_TIMEOUT_SECONDS, SECONDS);
        getContentAsString(res);
        return res;
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                   final HttpResponseStatus expectedStatus,
                                                                   @Nullable final CharSequence expectedContentType,
                                                                   final String expectedContent) {
        return sendAndAssertResponse(req, expectedStatus, expectedContentType, is(expectedContent),
                __ -> expectedContent.length());
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                   final HttpResponseStatus expectedStatus,
                                                                   @Nullable final CharSequence expectedContentType,
                                                                   final Matcher<String> contentMatcher,
                                                                   final int expectedContentLength) {
        return sendAndAssertResponse(req, expectedStatus, expectedContentType, contentMatcher,
                __ -> expectedContentLength);
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                   final HttpResponseStatus expectedStatus,
                                                                   @Nullable final CharSequence expectedContentType,
                                                                   final Matcher<String> contentMatcher,
                                                                   final Function<String,
                                                                           Integer> expectedContentLengthExtractor) {
        return sendAndAssertResponse(req, HTTP_1_1, expectedStatus, expectedContentType, contentMatcher,
                expectedContentLengthExtractor);
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                   final HttpResponseStatuses expectedStatus,
                                                                   final CharSequence expectedContentType,
                                                                   final String expectedContent,
                                                                   final int timeout,
                                                                   final TimeUnit unit) {
        return sendAndAssertResponse(req, HTTP_1_1, expectedStatus, expectedContentType, is(expectedContent),
                __ -> expectedContent.length(), timeout, unit);
    }

    protected HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                   final HttpProtocolVersion expectedHttpVersion,
                                                                   final HttpResponseStatus expectedStatus,
                                                                   @Nullable final CharSequence expectedContentType,
                                                                   final Matcher<String> contentMatcher,
                                                                   final Function<String,
                                                                           Integer> expectedContentLengthExtractor) {
        return sendAndAssertResponse(req, expectedHttpVersion, expectedStatus, expectedContentType, contentMatcher,
                expectedContentLengthExtractor, DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    private HttpResponse<HttpPayloadChunk> sendAndAssertResponse(final HttpRequest<HttpPayloadChunk> req,
                                                                 final HttpProtocolVersion expectedHttpVersion,
                                                                 final HttpResponseStatus expectedStatus,
                                                                 @Nullable final CharSequence expectedContentType,
                                                                 final Matcher<String> contentMatcher,
                                                                 final Function<String,
                                                                         Integer> expectedContentLengthExtractor,
                                                                 final int timeout,
                                                                 final TimeUnit unit) {
        final HttpResponse<HttpPayloadChunk> res =
                sendAndAssertStatus(req, expectedHttpVersion, expectedStatus, timeout, unit);

        if (expectedContentType != null) {
            assertThat(res.getHeaders().get(CONTENT_TYPE), is(expectedContentType));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_TYPE), is(false));
        }

        final String contentAsString = getContentAsString(res);

        @Nullable
        final Integer expectedContentLength = expectedContentLengthExtractor.apply(contentAsString);
        if (expectedContentLength != null) {
            assertThat(res.getHeaders().get(CONTENT_LENGTH),
                    is(newAsciiString(Integer.toString(expectedContentLength))));
            res.getHeaders().getAll(TRANSFER_ENCODING)
                    .forEachRemaining(h -> assertThat(h.toString(), equalToIgnoringCase("chunked")));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_LENGTH), is(false));
            if (res.getStatus().getCode() >= 200 && res.getStatus().getCode() != 204 &&
                    // It is OK to omit payload header fields in HEAD responses
                    !req.getMethod().equals(HEAD)) {
                assertThat(res.getHeaders().get(TRANSFER_ENCODING), is(CHUNKED));
            }
        }

        assertThat(contentAsString, contentMatcher);
        return res;
    }

    private HttpResponse<HttpPayloadChunk> sendAndAssertStatus(final HttpRequest<HttpPayloadChunk> req,
                                                               final HttpProtocolVersion expectedHttpVersion,
                                                               final HttpResponseStatus expectedStatus,
                                                               final int timeout,
                                                               final TimeUnit unit) {

        assert httpClient != null : "clientConnection can't be null";

        try {
            final HttpResponse<HttpPayloadChunk> res = awaitNonNull(httpClient.request(req), timeout, unit);

            assertThat(res.getVersion(), is(expectedHttpVersion));
            final HttpResponseStatus status = res.getStatus();
            assertThat(status.getCode(), is(expectedStatus.getCode()));
            final Buffer reasonPhrase = DEFAULT_ALLOCATOR.newBuffer();
            status.writeReasonPhraseTo(reasonPhrase);
            final Buffer expectedReasonPhrase = DEFAULT_ALLOCATOR.newBuffer();
            expectedStatus.writeReasonPhraseTo(expectedReasonPhrase);
            assertThat(reasonPhrase, is(expectedReasonPhrase));
            return res;
        } catch (final AssertionError ae) {
            throw ae;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
