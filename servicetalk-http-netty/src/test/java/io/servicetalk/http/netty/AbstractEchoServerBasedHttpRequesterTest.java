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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.core.IsEqual.equalTo;

public abstract class AbstractEchoServerBasedHttpRequesterTest {

    @RegisterExtension
    static final ExecutionContextExtension CTX = ExecutionContextExtension.immediate().setClassLevel(true);

    static ServerContext serverContext;

    @BeforeAll
    public static void startServer() throws Exception {
        serverContext = forAddress(localAddress(0))
            .ioExecutor(CTX.ioExecutor())
            .executionStrategy(noOffloadsStrategy())
            .listenStreamingAndAwait(new EchoServiceStreaming());
    }

    @AfterAll
    public static void stopServer() throws Exception {
        serverContext.closeAsync().toFuture().get();
    }

    private static class EchoServiceStreaming implements StreamingHttpService {

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
            return succeeded(resp);
        }
    }

    static void makeRequestValidateResponseAndClose(StreamingHttpRequester requester)
        throws ExecutionException, InterruptedException {
        try {
            StreamingHttpRequest request = requester.get("/request?foo=bar&foo=baz").payloadBody(
                from(DEFAULT_ALLOCATOR.fromAscii("Testing123")));
            request.headers().set(HOST, "mock.servicetalk.io");

            StreamingHttpResponse resp = awaitIndefinitelyNonNull(
                requester.request(defaultStrategy(), request)
                    .retryWhen(
                        retryWithExponentialBackoffFullJitter(10, t -> true, ofMillis(100), ofDays(10),
                                                              CTX.executor())));

            assertThat(resp.status(), equalTo(OK));

            Single<String> respBody = resp.payloadBody().collect(StringBuilder::new, (sb, buf) -> {
                sb.append(buf.toString(UTF_8));
                return sb;
            }).map(StringBuilder::toString);

            HttpHeaders headers = resp.headers();
            assertThat(headers.get("test-req-method"), hasToString(GET.toString()));
            assertThat(headers.get("test-req-target"), hasToString("/request?foo=bar&foo=baz"));
            assertThat(headers.get("test-req-header-host"), hasToString("mock.servicetalk.io"));
            assertThat(headers.get("test-req-header-transfer-encoding"), equalTo(CHUNKED));
            assertThat(respBody.toFuture().get(), equalTo("Testing123"));
        } finally {
            requester.closeAsync().toFuture().get();
        }
    }
}
