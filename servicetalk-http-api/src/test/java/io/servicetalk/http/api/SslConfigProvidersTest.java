/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;

import static io.servicetalk.http.api.HttpUri.HTTPS_SCHEME;
import static io.servicetalk.http.api.HttpUri.HTTPS_SCHEME_DEFAULT_PORT;
import static io.servicetalk.http.api.HttpUri.HTTP_SCHEME;
import static io.servicetalk.http.api.HttpUri.HTTP_SCHEME_DEFAULT_PORT;
import static io.servicetalk.http.api.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.api.SslConfigProviders.secureByDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SslConfigProvidersTest {

    @Test
    public void plainDefaultPortNullScheme() {
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(null, "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(null, null));
    }

    @Test
    public void plainDefaultPortHttpScheme() {
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(HTTP_SCHEME, "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(HTTP_SCHEME, null));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("http", "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("http", null));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("hTTp", "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("hTTp", null));
    }

    @Test
    public void plainDefaultPortHttpsScheme() {
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(HTTPS_SCHEME, "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort(HTTPS_SCHEME, null));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("https", "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("https", null));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("hTTps", "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, plainByDefault().defaultPort("hTTps", null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void plainDefaultPortUnknownScheme() {
        plainByDefault().defaultPort("unknown", "test.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void plainDefaultPortUnknownSchemeNoHost() {
        plainByDefault().defaultPort("unknown", null);
    }

    @Test
    public void plainForHostAndPort() {
        assertNull(plainByDefault().forHostAndPort(HostAndPort.of("test.", HTTPS_SCHEME_DEFAULT_PORT)));
    }

    @Test
    public void secureDefaultPortNullScheme() {
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(null, "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(null, null));
    }

    @Test
    public void secureDefaultPortHttpScheme() {
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(HTTP_SCHEME, "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(HTTP_SCHEME, null));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("http", "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("http", null));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("hTTp", "test."));
        assertEquals(HTTP_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("hTTp", null));
    }

    @Test
    public void secureDefaultPortHttpsScheme() {
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(HTTPS_SCHEME, "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort(HTTPS_SCHEME, null));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("https", "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("https", null));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("hTTps", "test."));
        assertEquals(HTTPS_SCHEME_DEFAULT_PORT, secureByDefault().defaultPort("hTTps", null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void secureDefaultPortUnknownScheme() {
        secureByDefault().defaultPort("unknown", "test.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void secureDefaultPortUnknownSchemeNoHost() {
        secureByDefault().defaultPort("unknown", null);
    }

    @Test
    public void secureForHostAndPort() {
        assertNotNull(secureByDefault().forHostAndPort(HostAndPort.of("test.", HTTPS_SCHEME_DEFAULT_PORT)));
    }
}
