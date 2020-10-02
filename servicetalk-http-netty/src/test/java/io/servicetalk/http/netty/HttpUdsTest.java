/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class HttpUdsTest {
    private static IoExecutor ioExecutor;

    @BeforeClass
    public static void beforeClass() {
        ioExecutor = createIoExecutor(new IoThreadFactory("io-executor"));
    }

    @AfterClass
    public static void afterClass() throws ExecutionException, InterruptedException {
        ioExecutor.closeAsync().toFuture().get();
    }

    @Test
    public void udsRoundTrip() throws Exception {
        assumeTrue(ioExecutor.isUnixDomainSocketSupported());
        try (ServerContext serverContext = HttpServers.forAddress(newSocketAddress()).ioExecutor(ioExecutor)
                             .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverContext.listenAddress())
                    .ioExecutor(ioExecutor).buildBlocking()) {
                assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
            }
        }
    }

    static DomainSocketAddress newSocketAddress() throws IOException {
        File file = File.createTempFile("STUDS", ".uds");
        assertTrue(file.delete());
        return new DomainSocketAddress(file);
    }
}
