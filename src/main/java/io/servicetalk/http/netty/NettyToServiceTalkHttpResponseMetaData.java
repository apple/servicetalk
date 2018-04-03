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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;

import io.netty.handler.codec.http.HttpResponse;

import java.util.function.BiFunction;

import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.fromNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.fromNettyHttpResponseStatus;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.toNettyHttpResponseStatus;
import static java.lang.System.lineSeparator;

class NettyToServiceTalkHttpResponseMetaData extends NettyToServiceTalkHttpHeaders implements HttpResponseMetaData {

    private final HttpResponse nettyHttpResponse;

    NettyToServiceTalkHttpResponseMetaData(final HttpResponse nettyHttpResponse) {
        super(nettyHttpResponse.headers());
        this.nettyHttpResponse = nettyHttpResponse;
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return fromNettyHttpVersion(nettyHttpResponse.protocolVersion());
    }

    @Override
    public HttpResponseMetaData setVersion(final HttpProtocolVersion version) {
        nettyHttpResponse.setProtocolVersion(toNettyHttpVersion(version));
        return this;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return fromNettyHttpResponseStatus(nettyHttpResponse.status());
    }

    @Override
    public HttpResponseMetaData setStatus(final HttpResponseStatus status) {
        nettyHttpResponse.setStatus(toNettyHttpResponseStatus(status));
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return this;
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return getVersion() + " " + getStatus() + lineSeparator()
                + getHeaders().toString(headerFilter);
    }

    final HttpResponse getNettyHttpResponse() {
        return nettyHttpResponse;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final NettyToServiceTalkHttpResponseMetaData entries = (NettyToServiceTalkHttpResponseMetaData) o;

        return nettyHttpResponse.equals(entries.nettyHttpResponse);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + nettyHttpResponse.hashCode();
        return result;
    }
}
