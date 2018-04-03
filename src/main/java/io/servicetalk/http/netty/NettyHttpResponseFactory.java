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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;

import io.netty.handler.codec.http.DefaultHttpResponse;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.toNettyHttpResponseStatus;
import static io.servicetalk.http.netty.NettyToServiceTalkHttpResponse.fromNettyHttpResponse;

/**
 * Temporary class.
 */
public final class NettyHttpResponseFactory {

    public static final NettyHttpResponseFactory INSTANCE = new NettyHttpResponseFactory();

    private NettyHttpResponseFactory() {
        // singleton.
    }

    /**
     * Temporary class.
     */
    public <I> HttpResponse<I> createResponse(final HttpResponseStatus status) {
        return createResponse(HTTP_1_1, status);
    }

    /**
     * Temporary class.
     */
    public <I> HttpResponse<I> createResponse(final HttpProtocolVersion version, final HttpResponseStatus status) {
        return createResponse(version, status, empty());
    }

    /**
     * Temporary class.
     */
    public <O> HttpResponse<O> createResponse(final HttpResponseStatus status, final O messageBody) {
        return createResponse(HTTP_1_1, status, messageBody);
    }

    /**
     * Temporary class.
     */
    public <O> HttpResponse<O> createResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final O messageBody) {
        return createResponse(version, status, just(messageBody));
    }

    /**
     * Temporary class.
     */
    public <O> HttpResponse<O> createResponse(final HttpResponseStatus status, final Publisher<O> messageBody) {
        return createResponse(HTTP_1_1, status, messageBody);
    }

    /**
     * Temporary class.
     */
    public <I> HttpResponse<I> createResponse(final HttpProtocolVersion version, final HttpResponseStatus status, final Publisher<I> messageBody) {
        final DefaultHttpResponse nettyResponse = new DefaultHttpResponse(toNettyHttpVersion(version), toNettyHttpResponseStatus(status));
        return fromNettyHttpResponse(nettyResponse, messageBody);
    }
}
