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

import java.util.function.Function;

final class NettyToServiceTalkHttpRequest<I> extends NettyToServiceTalkHttpRequestMetaData implements HttpRequest<I> {

    private final Publisher<I> messageBody;

    private NettyToServiceTalkHttpRequest(final io.netty.handler.codec.http.HttpRequest nettyHttpRequest,
                                          final Publisher<I> messageBody) {
        super(nettyHttpRequest);
        this.messageBody = messageBody;
    }

    private NettyToServiceTalkHttpRequest(final NettyToServiceTalkHttpRequestMetaData metaData,
                                          final Publisher<I> messageBody) {
        super(metaData);
        this.messageBody = messageBody;
    }

    static <I> HttpRequest<I> fromNettyHttpRequest(final io.netty.handler.codec.http.HttpRequest nettyHttpRequest,
                                                   final Publisher<I> messageBody) {
        if (nettyHttpRequest instanceof ServiceTalkToNettyHttpRequest) {
            return ((ServiceTalkToNettyHttpRequest) nettyHttpRequest).getHttpRequest();
        }
        return new NettyToServiceTalkHttpRequest<>(nettyHttpRequest, messageBody);
    }

    @Override
    public HttpRequest<I> setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public HttpRequest<I> setRawPath(final String path) {
        super.setRawPath(path);
        return this;
    }

    @Override
    public HttpRequest<I> setRawQuery(final String query) {
        super.setRawQuery(query);
        return this;
    }

    @Override
    public HttpRequest<I> setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public HttpRequest<I> setMethod(final HttpRequestMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public HttpRequest<I> setRequestTarget(final String requestTarget) {
        super.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public Publisher<I> getMessageBody() {
        return messageBody;
    }

    @Override
    public <R> HttpRequest<R> transformMessageBody(final Function<Publisher<I>, Publisher<R>> transformer) {
        return new NettyToServiceTalkHttpRequest<>(this, transformer.apply(messageBody));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        NettyToServiceTalkHttpRequest<?> that = (NettyToServiceTalkHttpRequest<?>) o;

        return messageBody.equals(that.messageBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + messageBody.hashCode();
    }
}
