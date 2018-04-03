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

import io.servicetalk.http.api.HttpMetaData;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.fromNettyHttpMethod;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.toNettyHttpMethod;

class ServiceTalkToNettyHttpRequest extends ServiceTalkToNettyHttpMessage implements HttpRequest {

    private final io.servicetalk.http.api.HttpRequest httpRequest;

    ServiceTalkToNettyHttpRequest(final io.servicetalk.http.api.HttpRequest httpRequest) {
        super(httpRequest.getHeaders());
        this.httpRequest = httpRequest;
    }

    @SuppressWarnings("unchecked")
    static <I> HttpRequest toNettyHttpRequest(final io.servicetalk.http.api.HttpRequest<I> metaData) {
        if (metaData instanceof NettyToServiceTalkHttpRequest) {
            return ((NettyToServiceTalkHttpRequest<I>) metaData).getNettyHttpRequest();
        }
        return new ServiceTalkToNettyHttpRequest(metaData);
    }

    @Override
    public HttpRequest setProtocolVersion(final HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public HttpMethod getMethod() {
        return method();
    }

    @Override
    public HttpMethod method() {
        return toNettyHttpMethod(httpRequest.getMethod());
    }

    @Override
    public HttpRequest setMethod(final HttpMethod method) {
        httpRequest.setMethod(fromNettyHttpMethod(method));
        return this;
    }

    @Override
    public String getUri() {
        return uri();
    }

    @Override
    public String uri() {
        return httpRequest.getRequestTarget();
    }

    @Override
    public HttpRequest setUri(final String uri) {
        httpRequest.setRequestTarget(uri);
        return this;
    }

    @Override
    final HttpMetaData getHttpMetaData() {
        return httpRequest;
    }

    @SuppressWarnings("unchecked")
    final <I> io.servicetalk.http.api.HttpRequest<I> getHttpRequest() {
        return httpRequest;
    }
}
