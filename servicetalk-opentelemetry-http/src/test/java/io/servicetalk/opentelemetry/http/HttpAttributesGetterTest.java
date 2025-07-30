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

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

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
    void networkTypeIpv4() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress ipv4Address = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(ipv4Address);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv4"));
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv4"));
    }

    @Test
    void networkTypeIpv6() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress ipv6Address = new InetSocketAddress(Inet6Address.getByName("::1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(ipv6Address);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv6"));
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), equalTo("ipv6"));
    }

    @Test
    void networkTypeNoConnectionInfo() {
        StreamingHttpRequest request = newRequest("/path");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getNetworkType(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkType(requestInfo, null), nullValue());
    }

    @Test
    void networkTransportTcp() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("tcp"));
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("tcp"));
    }

    @Test
    void networkTransportUnix() {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        DomainSocketAddress unixAddress = new DomainSocketAddress("/tmp/socket");
        when(connectionInfo.remoteAddress()).thenReturn(unixAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("unix"));
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), equalTo("unix"));
    }

    @Test
    void networkTransportNoConnectionInfo() {
        StreamingHttpRequest request = newRequest("/path");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkTransport(requestInfo, null), nullValue());
    }

    @Test
    void networkPeerAddressAndPortInet() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), equalTo("192.168.1.1"));
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), equalTo(8080));
    }

    @Test
    void networkPeerAddressAndPortUnix() {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        DomainSocketAddress unixAddress = new DomainSocketAddress("/tmp/socket");
        when(connectionInfo.remoteAddress()).thenReturn(unixAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), equalTo("/tmp/socket"));
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), nullValue());
    }

    @Test
    void networkPeerAddressAndPortNoConnectionInfo() {
        StreamingHttpRequest request = newRequest("/path");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(SERVER_INSTANCE.getNetworkPeerAddress(requestInfo, null), nullValue());
        assertThat(SERVER_INSTANCE.getNetworkPeerPort(requestInfo, null), nullValue());
    }

    @Test
    void serverAddressFromHostHeader() {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);

        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("example.com"));
    }

    @Test
    void serverPortFromHostHeader() {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);

        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));
    }

    @Test
    void serverAddressFallbackToRemoteAddress() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("192.168.1.1"));
    }

    @Test
    void serverPortFallbackToRemoteAddress() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));
    }

    @Test
    void serverAddressHostHeaderPreferredOverRemoteAddress() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:9090");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("example.com"));
    }

    @Test
    void serverHostHeaderPortPreferredOverResolvedAddress() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        request.addHeader(HOST, "example.com:9090");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        // The implementation prefers resolved port over host header port for accuracy
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(9090));
    }

    @Test
    void serverPortFallbackToResolvedAddress() throws UnknownHostException {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        InetSocketAddress inetAddress = new InetSocketAddress(Inet4Address.getByName("192.168.1.1"), 8080);
        when(connectionInfo.remoteAddress()).thenReturn(inetAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);

        // When there's no connection info, falls back to host header port
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), equalTo(8080));
    }

    @Test
    void serverAddressAndPortNoHostNoConnection() {
        StreamingHttpRequest request = newRequest("/path");
        RequestInfo requestInfo = new RequestInfo(request, null);

        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), nullValue());
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), nullValue());
    }

    @Test
    void serverAddressAndPortUnixSocket() {
        StreamingHttpRequest request = newRequest("/path");
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        DomainSocketAddress unixAddress = new DomainSocketAddress("/tmp/socket");
        when(connectionInfo.remoteAddress()).thenReturn(unixAddress);

        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        assertThat(CLIENT_INSTANCE.getServerAddress(requestInfo), equalTo("/tmp/socket"));
        assertThat(CLIENT_INSTANCE.getServerPort(requestInfo), nullValue());
    }

    private static StreamingHttpRequest newRequest(String requestTarget) {
        return REQ_RES_FACTORY.newRequest(HttpRequestMethod.GET, requestTarget);
    }
}
