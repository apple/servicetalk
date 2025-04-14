/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class WatchdogLeakDetectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchdogLeakDetectorTest.class);
    private static final int ITERATIONS = 10;

    private static boolean leakDetected;

    static {
        System.setProperty("io.servicetalk.http.netty.leakdetection", "strict");
        System.setProperty("io.netty.leakDetection.level", "paranoid");
        ByteBufUtil.setLeakListener((type, records) -> {
            leakDetected = true;
            LOGGER.error("ByteBuf leak detected!");
        });
    }

    @Test
    void orphanedServiceRequestPublisher() throws Exception {
        // TODO: this fails with HTTP/1.1 as we hang on the `client.request(..).toFuture().get()` call.
        HttpProtocolConfig config = HttpProtocolConfigs.h2Default();
        try (HttpServerContext serverContext = HttpServers.forPort(0)
                        .protocols(config)
                                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                                    abandon(request.messageBody());
                                    return Single.succeeded(responseFactory.ok());
                                })) {
            try (HttpClient client = HttpClients.forSingleAddress("localhost",
                    ((InetSocketAddress) serverContext.listenAddress()).getPort()).protocols(config).build()) {
                for (int i = 0; i < ITERATIONS && !leakDetected; i++) {
                    HttpResponse response = client.request(client.post("/foo")
                            .payloadBody(payload())).toFuture().get();
                    assertThat(response.status(), equalTo(HttpResponseStatus.OK));

                    System.gc();
                    System.runFinalization();
                }
            }
        }
        assertFalse(leakDetected);
    }

    @Test
    void orphanClientResponsePublisher() throws Exception {
        // TODO: this succeeds with or without strict detection.
        HttpProtocolConfig config = HttpProtocolConfigs.h2Default();
        try (HttpServerContext serverContext = HttpServers.forPort(0)
                .protocols(config)
                .listenAndAwait((ctx, request, responseFactory) ->
                    Single.succeeded(responseFactory.ok().payloadBody(payload())))) {
            try (StreamingHttpClient client = HttpClients.forSingleAddress("localhost",
                    ((InetSocketAddress) serverContext.listenAddress()).getPort()).
                    protocols(config).build().asStreamingClient()) {
                for (int i = 0; i < ITERATIONS && !leakDetected; i++) {
                    StreamingHttpResponse response = client.request(client.get("/foo")).toFuture().get();
                    assertThat(response.status(), equalTo(HttpResponseStatus.OK));
                    abandon(response.messageBody());
                    response = null;

                    System.gc();
                    System.runFinalization();
                }
            }
        }
        assertFalse(leakDetected);
    }

    private static Buffer payload() {
        return BufferAllocators.DEFAULT_ALLOCATOR.fromAscii("Hello, world!");
    }

    private static void abandon(Publisher<?> messageBody) {
        SourceAdapters.toSource(messageBody).subscribe(new PublisherSource.Subscriber<Object>() {
            @Override
            public void onSubscribe(PublisherSource.Subscription subscription) {
            }

            @Override
            public void onNext(@Nullable Object o) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
    }
}
