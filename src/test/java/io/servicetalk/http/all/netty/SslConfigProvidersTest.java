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
package io.servicetalk.http.all.netty;

import io.servicetalk.transport.api.DefaultHostAndPort;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;

import static io.servicetalk.http.all.netty.HttpScheme.HTTP;
import static io.servicetalk.http.all.netty.HttpScheme.HTTPS;
import static io.servicetalk.http.all.netty.HttpScheme.NONE;
import static io.servicetalk.http.all.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.all.netty.SslConfigProviders.secureByDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class SslConfigProvidersTest {

    @Test
    public void testPlainByDefault() {
        assertEquals(HTTP.getDefaultPort(), plainByDefault().defaultPort(NONE, "test"));
        assertEquals(HTTP.getDefaultPort(), plainByDefault().defaultPort(HTTP, "test"));
        assertEquals(HTTPS.getDefaultPort(), plainByDefault().defaultPort(HTTPS, "test"));

        assertNull(plainByDefault().forHostAndPort(mock(HostAndPort.class)));
    }

    @Test
    public void testSecureByDefault() {
        assertEquals(HTTPS.getDefaultPort(), secureByDefault().defaultPort(NONE, "test"));
        assertEquals(HTTP.getDefaultPort(), secureByDefault().defaultPort(HTTP, "test"));
        assertEquals(HTTPS.getDefaultPort(), secureByDefault().defaultPort(HTTPS, "test"));

        assertNotNull(secureByDefault().forHostAndPort(new DefaultHostAndPort("test", HTTPS.getDefaultPort())));
    }
}
