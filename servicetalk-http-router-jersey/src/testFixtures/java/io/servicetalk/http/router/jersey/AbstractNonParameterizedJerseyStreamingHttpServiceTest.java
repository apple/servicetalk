/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder.toAggregated;
import static io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder.toBlocking;
import static io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder.toBlockingStreaming;
import static io.servicetalk.http.router.jersey.TestUtils.getContentAsString;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glassfish.jersey.CommonProperties.getValue;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;

public abstract class AbstractNonParameterizedJerseyStreamingHttpServiceTest {

    public enum RouterApi {
        ASYNC_AGGREGATED,
        ASYNC_STREAMING,
        BLOCKING_AGGREGATED,
        BLOCKING_STREAMING
    }

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached(new IoThreadFactory("stserverio"));

    protected final RouterApi api;

    private ServerContext serverContext;
    private String hostHeader;
    private boolean streamingJsonEnabled;
    private StreamingHttpClient httpClient;

    protected AbstractNonParameterizedJerseyStreamingHttpServiceTest(final RouterApi api) {
        this.api = api;
    }

    @SuppressWarnings("unused")
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.stream(RouterApi.values()).map(api -> new Object[]{api}).collect(Collectors.toList());
    }

    @Before
    public final void initServerAndClient() throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
        HttpJerseyRouterBuilder routerBuilder = new HttpJerseyRouterBuilder();
        configureBuilders(serverBuilder, routerBuilder);
        DefaultJerseyStreamingHttpRouter router = routerBuilder.from(application());
        final Configuration config = router.configuration();

        streamingJsonEnabled = getValue(config.getProperties(), config.getRuntimeType(), JSON_FEATURE, "",
                String.class).toLowerCase().contains("servicetalk");

        HttpServerBuilder httpServerBuilder = serverBuilder
                .ioExecutor(SERVER_CTX.ioExecutor())
                .bufferAllocator(SERVER_CTX.bufferAllocator());

        switch (api) {
            case ASYNC_AGGREGATED:
                serverContext = buildRouter(httpServerBuilder, toAggregated(router));
                break;
            case ASYNC_STREAMING:
                serverContext = buildRouter(httpServerBuilder, router);
                break;
            case BLOCKING_AGGREGATED:
                serverContext = buildRouter(httpServerBuilder, toBlocking(router));
                break;
            case BLOCKING_STREAMING:
                serverContext = buildRouter(httpServerBuilder, toBlockingStreaming(router));
                break;
            default:
                throw new IllegalArgumentException(api.name());
        }
        final HostAndPort hostAndPort = serverHostAndPort(serverContext);
        httpClient = HttpClients.forSingleAddress(hostAndPort).buildStreaming();
        hostHeader = hostHeader(hostAndPort);
    }

    protected ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                                        final HttpService router) throws Exception {
        return httpServerBuilder.listenAndAwait(router);
    }

    protected ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                                        final StreamingHttpService router) throws Exception {
        return httpServerBuilder.listenStreamingAndAwait(router);
    }

    protected ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                                        final BlockingHttpService router) throws Exception {
        return httpServerBuilder.listenBlockingAndAwait(router);
    }

    protected ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                                        final BlockingStreamingHttpService router) throws Exception {
        return httpServerBuilder.listenBlockingStreamingAndAwait(router);
    }

    protected void configureBuilders(final HttpServerBuilder serverBuilder,
                                     final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        serverBuilder.executionStrategy(defaultStrategy(SERVER_CTX.executor()));
    }

    @After
    public final void closeClient() throws Exception {
        httpClient.closeAsync().toFuture().get();
    }

    @After
    public final void closeServer() throws Exception {
        serverContext.closeAsync().toFuture().get();
    }

    protected abstract Application application();

    protected String host() {
        return hostHeader;
    }

    protected boolean isStreamingJsonEnabled() {
        return streamingJsonEnabled;
    }

    protected String testUri(final String path) {
        return path;
    }

    protected StreamingHttpRequest options(final String path) {
        return noPayloadRequest(OPTIONS, path);
    }

    protected StreamingHttpRequest head(final String path) {
        return noPayloadRequest(HEAD, path);
    }

    protected StreamingHttpRequest get(final String path) {
        return noPayloadRequest(GET, path);
    }

    protected StreamingHttpRequest post(final String path, final CharSequence payload,
                                        final CharSequence contentType) {
        return payloadRequest(POST, path, payload, contentType);
    }

    protected StreamingHttpRequest put(final String path, final CharSequence payload,
                                       final CharSequence contentType) {
        return payloadRequest(PUT, path, payload, contentType);
    }

    protected StreamingHttpRequest noPayloadRequest(final HttpRequestMethod method, final String path) {
        final StreamingHttpRequest req = httpClient.newRequest(method, testUri(path));
        req.headers().set(HOST, host());
        return req;
    }

    protected StreamingHttpRequest payloadRequest(final HttpRequestMethod method,
                                                  final String path,
                                                  final CharSequence payload,
                                                  final CharSequence contentType) {
        final Buffer content = DEFAULT_ALLOCATOR.fromUtf8(payload);
        final StreamingHttpRequest req = httpClient.newRequest(method, testUri(path)).payloadBody(from(content));
        req.headers().set(HOST, host());
        req.headers().set(CONTENT_TYPE, contentType);
        req.headers().set(CONTENT_LENGTH, Integer.toString(content.readableBytes()));
        return req;
    }

    protected StreamingHttpRequest withHeader(final StreamingHttpRequest req,
                                              final String name,
                                              final String value) {
        req.headers().set(name, value);
        return req;
    }

    protected Function<String, Integer> getJsonResponseContentLengthExtractor() {
        return isStreamingJsonEnabled() ? __ -> null : String::length;
    }

    protected StreamingHttpResponse sendAndAssertNoResponse(final StreamingHttpRequest req,
                                                            final HttpResponseStatus expectedStatus) {
        return sendAndAssertResponse(req, expectedStatus, null, "");
    }

    protected String sendAndAssertStatusOnly(final StreamingHttpRequest req,
                                             final HttpResponseStatus expectedStatus) {
        final StreamingHttpResponse res =
                sendAndAssertStatus(req, HTTP_1_1, expectedStatus, DEFAULT_TIMEOUT_SECONDS, SECONDS);
        return getContentAsString(res);
    }

    protected StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                          final HttpResponseStatus expectedStatus,
                                                          @Nullable final CharSequence expectedContentType,
                                                          final String expectedContent) {
        return sendAndAssertResponse(req, expectedStatus, expectedContentType, is(expectedContent),
                __ -> expectedContent.length());
    }

    protected StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                          final HttpResponseStatus expectedStatus,
                                                          @Nullable final CharSequence expectedContentType,
                                                          final Matcher<String> contentMatcher,
                                                          final int expectedContentLength) {
        return sendAndAssertResponse(req, expectedStatus, expectedContentType, contentMatcher,
                __ -> expectedContentLength);
    }

    protected StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                          final HttpResponseStatus expectedStatus,
                                                          @Nullable final CharSequence expectedContentType,
                                                          final Matcher<String> contentMatcher,
                                                          final Function<String,
                                                                  Integer> expectedContentLengthExtractor) {
        return sendAndAssertResponse(req, HTTP_1_1, expectedStatus, expectedContentType, contentMatcher,
                expectedContentLengthExtractor);
    }

    protected StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                          final HttpResponseStatus expectedStatus,
                                                          final CharSequence expectedContentType,
                                                          final String expectedContent,
                                                          final int timeout,
                                                          final TimeUnit unit) {
        return sendAndAssertResponse(req, HTTP_1_1, expectedStatus, expectedContentType, is(expectedContent),
                __ -> expectedContent.length(), timeout, unit);
    }

    protected StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                          final HttpProtocolVersion expectedHttpVersion,
                                                          final HttpResponseStatus expectedStatus,
                                                          @Nullable final CharSequence expectedContentType,
                                                          final Matcher<String> contentMatcher,
                                                          final Function<String,
                                                                  Integer> expectedContentLengthExtractor) {
        return sendAndAssertResponse(req, expectedHttpVersion, expectedStatus, expectedContentType, contentMatcher,
                expectedContentLengthExtractor, DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    private StreamingHttpResponse sendAndAssertResponse(final StreamingHttpRequest req,
                                                        final HttpProtocolVersion expectedHttpVersion,
                                                        final HttpResponseStatus expectedStatus,
                                                        @Nullable final CharSequence expectedContentType,
                                                        final Matcher<String> contentMatcher,
                                                        final Function<String,
                                                                Integer> expectedContentLengthExtractor,
                                                        final int timeout,
                                                        final TimeUnit unit) {
        final StreamingHttpResponse res =
                sendAndAssertStatus(req, expectedHttpVersion, expectedStatus, timeout, unit);

        if (expectedContentType != null) {
            assertThat(res.headers().get(CONTENT_TYPE), is(expectedContentType));
        } else {
            assertThat(res.headers().contains(CONTENT_TYPE), is(false));
        }

        final String contentAsString = getContentAsString(res);

        @Nullable
        final Integer expectedContentLength = expectedContentLengthExtractor.apply(contentAsString);
        if (expectedContentLength != null) {
            assertThat(res.headers().get(CONTENT_LENGTH),
                    is(newAsciiString(Integer.toString(expectedContentLength))));
            res.headers().valuesIterator(TRANSFER_ENCODING)
                    .forEachRemaining(h -> assertThat(h.toString(), equalToIgnoringCase("chunked")));
        } else {
            assertThat(res.headers().contains(CONTENT_LENGTH), is(false));
            if (SUCCESSFUL_2XX.contains(res.status()) && !NO_CONTENT.equals(res.status()) &&
                    // It is OK to omit payload header fields in HEAD responses
                    !HEAD.equals(req.method())) {
                assertThat(res.headers().get(TRANSFER_ENCODING), is(CHUNKED));
            }
        }

        assertThat(contentAsString, contentMatcher);
        return res;
    }

    private StreamingHttpResponse sendAndAssertStatus(final StreamingHttpRequest req,
                                                      final HttpProtocolVersion expectedHttpVersion,
                                                      final HttpResponseStatus expectedStatus,
                                                      final int timeout,
                                                      final TimeUnit unit) {
        try {
            final StreamingHttpResponse res = awaitNonNull(httpClient.request(req), timeout, unit);

            assertThat(res.version(), is(expectedHttpVersion));
            final HttpResponseStatus status = res.status();
            assertThat(status, is(expectedStatus));
            assertThat(status.reasonPhrase(), is(expectedStatus.reasonPhrase()));
            return res;
        } catch (final AssertionError ae) {
            throw ae;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Runs the provided {@code test} lambda multiple times.
     * <p>
     * some tests depend on Endpoint enhancement which is now backed by a cache, so we test the test code multiple times
     * to ensure that the caching of endpoints doesn't cause any weird side-effects.
     * @param test {@link Runnable} test callback will be executed multiple times, typically this is run from a @{@link
     * Test} within a single setup/teardown cycle
     */
    protected static void runTwiceToEnsureEndpointCache(final Runnable test) {
        test.run();
        test.run();
    }
}
