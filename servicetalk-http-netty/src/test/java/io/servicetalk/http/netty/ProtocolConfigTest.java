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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.rules.ExpectedException.none;

public class ProtocolConfigTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void clientDoesNotSupportH2cUpgrade() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        HttpClients.forSingleAddress("localhost", 8080)
                .protocols(h2Default(), h1Default())
                .build();
    }

    @Test
    public void serverDoesNotSupportH2cUpgrade() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        HttpServers.forAddress(localAddress(0))
                .protocols(h2Default(), h1Default())
                .listenBlocking((ctx, request, responseFactory) -> responseFactory.noContent());
    }

    @Test
    public void clientWithUnsupportedProtocolConfig() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("No supported HTTP configuration provided");
        HttpClients.forSingleAddress("localhost", 8080)
                .protocols(() -> "unknown");
    }

    @Test
    public void serverWithUnsupportedProtocolConfig() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("No supported HTTP configuration provided");
        HttpServers.forAddress(localAddress(0))
                .protocols(() -> "unknown");
    }
}
