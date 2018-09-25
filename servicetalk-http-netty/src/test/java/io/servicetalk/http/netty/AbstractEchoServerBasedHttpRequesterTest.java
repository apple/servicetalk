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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.core.IsEqual.equalTo;

public abstract class AbstractEchoServerBasedHttpRequesterTest {

    @Rule
    public final Timeout serviceTalkTestTimeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    protected static ServerContext serverContext;

    @BeforeClass
    public static void startServer() throws ExecutionException, InterruptedException {
        serverContext = awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                .startStreaming(CTX, 0, new EchoServiceStreaming()));
    }

    @AfterClass
    public static void stopServer() throws ExecutionException, InterruptedException {
        awaitIndefinitely(serverContext.closeAsync());
    }

    private static class EchoServiceStreaming extends StreamingHttpService {
        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {

            StreamingHttpResponse resp = factory.ok().payloadBody(request.payloadBody());

            resp.headers()
                    .set("test-req-target", request.requestTarget())
                    .set("test-req-method", request.method().toString());

            HttpHeaders headers = resp.headers();
            request.headers().forEach(entry -> headers.set("test-req-header-" + entry.getKey(), entry.getValue()));
            return Single.success(resp);
        }
    }

    public static void makeRequestValidateResponseAndClose(StreamingHttpRequester requester)
            throws ExecutionException, InterruptedException {
        try {
            StreamingHttpRequest request = requester.get("/request?foo=bar&foo=baz").payloadBody(
                    just(DEFAULT_ALLOCATOR.fromAscii("Testing123")));
            request.headers().set(HttpHeaderNames.HOST, "mock.servicetalk.io");

            StreamingHttpResponse resp = awaitIndefinitelyNonNull(requester.request(request).retryWhen(
                    retryWithExponentialBackoff(10, t -> true, Duration.ofMillis(100),
                            CTX.executor())));

            assertThat(resp.status().code(), equalTo(200));

            Single<String> respBody = resp.payloadBody().reduce(StringBuilder::new, (sb, buf) -> {
                sb.append(buf.toString(UTF_8));
                return sb;
            }).map(StringBuilder::toString);

            HttpHeaders headers = resp.headers();
            assertThat(headers.get("test-req-method"), hasToString(GET.toString()));
            assertThat(headers.get("test-req-target"), hasToString("/request?foo=bar&foo=baz"));
            assertThat(headers.get("test-req-header-host"), hasToString("mock.servicetalk.io"));
            assertThat(headers.get("test-req-header-transfer-encoding"), equalTo(CHUNKED));
            assertThat(awaitIndefinitely(respBody), equalTo("Testing123"));
        } finally {
            awaitIndefinitely(requester.closeAsync());
        }
    }
}
