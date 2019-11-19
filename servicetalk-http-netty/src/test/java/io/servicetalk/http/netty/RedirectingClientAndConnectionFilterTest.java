/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.AbstractHttpRequesterFilterTest;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;
import java.util.function.Predicate;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.TestStrategyInfluencerAwareConversions.toConditionalClientFilterFactory;
import static io.servicetalk.http.api.TestStrategyInfluencerAwareConversions.toConditionalConnectionFilterFactory;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static java.lang.String.format;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * This test-case is for integration testing the {@link RedirectingHttpRequesterFilter} with the various types
 * of {@link HttpClient} and {@link HttpConnection} builders.
 */
@RunWith(Parameterized.class)
public final class RedirectingClientAndConnectionFilterTest extends AbstractHttpRequesterFilterTest {

    private SSLSession session;

    public RedirectingClientAndConnectionFilterTest(final RequesterType type, final SecurityType security) {
        super(type, security);
    }

    @Before
    public void setUp() {
        session = mock(SSLSession.class);
    }

    @Override
    protected SSLSession sslSession() {
        return session;
    }

    @Test
    public void redirectFilterNoHostHeaderRelativeLocation() throws Exception {
        BlockingHttpRequester client = asBlockingRequester(createFilter((responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect().addHeader(LOCATION, "/next"));
            }
            return succeeded(responseFactory.ok());

        }, new RedirectingHttpRequesterFilterFactory(new RedirectingHttpRequesterFilter(), remoteAddress())));

        HttpRequest request = client.get("/");
        HttpResponse response = client.request(noOffloadsStrategy(), request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));

        // HTTP/1.0 doesn't support HOST, ensure that we don't get any errors and fallback to redirect
        response = client.request(noOffloadsStrategy(),
                client.get("/")
                        .version(HTTP_1_0)
                        .addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));
    }

    @Test
    public void redirectFilterNoHostHeaderAbsoluteLocation() throws Exception {
        BlockingHttpRequester client = asBlockingRequester(createFilter((responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect().addHeader(LOCATION,
                        format("http://%s/next", hostHeader(HostAndPort.of(remoteAddress())))));
            }
            return succeeded(responseFactory.ok());
        }, new RedirectingHttpRequesterFilterFactory(new RedirectingHttpRequesterFilter(), remoteAddress())));
        HttpRequest request = client.get("/");
        HttpResponse response = client.request(noOffloadsStrategy(), request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));

        // HTTP/1.0 doesn't support HOST, ensure that we don't get any errors and fallback to redirect
        response = client.request(noOffloadsStrategy(),
                client.get("/")
                        .version(HTTP_1_0)
                        .addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));
    }

    @Test
    public void redirectFilterWithHostHeaderRelativeLocation() throws Exception {

        BlockingHttpRequester client = asBlockingRequester(createFilter((responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect()
                        .addHeader(LOCATION, "/next"));
            }
            return succeeded(responseFactory.ok());
        }, new RedirectingHttpRequesterFilterFactory(new RedirectingHttpRequesterFilter(), remoteAddress())));
        HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io");
        HttpResponse response = client.request(noOffloadsStrategy(), request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));
    }

    @Test
    public void redirectFilterWithHostHeaderAbsoluteLocation() throws Exception {

        BlockingHttpRequester client = asBlockingRequester(createFilter((responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect()
                        .addHeader(LOCATION, "http://servicetalk.io/next"));
            }
            return succeeded(responseFactory.ok());
        }, new RedirectingHttpRequesterFilterFactory(new RedirectingHttpRequesterFilter(), remoteAddress())));
        HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io");
        HttpResponse response = client.request(noOffloadsStrategy(), request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));
    }

    private static final class RedirectingHttpRequesterFilterFactory
            implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {
        private final Predicate<StreamingHttpRequest> predicate = request -> request.headers().contains("X-REDIRECT");
        private final HostHeaderHttpRequesterFilter hostRedirect;
        private final RedirectingHttpRequesterFilter redirectingFilter;

        RedirectingHttpRequesterFilterFactory(final RedirectingHttpRequesterFilter redirectingFilter,
                                              final InetSocketAddress fallbackHost) {
            this.redirectingFilter = redirectingFilter;
            this.hostRedirect = new HostHeaderHttpRequesterFilter(HostAndPort.of(fallbackHost).toString());
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient original) {
            return hostRedirect.create(toConditionalClientFilterFactory(predicate, redirectingFilter).create(original));
        }

        @Override
        public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection original) {
            return hostRedirect.create(
                    toConditionalConnectionFilterFactory(predicate, redirectingFilter).create(original));
        }
    }
}
