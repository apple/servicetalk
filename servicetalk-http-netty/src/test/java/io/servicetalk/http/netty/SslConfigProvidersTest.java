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

import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;

import static io.servicetalk.http.api.HttpScheme.HTTP;
import static io.servicetalk.http.api.HttpScheme.HTTPS;
import static io.servicetalk.http.api.HttpScheme.NONE;
import static io.servicetalk.http.api.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.api.SslConfigProviders.secureByDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class SslConfigProvidersTest {

    @Test
    public void testPlainByDefault() {
        assertEquals(HTTP.defaultPort(), plainByDefault().defaultPort(NONE, "test"));
        assertEquals(HTTP.defaultPort(), plainByDefault().defaultPort(HTTP, "test"));
        assertEquals(HTTPS.defaultPort(), plainByDefault().defaultPort(HTTPS, "test"));

        assertNull(plainByDefault().forHostAndPort(mock(HostAndPort.class)));
    }

    @Test
    public void testSecureByDefault() {
        assertEquals(HTTPS.defaultPort(), secureByDefault().defaultPort(NONE, "test"));
        assertEquals(HTTP.defaultPort(), secureByDefault().defaultPort(HTTP, "test"));
        assertEquals(HTTPS.defaultPort(), secureByDefault().defaultPort(HTTPS, "test"));

        assertNotNull(secureByDefault().forHostAndPort(HostAndPort.of("test", HTTPS.defaultPort())));
    }
}
