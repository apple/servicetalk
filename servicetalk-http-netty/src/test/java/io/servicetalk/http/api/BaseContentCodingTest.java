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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.encoding.api.ContentCodec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static io.servicetalk.encoding.api.ContentCodings.deflateDefault;
import static io.servicetalk.encoding.api.ContentCodings.gzipDefault;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.http.api.BaseContentCodingTest.Expectation.SHOULD_FAIL;
import static io.servicetalk.http.api.BaseContentCodingTest.Expectation.SHOULD_PASS;
import static io.servicetalk.http.api.BaseContentCodingTest.Protocol.H1;
import static io.servicetalk.http.api.BaseContentCodingTest.Protocol.H2;
import static io.servicetalk.http.api.BaseContentCodingTest.Scenario.when;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public abstract class BaseContentCodingTest {

    private static final int PAYLOAD_SIZE = 1024;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    protected final Scenario scenario;
    private final boolean expectedSuccess;

    public BaseContentCodingTest(final Scenario scenario) {
        this.scenario = scenario;
        this.expectedSuccess = scenario.valid;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[] {
                when(H1, Server.DEFAULT, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H2, Server.DEFAULT, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H1, Server.DEFAULT, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H2, Server.DEFAULT, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H1, Server.DEFAULT, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H2, Server.DEFAULT, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H1, Server.GZIP_DEFLATE_ID, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H2, Server.GZIP_DEFLATE_ID, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H1, Server.ID_GZIP_DEFLATE, Client.GZIP_ID, Request.GZIP, SHOULD_PASS),
                when(H2, Server.ID_GZIP_DEFLATE, Client.GZIP_ID, Request.GZIP, SHOULD_PASS),
                when(H1, Server.ID_GZIP_DEFLATE, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_PASS),
                when(H2, Server.ID_GZIP_DEFLATE, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_PASS),
                when(H1, Server.ID_GZIP, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H2, Server.ID_GZIP, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H1, Server.ID_DEFLATE, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H2, Server.ID_DEFLATE, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H1, Server.ID_DEFLATE, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_PASS),
                when(H2, Server.ID_DEFLATE, Client.DEFLATE_ID, Request.DEFLATE, SHOULD_PASS),
                when(H1, Server.ID_DEFLATE, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H2, Server.ID_DEFLATE, Client.DEFAULT, Request.ID, SHOULD_PASS),
                when(H1, Server.GZIP_ONLY, Client.ID_ONLY, Request.ID, SHOULD_PASS),
                when(H2, Server.GZIP_ONLY, Client.ID_ONLY, Request.ID, SHOULD_PASS),
                when(H1, Server.GZIP_ONLY, Client.GZIP_ID, Request.ID, SHOULD_PASS),
                when(H2, Server.GZIP_ONLY, Client.GZIP_ID, Request.ID, SHOULD_PASS),
                when(H1, Server.GZIP_ONLY, Client.GZIP_ID, Request.ID, SHOULD_PASS),
                when(H2, Server.GZIP_ONLY, Client.GZIP_ID, Request.ID, SHOULD_PASS),
                when(H1, Server.GZIP_ONLY, Client.GZIP_ID, Request.GZIP, SHOULD_PASS),
                when(H2, Server.GZIP_ONLY, Client.GZIP_ID, Request.GZIP, SHOULD_PASS),
                when(H1, Server.DEFAULT, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H2, Server.DEFAULT, Client.GZIP_ID, Request.GZIP, SHOULD_FAIL),
                when(H1, Server.DEFAULT, Client.GZIP_DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H2, Server.DEFAULT, Client.GZIP_DEFLATE_ID, Request.DEFLATE, SHOULD_FAIL),
                when(H1, Server.DEFAULT, Client.GZIP_ID, Request.ID, SHOULD_PASS),
                when(H2, Server.DEFAULT, Client.GZIP_ID, Request.ID, SHOULD_PASS),
        };
    }

    @Test
    public void testCompatibility() throws Exception {
        if (expectedSuccess) {
            assertSuccessful(scenario.requestEncoding);
        } else {
            assertNotSupported(scenario.requestEncoding);
        }
    }

    protected abstract void assertSuccessful(ContentCodec requestEncoding) throws Exception;

    protected abstract void assertNotSupported(ContentCodec requestEncoding) throws Exception;

    protected static byte[] payload(byte b) {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, b);
        return payload;
    }

    protected static String payloadAsString(byte b) {
        return new String(payload(b), StandardCharsets.US_ASCII);
    }

    protected enum Server {
        DEFAULT(emptyList()),
        GZIP_ONLY(singletonList(gzipDefault())),
        GZIP_ID(asList(gzipDefault(), identity())),
        GZIP_DEFLATE_ID(asList(gzipDefault(), deflateDefault(), identity())),
        ID_GZIP(asList(identity(), gzipDefault())),
        ID_DEFLATE(asList(identity(), deflateDefault())),
        ID_GZIP_DEFLATE(asList(identity(), gzipDefault(), deflateDefault())),
        DEFLATE_ONLY(singletonList(deflateDefault())),
        DEFLATE_ID(asList(deflateDefault(), identity()));

        List<ContentCodec> list;

        Server(List<ContentCodec> list) {
            this.list = list;
        }
    }

    protected enum Client {
        DEFAULT(emptyList()),
        GZIP_ONLY(singletonList(gzipDefault())),
        GZIP_ID(asList(gzipDefault(), identity())),
        GZIP_DEFLATE_ID(asList(gzipDefault(), deflateDefault(), identity())),
        ID_ONLY(singletonList(identity())),
        ID_GZIP(asList(identity(), gzipDefault())),
        ID_DEFLATE(asList(identity(), deflateDefault())),
        ID_GZIP_DEFLATE(asList(identity(), gzipDefault(), deflateDefault())),
        DEFLATE_ONLY(singletonList(deflateDefault())),
        DEFLATE_ID(asList(deflateDefault(), identity()));

        List<ContentCodec> list;

        Client(List<ContentCodec> list) {
            this.list = list;
        }
    }

    protected enum Request {
        ID(identity()),
        GZIP(gzipDefault()),
        DEFLATE(deflateDefault());

        ContentCodec codec;

        Request(ContentCodec codec) {
            this.codec = codec;
        }
    }

    protected enum Expectation {
        SHOULD_PASS(true),
        SHOULD_FAIL(false);

        boolean valid;
        Expectation(boolean valid) {
            this.valid = valid;
        }
    }

    protected enum Protocol {
        H1(h1Default()),
        H2(h2Default());

        HttpProtocolConfig config;

        Protocol(HttpProtocolConfig config) {
            this.config = config;
        }
    }

    protected static class Scenario {
        final ContentCodec requestEncoding;
        final List<ContentCodec> clientSupported;
        final List<ContentCodec> serverSupported;
        final HttpProtocolConfig protocol;
        final boolean valid;
        final boolean isH2;

        Scenario(final ContentCodec requestEncoding,
                 final List<ContentCodec> clientSupported, final List<ContentCodec> serverSupported,
                 final Protocol protocol, final boolean valid) {
            this.requestEncoding = requestEncoding;
            this.clientSupported = clientSupported;
            this.serverSupported = serverSupported;
            this.valid = valid;
            this.isH2 = protocol == H2;
            this.protocol = isH2 ? h2Default() : h1Default();
        }

        static Scenario when(final Protocol protocol, final Server server,
                             final Client client, final Request request, final Expectation expectation) {
            return new Scenario(request.codec, client.list, server.list, protocol, expectation.valid);
        }

        @Override
        public String toString() {
            return "When a client that supports " + encString(clientSupported) + ", sends a request encoded with "
                    + requestEncoding.name() + ", to an " + (isH2 ? "H2" : "H1") + " server that supports "
                    + encString(serverSupported) + ", the request should be " + (valid ? "ACCEPTED" : "REJECTED");
        }

        private String encString(List<ContentCodec> codecs) {
            if (codecs.isEmpty()) {
                return "only identity";
            }

            if (codecs.size() == 1) {
                return "only " + codecs.get(0).name();
            }

            StringBuilder b = new StringBuilder();
            b.append("[");
            for (ContentCodec c : codecs) {
                if (b.length() > 1) {
                    b.append(", ");
                }

                b.append(c.name());
            }
            b.append("]");
            return b.toString();
        }
    }
}
