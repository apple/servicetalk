/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.NettyIoThreadFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HttpUdsTest {
    private static IoExecutor ioExecutor;

    @BeforeAll
    static void beforeClass() {
        ioExecutor = createIoExecutor(new NettyIoThreadFactory("io-executor"));
    }

    @AfterAll
    static void afterClass() throws ExecutionException, InterruptedException {
        ioExecutor.closeAsync().toFuture().get();
    }

    @Test
    void udsRoundTrip() throws Exception {
        assumeTrue(ioExecutor.isUnixDomainSocketSupported());
        try (ServerContext serverContext = HttpServers.forAddress(newSocketAddress()).ioExecutor(ioExecutor)
                             .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverContext.listenAddress())
                    .ioExecutor(ioExecutor).buildBlocking()) {
                assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
            }
        }
    }
}
