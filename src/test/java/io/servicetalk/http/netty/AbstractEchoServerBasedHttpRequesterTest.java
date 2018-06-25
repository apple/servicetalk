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
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;
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
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.nio.charset.StandardCharsets.US_ASCII;
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
                .start(CTX, 0, new EchoService()));
    }

    @AfterClass
    public static void stopServer() throws ExecutionException, InterruptedException {
        awaitIndefinitely(serverContext.closeAsync());
    }

    private static class EchoService extends HttpService {
        @Override
        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                             final HttpRequest<HttpPayloadChunk> request) {

            HttpResponse<HttpPayloadChunk> resp = newResponse(HTTP_1_1, OK, request.getPayloadBody());

            resp.getHeaders()
                    .set("test-req-target", request.getRequestTarget())
                    .set("test-req-method", request.getMethod().getName().toString(US_ASCII));

            HttpHeaders headers = resp.getHeaders();
            request.getHeaders().forEach(entry -> headers.set("test-req-header-" + entry.getKey(), entry.getValue()));
            return Single.success(resp);
        }
    }

    public static void makeRequestValidateResponseAndClose(HttpRequester requester)
            throws ExecutionException, InterruptedException {
        try {
            HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/request?foo=bar&foo=baz",
                    newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("Testing123")));
            request.getHeaders().set(HttpHeaderNames.HOST, "mock.servicetalk.io");

            HttpResponse<HttpPayloadChunk> resp = awaitIndefinitelyNonNull(requester.request(request).retryWhen(
                    retryWithExponentialBackoff(10, t -> true, Duration.ofMillis(100),
                            CTX.getExecutor())));

            assertThat(resp.getStatus().getCode(), equalTo(200));

            Single<String> respBody = aggregateChunks(resp.getPayloadBody(), CTX.getBufferAllocator())
                    .map(ch -> ch.getContent().toString(UTF_8));

            HttpHeaders headers = resp.getHeaders();
            assertThat(headers.get("test-req-method"), hasToString(GET.getName().toString(US_ASCII)));
            assertThat(headers.get("test-req-target"), hasToString("/request?foo=bar&foo=baz"));
            assertThat(headers.get("test-req-header-host"), hasToString("mock.servicetalk.io"));
            assertThat(headers.get("test-req-header-transfer-encoding"), equalTo(CHUNKED));
            assertThat(awaitIndefinitely(respBody), equalTo("Testing123"));
        } finally {
            awaitIndefinitely(requester.closeAsync());
        }
    }
}
