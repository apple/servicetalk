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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;

import io.netty.util.CharsetUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.StandardSocketOptions;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Boolean.parseBoolean;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

public class NettyHttpServerConnectionDrainTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final String LARGE_TEXT;

    static {
        int capacity = 1_000_000;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(capacity);
        for (int i = 0; i < capacity; i++) {
            sb.append(rnd.nextInt(32, 128)); // ASCII
        }
        LARGE_TEXT = sb.toString();
    }

    @Test
    public void requestIsAutoDrainedWhenUserFailsToConsume() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(AddressUtils.localAddress(0))
                // Ensures small TCP buffer such that our large request payload will fill up to make sure we can't
                // complete the request without at least reading more data
                .socketOption(StandardSocketOptions.SO_RCVBUF, 64)
                .enableDrainingRequestPayloadBody() // This is enabled by default
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    // User fails to consume payload, expect auto-draining to take care of it
                    return succeeded(responseFactory.ok().payloadBody(from("OK"), textSerializer()));
                });

             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .buildBlocking()) {

            HttpResponse response = client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializer()));
            assertThat(response.payloadBody(textDeserializer()), equalTo("OK"));
        }
    }

    @Test
    public void requestIsDrainedByUserWithDrainingDisabled() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(AddressUtils.localAddress(0))
                // Ensures small TCP buffer such that our large request payload will fill up to make sure we can't
                // complete the request without at least reading more data
                .socketOption(StandardSocketOptions.SO_RCVBUF, 64)
                // No auto-draining of payload, user needs to drain
                .disableDrainingRequestPayloadBody()
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        request.payloadBody().ignoreElements() // User consumes payload (ignoring)
                                .concat(succeeded(responseFactory.ok().payloadBody(from("OK"), textSerializer()))));

             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .buildBlocking()) {

            HttpResponse response = client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializer()));
            assertThat(response.payloadBody(textDeserializer()), equalTo("OK"));
        }
    }

    @Test
    public void requestIsConsumedByUserWithDrainingEnabled() throws Exception {
        AtomicReference<String> resultRef = new AtomicReference<>();
        try (ServerContext serverContext = HttpServers.forAddress(AddressUtils.localAddress(0))
                // Ensures small TCP buffer such that our large request payload will fill up to make sure we can't
                // complete the request without at least reading more data
                .socketOption(StandardSocketOptions.SO_RCVBUF, 64)
                .enableDrainingRequestPayloadBody() // This is enabled by default, should not cause issues for the user
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        request.payloadBody()
                                // User consumes payload and stores for assert ensuring auto-draining doesn't break it
                                .collect(() -> new StringBuilder(LARGE_TEXT.length()),
                                        (sb, b) -> sb.append(b.toString(CharsetUtil.US_ASCII)))
                                .map(StringBuilder::toString)
                                .whenOnSuccess(resultRef::set)
                                .toCompletable()
                                .concat(succeeded(responseFactory.ok().payloadBody(from("OK"), textSerializer()))));

             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .buildBlocking()) {

            HttpResponse response = client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializer()));
            assertThat(response.payloadBody(textDeserializer()), equalTo("OK"));
            assertThat(resultRef.get(), equalTo(LARGE_TEXT));
        }
    }

    @Test(expected = TimeoutException.class)
    public void requestTimesOutWithoutAutoDrainingOrUserConsuming() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(AddressUtils.localAddress(0))
                // Ensures small TCP buffer such that our large request payload will fill up to make sure we can't
                // complete the request without at least reading more data
                .socketOption(StandardSocketOptions.SO_RCVBUF, 64)
                // With draining disabled the request hangs
                .disableDrainingRequestPayloadBody()
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(from("OK"), textSerializer())));

             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .appendClientFilter((client1, lbEvents) -> new StreamingHttpClientFilter(client1) {
                         @Override
                         protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                         final HttpExecutionStrategy strategy,
                                                                         final StreamingHttpRequest request) {
                             // Without draining the request is expected to hang, don't wait too long unless on CI
                             int timeoutSeconds = ServiceTalkTestTimeout.CI ? 15 : 1;
                             return delegate.request(strategy, request).idleTimeout(timeoutSeconds, SECONDS);
                         }
                     })
                     .buildBlocking()) {

            client.request(client.post("/").payloadBody(LARGE_TEXT, textSerializer()));
        }
        fail("Request should not complete normally");
    }
}
