/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

abstract class HttpAttributesGetter
        implements NetworkAttributesGetter<RequestInfo, HttpResponseMetaData>,
        HttpCommonAttributesGetter<RequestInfo, HttpResponseMetaData> {

    private static final CharSequence AUTHORITY = CharSequences.newAsciiString(":authority");
    private static final Integer PORT_80 = 80;
    private static final Integer PORT_443 = 443;

    static final HttpClientAttributesGetter<RequestInfo, HttpResponseMetaData>
            CLIENT_INSTANCE = new ClientGetter();

    static final HttpServerAttributesGetter<RequestInfo, HttpResponseMetaData>
            SERVER_INSTANCE = new ServerGetter();

    private HttpAttributesGetter() {}

    @Override
    public final String getHttpRequestMethod(final RequestInfo requestInfo) {
        return requestInfo.request().method().name();
    }

    @Override
    public final List<String> getHttpRequestHeader(
            final RequestInfo requestInfo, final String name) {
        return getHeaderValues(requestInfo.request().headers(), name);
    }

    @Override
    public final Integer getHttpResponseStatusCode(
            final RequestInfo requestInfo,
            final HttpResponseMetaData httpResponseMetaData,
            @Nullable final Throwable error) {
        return httpResponseMetaData.status().code();
    }

    @Override
    public final List<String> getHttpResponseHeader(
            final RequestInfo requestInfo,
            final HttpResponseMetaData httpResponseMetaData,
            final String name) {
        return getHeaderValues(httpResponseMetaData.headers(), name);
    }

    @Override
    public final String getNetworkProtocolName(
            final RequestInfo request, @Nullable final HttpResponseMetaData response) {
        return Constants.HTTP_SCHEME;
    }

    @Override
    public final String getNetworkProtocolVersion(
            final RequestInfo request, @Nullable final HttpResponseMetaData response) {
        HttpRequestMetaData metadata = request.request();
        if (response == null) {
            return metadata.version().fullVersion();
        }
        return response.version().fullVersion();
    }

    @Nullable
    @Override
    public final String getNetworkTransport(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        if (connectionInfo.remoteAddress() instanceof InetSocketAddress) {
            return Constants.TCP;
        } else if (connectionInfo.remoteAddress() instanceof DomainSocketAddress) {
            return Constants.UNIX;
        } else {
            // we don't know.
            return null;
        }
    }

    @Nullable
    @Override
    public final String getNetworkType(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        if (connectionInfo.remoteAddress() instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress) connectionInfo.remoteAddress()).getAddress();
            if (address instanceof Inet6Address) {
                return Constants.IPV6;
            } else if (address instanceof Inet4Address) {
                return Constants.IPV4;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public final InetSocketAddress getNetworkPeerInetSocketAddress(RequestInfo requestInfo,
                                                                   @Nullable HttpResponseMetaData responseMetaData) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo != null && connectionInfo.remoteAddress() instanceof InetSocketAddress) {
            return (InetSocketAddress) connectionInfo.remoteAddress();
        }
        return null;
    }

    @Nullable
    @Override
    public final String getNetworkPeerAddress(RequestInfo requestInfo,
                                              @Nullable HttpResponseMetaData responseMetaData) {
        return getResolvedPeerAddress(requestInfo);
    }

    @Nullable
    @Override
    public final Integer getNetworkPeerPort(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
        return getResolvedPort(requestInfo);
    }

    private static List<String> getHeaderValues(final HttpHeaders headers, final String name) {
        final Iterator<? extends CharSequence> iterator = headers.valuesIterator(name);
        if (!iterator.hasNext()) {
            return emptyList();
        }
        final CharSequence firstValue = iterator.next();
        if (!iterator.hasNext()) {
            return singletonList(firstValue.toString());
        }
        final List<String> result = new ArrayList<>(2);
        result.add(firstValue.toString());
        result.add(iterator.next().toString());
        while (iterator.hasNext()) {
            result.add(iterator.next().toString());
        }
        return unmodifiableList(result);
    }

    private static final class ClientGetter extends HttpAttributesGetter
            implements HttpClientAttributesGetter<RequestInfo, HttpResponseMetaData> {

        @Override
        @Nullable
        public String getUrlFull(final RequestInfo requestInfo) {
            HttpRequestMetaData request = requestInfo.request();
            String requestTarget = request.requestTarget();
            if (requestTarget.startsWith("https://") || requestTarget.startsWith("http://")) {
                // request target is already absolute-form: just return it.
                return requestTarget;
            }

            // in this case the request target is most likely origin-form so we need to convert it to absolute-form.
            HostAndPort effectiveHostAndPort = effectiveHostAndPort(requestInfo);
            if (effectiveHostAndPort == null) {
                // we cant create the authority so we must just return.
                return null;
            }
            String scheme = request.scheme();
            if (scheme == null) {
                // Note that this is best effort guessing: we cannot know if the connection is actually secure.
                scheme = effectiveHostAndPort.port() == 443 ? Constants.HTTPS_SCHEME : Constants.HTTP_SCHEME;
            }
            String authority = effectiveHostAndPort.hostName();
            if (!isDefaultPort(scheme, effectiveHostAndPort.port())) {
                authority = authority + ':' + effectiveHostAndPort.port();
            }
            String authoritySeparator = requestTarget.startsWith("/") ? "" : "/";
            return scheme + "://" + authority + authoritySeparator + requestTarget;
        }

        @Override
        @Nullable
        public String getServerAddress(final RequestInfo requestInfo) {
            // For the server address we prefer the unresolved address, if possible. If we don't have that we'll
            // fall back to the resolved address.
            HostAndPort effectiveHostAndPort = effectiveHostAndPort(requestInfo);
            if (effectiveHostAndPort != null) {
                return effectiveHostAndPort.hostName();
            }
            return getRemoteHostOrAddress(requestInfo);
        }

        @Nullable
        @Override
        public Integer getServerPort(RequestInfo requestInfo) {
            final HostAndPort effectiveHostAndPort = effectiveHostAndPort(requestInfo);
            if (effectiveHostAndPort != null) {
                return effectiveHostAndPort.port();
            }
            Integer serverPort = getResolvedPort(requestInfo);
            if (serverPort != null) {
                return serverPort;
            }
            // No port from the request or from the peer address. We'll try to infer it from the scheme.
            String scheme = requestInfo.request().scheme();
            if (scheme != null) {
                if (Constants.HTTP_SCHEME.equals(scheme)) {
                    return PORT_80;
                }
                if (Constants.HTTPS_SCHEME.equals(scheme)) {
                    return PORT_443;
                }
            }
            return null;
        }

        private static boolean isDefaultPort(String scheme, int port) {
            return port < 1 || Constants.HTTPS_SCHEME.equals(scheme) && port == PORT_443 ||
                    Constants.HTTP_SCHEME.equals(scheme) && port == PORT_80;
        }
    }

    private static final class ServerGetter extends HttpAttributesGetter
            implements HttpServerAttributesGetter<RequestInfo, HttpResponseMetaData> {

        @Nullable
        @Override
        public String getClientAddress(RequestInfo requestInfo) {
            return getRemoteHostOrAddress(requestInfo);
        }

        @Nullable
        @Override
        public Integer getClientPort(RequestInfo requestInfo) {
            return getResolvedPort(requestInfo);
        }

        @Override
        public String getUrlScheme(final RequestInfo requestInfo) {
            final String scheme = requestInfo.request().scheme();
            return scheme == null ? Constants.HTTP_SCHEME : scheme;
        }

        @Override
        public String getUrlPath(final RequestInfo requestInfo) {
            return requestInfo.request().path();
        }

        @Nullable
        @Override
        public String getUrlQuery(final RequestInfo requestInfo) {
            return requestInfo.request().query();
        }
    }

    @Nullable
    private static Integer getResolvedPort(RequestInfo requestInfo) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        SocketAddress address = connectionInfo.remoteAddress();
        return address instanceof InetSocketAddress ? ((InetSocketAddress) address).getPort() : null;
    }

    @Nullable
    private static String getResolvedPeerAddress(RequestInfo requestInfo) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        SocketAddress address = connectionInfo.remoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress().getHostAddress();
        } else if (address instanceof DomainSocketAddress) {
            return ((DomainSocketAddress) address).path();
        } else {
            // Try to turn it into something meaningful.
            return address.toString();
        }
    }

    // This variant prefers to use a hostname, if possible, but will fall back to an IP address, if available.
    @Nullable
    private static String getRemoteHostOrAddress(final RequestInfo requestInfo) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        SocketAddress address = connectionInfo.remoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getHostString();
        } else if (address instanceof DomainSocketAddress) {
            return ((DomainSocketAddress) address).path();
        } else {
            // Try to turn it into something meaningful.
            return address.toString();
        }
    }

    @Nullable
    static HostAndPort effectiveHostAndPort(final RequestInfo requestInfo) {
        HostAndPort hostAndPort = requestInfo.request().effectiveHostAndPort();
        if (hostAndPort == null) {
            // On the client side we lazily populate the attributes because adding the host header happens last.
            // For HTTP/2, this host header will get turned into an ':authority' header by the ServiceTalk internals
            // and that is what we typically observer after the request.
            hostAndPort = parseAuthorityHeader(requestInfo.request().headers().get(AUTHORITY));
        }
        return hostAndPort;
    }

    @Nullable
    // exposed for testing
    static HostAndPort parseAuthorityHeader(@Nullable CharSequence authority) {
        if (authority == null || authority.length() == 0) {
            return null;
        }
        final int colonPosition;
        // need to determine if this authority is ipv6 before we can look for ':' chars to see if we have a port.
        if (authority.length() > 0 && authority.charAt(0) == '[') {
            // ipv6. We need to look for a ':' after the ending ']' char.
            int endIpv6Address = CharSequences.indexOf(authority, ']', 0);
            if (endIpv6Address == -1) {
                // invalid address: just return null.
                return null;
            }
            colonPosition = CharSequences.indexOf(authority, ':', endIpv6Address + 1);
        } else {
            colonPosition = CharSequences.indexOf(authority, ':', 0);
        }
        final CharSequence host;
        final int port;
        if (colonPosition < 0) {
            // no port.
            host = authority;
            port = -1;
        } else if (colonPosition == 0 || colonPosition == authority.length() - 1) {
            // something like "foo:" or ":123", which we assume is invalid.
            return null;
        } else {
            host = authority.subSequence(0, colonPosition);
            try {
                port = Math.max(-1,
                        (int) CharSequences.parseLong(authority.subSequence(colonPosition + 1, authority.length())));
            } catch (IllegalArgumentException ex) {
                // malformed port, give up.
                return null;
            }
        }
        // If we don't have a host name we assume it's malformed.
        return host.length() == 0 ? null : HostAndPort.of(host.toString(), port);
    }
}
