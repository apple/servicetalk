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

import java.net.Inet6Address;
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

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String IPV4 = "ipv4";
    private static final String IPV6 = "ipv6";
    private static final String TCP = "tcp";
    private static final String UNIX = "unix";
    private static final Integer PORT_80 = 80;
    private static final Integer PORT_443 = 443;

    static final HttpClientAttributesGetter<RequestInfo, HttpResponseMetaData>
            CLIENT_INSTANCE = new ClientGetter();

    static final HttpServerAttributesGetter<RequestInfo, HttpResponseMetaData>
            SERVER_INSTANCE = new ServerGetter();

    private HttpAttributesGetter() {}

    @Override
    public String getHttpRequestMethod(final RequestInfo requestInfo) {
        return requestInfo.request().method().name();
    }

    @Override
    public List<String> getHttpRequestHeader(
            final RequestInfo requestInfo, final String name) {
        return getHeaderValues(requestInfo.request().headers(), name);
    }

    @Override
    public Integer getHttpResponseStatusCode(
            final RequestInfo requestInfo,
            final HttpResponseMetaData httpResponseMetaData,
            @Nullable final Throwable error) {
        return httpResponseMetaData.status().code();
    }

    @Override
    public List<String> getHttpResponseHeader(
            final RequestInfo requestInfo,
            final HttpResponseMetaData httpResponseMetaData,
            final String name) {
        return getHeaderValues(httpResponseMetaData.headers(), name);
    }

    @Override
    public final String getNetworkProtocolName(
            final RequestInfo request, @Nullable final HttpResponseMetaData response) {
        return HTTP_SCHEME;
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
    public String getNetworkTransport(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        if (connectionInfo.remoteAddress() instanceof InetSocketAddress) {
            return TCP;
        } else if (connectionInfo.remoteAddress() instanceof DomainSocketAddress) {
            return UNIX;
        } else {
            // we don't know.
            return null;
        }
    }

    @Nullable
    @Override
    public String getNetworkType(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        if (connectionInfo.remoteAddress() instanceof InetSocketAddress) {
            return ((InetSocketAddress) connectionInfo.remoteAddress()).getAddress() instanceof Inet6Address ?
                    IPV6 : IPV4;
        }
        return null;
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
            HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
            if (effectiveHostAndPort == null) {
                // we cant create the authority so we must just return.
                return null;
            }
            String scheme = request.scheme();
            if (scheme == null) {
                // Note that this is best effort guessing: we cannot know if the connection is actually secure.
                scheme = effectiveHostAndPort.port() == 443 ? HTTPS_SCHEME : HTTP_SCHEME;
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
            HostAndPort effectiveHostAndPort = requestInfo.request().effectiveHostAndPort();
            if (effectiveHostAndPort != null) {
                return effectiveHostAndPort.hostName();
            }
            ConnectionInfo connectionInfo = requestInfo.connectionInfo();
            if (connectionInfo == null) {
                return null;
            }
            SocketAddress address = connectionInfo.remoteAddress();
            if (address instanceof InetSocketAddress) {
                return ((InetSocketAddress) address).getHostString();
            } else if (address instanceof DomainSocketAddress) {
                return ((DomainSocketAddress) address).getPath();
            } else {
                // Try to turn it into something meaningful.
                return address.toString();
            }
        }

        @Nullable
        @Override
        public Integer getServerPort(RequestInfo requestInfo) {
            final HostAndPort effectiveHostAndPort = requestInfo.request().effectiveHostAndPort();
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
                if (HTTP_SCHEME.equals(scheme)) {
                    return PORT_80;
                }
                if (HTTPS_SCHEME.equals(scheme)) {
                    return PORT_443;
                }
            }
            return null;
        }

        private static boolean isDefaultPort(String scheme, int port) {
            return port < 1 || HTTPS_SCHEME.equals(scheme) && port == PORT_443 ||
                    HTTP_SCHEME.equals(scheme) && port == PORT_80;
        }
    }

    private static final class ServerGetter extends HttpAttributesGetter
            implements HttpServerAttributesGetter<RequestInfo, HttpResponseMetaData> {

        @Nullable
        @Override
        public String getClientAddress(RequestInfo requestInfo) {
            return getResolvedAddress(requestInfo);
        }

        @Nullable
        @Override
        public Integer getClientPort(RequestInfo requestInfo) {
            return getResolvedPort(requestInfo);
        }

        @Override
        public String getUrlScheme(final RequestInfo requestInfo) {
            final String scheme = requestInfo.request().scheme();
            return scheme == null ? HTTP_SCHEME : scheme;
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

        @Nullable
        @Override
        public String getNetworkPeerAddress(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
            return getResolvedAddress(requestInfo);
        }

        @Nullable
        @Override
        public Integer getNetworkPeerPort(RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData) {
            return getResolvedPort(requestInfo);
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
    private static String getResolvedAddress(RequestInfo requestInfo) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return null;
        }
        SocketAddress address = connectionInfo.remoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress().getHostAddress();
        } else if (address instanceof DomainSocketAddress) {
            return ((DomainSocketAddress) address).getPath();
        } else {
            // Try to turn it into something meaningful.
            return address.toString();
        }
    }
}
