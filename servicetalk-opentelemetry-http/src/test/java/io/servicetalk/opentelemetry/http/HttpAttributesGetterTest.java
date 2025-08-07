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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.DomainSocketAddress;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.opentelemetry.http.HttpAttributesGetter.CLIENT_INSTANCE;
import static io.servicetalk.opentelemetry.http.HttpAttributesGetter.SERVER_INSTANCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class HttpAttributesGetterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_RO_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @Test
    void clientUrlExtractionNoHostAndPort() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), nullValue());
    }

    @Test
    void clientUrlExtractionHostHeader() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionNoLeadingSlashPath() {
        String pathQueryFrag = "foo";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice:8080/" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpNonDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice:8080" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:80");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpsDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:443");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("https://myservice" + pathQueryFrag));
    }

    @Test
    void clientAbsoluteUrl() {
        String requestTarget = "https://myservice/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(requestTarget);
        request.addHeader(HOST, "badservice"); // should be unused
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo(requestTarget));
    }

    @Test
    void attributesWithIpv4Connection() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress("192.168.1.1", 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // Network attributes
        for (NetworkAttributesGetter<RequestInfo, ?> getter : Arrays.asList(SERVER_INSTANCE, CLIENT_INSTANCE)) {
            assertThat(getter.getNetworkType(requestInfo, null), equalTo("ipv4"));
            assertThat(getter.getNetworkTransport(requestInfo, null), equalTo("tcp"));
            assertThat(getter.getNetworkPeerInetSocketAddress(requestInfo, null), equalTo(inetAddress));
            assertThat(getter.getNetworkPeerAddress(requestInfo, null), equalTo("192.168.1.1"));
            assertThat(getter.getNetworkPeerPort(requestInfo, null), equalTo(8080));
        }

        // Server attributes (client-side, fallback to connection)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("192.168.1.1"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));

        // Client attributes (server-side)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), equalTo("192.168.1.1"));
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), equalTo(8080));
    }

    @Test
    void attributesWithIpv6Connection() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress("::1", 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // Network attributes
        for (NetworkAttributesGetter<RequestInfo, ?> getter : Arrays.asList(SERVER_INSTANCE, CLIENT_INSTANCE)) {
            assertThat(getter.getNetworkType(requestInfo, null), equalTo("ipv6"));
            assertThat(getter.getNetworkTransport(requestInfo, null), equalTo("tcp"));
            assertThat(getter.getNetworkPeerInetSocketAddress(requestInfo, null), equalTo(inetAddress));
            assertThat(getter.getNetworkPeerAddress(requestInfo, null), equalTo("0:0:0:0:0:0:0:1"));
            assertThat(getter.getNetworkPeerPort(requestInfo, null), equalTo(8080));
        }

        // Server attributes (client-side, fallback to connection)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("0:0:0:0:0:0:0:1"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));

        // Client attributes (server-side)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), equalTo("0:0:0:0:0:0:0:1"));
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), equalTo(8080));
    }

    @Test
    void attributesWithUnixSocketConnection() {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        DomainSocketAddress unixAddress = new DomainSocketAddress("/tmp/socket");
        when(connectionInfo.remoteAddress()).thenReturn(unixAddress);
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // Network attributes
        for (NetworkAttributesGetter<RequestInfo, ?> getter : Arrays.asList(SERVER_INSTANCE, CLIENT_INSTANCE)) {
            assertThat(getter.getNetworkType(requestInfo, null), nullValue());
            assertThat(getter.getNetworkTransport(requestInfo, null), equalTo("unix"));
            assertThat(getter.getNetworkPeerInetSocketAddress(requestInfo, null), nullValue());
            assertThat(getter.getNetworkPeerAddress(requestInfo, null), equalTo("/tmp/socket"));
            assertThat(getter.getNetworkPeerPort(requestInfo, null), nullValue());
        }

        // Server attributes (client-side, fallback to connection)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("/tmp/socket"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), nullValue());

        // Client attributes (server-side)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), equalTo("/tmp/socket"));
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), nullValue());
    }

    @Test
    void attributesWithHostHeaderOnly() {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);

        // Network attributes (null without connection info)
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());

        // Network peer attributes (null without connection info)
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), nullValue());

        // Server attributes (client-side, from host header)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("example.com"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));

        // Client attributes (null without connection info)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), nullValue());
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), nullValue());
    }

    @Test
    void attributesWithHostHeaderAndIpv4Connection() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:9090");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress("192.168.1.1", 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // Network attributes (from connection)
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv4"));
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv4"));
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("tcp"));
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("tcp"));

        // Network peer attributes (from connection)
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), equalTo("192.168.1.1"));
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), equalTo(8080));

        // Server attributes (host header preferred for address, host header for port)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("example.com"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(9090));

        // Client attributes (from connection)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), equalTo("192.168.1.1"));
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), equalTo(8080));
    }

    @Test
    void attributesWithHostHeaderAndUnixSocketConnection() {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        DomainSocketAddress unixAddress = new DomainSocketAddress("/tmp/socket");
        when(connectionInfo.remoteAddress()).thenReturn(unixAddress);
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // Network attributes (from connection)
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("unix"));
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("unix"));

        // Network peer attributes (from connection)
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), equalTo("/tmp/socket"));
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), nullValue());

        // Server attributes (host header preferred for address, no port specified in host header returns -1)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("example.com"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(-1));

        // Client attributes (from connection)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), equalTo("/tmp/socket"));
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), nullValue());
    }

    @Test
    void attributesWithNoConnectionInfo() {
        StreamingHttpRequest request = newRequest("/path");
        RequestInfo requestInfo = new RequestInfo(request, null);

        // Network attributes (all null without connection)
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());

        // Network peer attributes (all null without connection)
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), nullValue());

        // Server attributes (all null without host header or connection)
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), nullValue());
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), nullValue());

        // Client attributes (null without connection)
        assertThat(SERVER_INSTANCE.getClientAddress(requestInfo), nullValue());
        assertThat(SERVER_INSTANCE.getClientPort(requestInfo), nullValue());
    }

    private static StreamingHttpRequest newRequest(String requestTarget) {
        return REQ_RES_FACTORY.newRequest(HttpRequestMethod.GET, requestTarget);
    }
}
