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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;

import io.netty.handler.codec.http.DefaultHttpRequest;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.toNettyHttpMethod;
import static io.servicetalk.http.netty.NettyToServiceTalkHttpRequest.fromNettyHttpRequest;

/**
 * Temporary class.
 */
public final class NettyHttpRequestFactory {

    public static final NettyHttpRequestFactory INSTANCE = new NettyHttpRequestFactory();

    private NettyHttpRequestFactory() {
        // singleton.
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpRequestMethod method, final String requestTarget) {
        return createRequest(HTTP_1_1, method, requestTarget);
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget) {
        return createRequest(version, method, requestTarget, empty());
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpRequestMethod method, final String requestTarget, final I messageBody) {
        return createRequest(HTTP_1_1, method, requestTarget, messageBody);
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget, final I messageBody) {
        return createRequest(version, method, requestTarget, just(messageBody));
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpRequestMethod method, final String requestTarget, final Publisher<I> messageBody) {
        return createRequest(HTTP_1_1, method, requestTarget, messageBody);
    }

    /**
     * Temporary class.
     */
    public <I> HttpRequest<I> createRequest(final HttpProtocolVersion version, final HttpRequestMethod method, final String requestTarget, final Publisher<I> messageBody) {
        final DefaultHttpRequest nettyRequest = new DefaultHttpRequest(toNettyHttpVersion(version), toNettyHttpMethod(method), requestTarget);
        return fromNettyHttpRequest(nettyRequest, messageBody);
    }
}
