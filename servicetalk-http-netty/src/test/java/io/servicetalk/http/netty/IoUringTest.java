/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.IoUringUtils;
import io.servicetalk.transport.netty.internal.NettyIoExecutors;

import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

class IoUringTest {

    @Test
    @EnabledOnOs(value = { MAC })
    void ioUringIsNotAvailableOnMacOs() {
        assertFalse(IOUring.isAvailable());
        try {
            IoUringUtils.tryIoUring(false);
            assertFalse(IoUringUtils.isAvailable());
            IoUringUtils.tryIoUring(true);
            assertFalse(IoUringUtils.isAvailable());
        } finally {
            IoUringUtils.tryIoUring(false);
        }
    }

    @Test
    @EnabledOnOs(value = { LINUX })
    void ioUringIsAvailableOnLinux() throws Exception {
        EventLoopAwareNettyIoExecutor ioUringExecutor = null;
        try {
            IoUringUtils.tryIoUring(true);
            assertTrue(IoUringUtils.isAvailable());
            IOUring.ensureAvailability();

            ioUringExecutor = NettyIoExecutors.createIoExecutor(2, "io-uring");
            assertThat(ioUringExecutor.eventLoopGroup(), is(instanceOf(IOUringEventLoopGroup.class)));

            try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                    .ioExecutor(ioUringExecutor)
                    .listenStreamingAndAwait(new TestServiceStreaming());
                 BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                         .ioExecutor(ioUringExecutor)
                         .buildBlocking()) {
                HttpRequest request = client.post(SVC_ECHO).payloadBody("bonjour!", textSerializerUtf8());
                HttpResponse response = client.request(request);
                assertThat(response.status(), is(OK));
                assertThat(response.payloadBody(textSerializerUtf8()), is("bonjour!"));
            }
        } finally {
            IoUringUtils.tryIoUring(false);
            if (ioUringExecutor != null) {
                ioUringExecutor.closeAsync().toFuture().get();
                assertTrue(ioUringExecutor.eventLoopGroup().isShutdown());
            }
        }
    }
}
