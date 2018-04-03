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

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.fromNettyHttpResponseStatus;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.toNettyHttpResponseStatus;

class ServiceTalkToNettyHttpResponse extends ServiceTalkToNettyHttpMessage implements HttpResponse {

    private final io.servicetalk.http.api.HttpResponse httpResponse;

    ServiceTalkToNettyHttpResponse(final io.servicetalk.http.api.HttpResponse httpResponse) {
        super(httpResponse.getHeaders());
        this.httpResponse = httpResponse;
    }

    @SuppressWarnings("unchecked")
    static <I> HttpResponse toNettyHttpResponse(final io.servicetalk.http.api.HttpResponse<I> metaData) {
        if (metaData instanceof NettyToServiceTalkHttpResponse) {
            return ((NettyToServiceTalkHttpResponse<I>) metaData).getNettyHttpResponse();
        }
        return new ServiceTalkToNettyHttpResponse(metaData);
    }

    @Override
    public HttpResponse setProtocolVersion(final HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return status();
    }

    @Override
    public HttpResponseStatus status() {
        return toNettyHttpResponseStatus(httpResponse.getStatus());
    }

    @Override
    public HttpResponse setStatus(final HttpResponseStatus status) {
        httpResponse.setStatus(fromNettyHttpResponseStatus(status));
        return this;
    }

    @Override
    final HttpMetaData getHttpMetaData() {
        return httpResponse;
    }

    final <O> io.servicetalk.http.api.HttpResponse<O> getHttpResponse() {
        return httpResponse;
    }
}
