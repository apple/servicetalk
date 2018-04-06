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

import java.util.function.Function;

final class NettyToServiceTalkHttpResponse<I> extends NettyToServiceTalkHttpResponseMetaData implements HttpResponse<I> {

    private final Publisher<I> messageBody;

    private NettyToServiceTalkHttpResponse(final io.netty.handler.codec.http.HttpResponse nettyHttpResponse,
                                           final Publisher<I> messageBody) {
        super(nettyHttpResponse);
        this.messageBody = messageBody;
    }

    @SuppressWarnings("unchecked")
    static <I> HttpResponse<I> fromNettyHttpResponse(final io.netty.handler.codec.http.HttpResponse nettyHttpResponse,
                                                     final Publisher<I> messageBody) {
        if (nettyHttpResponse instanceof ServiceTalkToNettyHttpResponse) {
            return ((ServiceTalkToNettyHttpResponse) nettyHttpResponse).getHttpResponse();
        }
        return new NettyToServiceTalkHttpResponse<>(nettyHttpResponse, messageBody);
    }

    @Override
    public HttpResponse<I> setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public HttpResponse<I> setStatus(final HttpResponseStatus status) {
         super.setStatus(status);
         return this;
    }

    @Override
    public Publisher<I> getMessageBody() {
        return messageBody;
    }

    @Override
    public <R> HttpResponse<R> transformMessageBody(final Function<Publisher<I>, Publisher<R>> transformer) {
        return new NettyToServiceTalkHttpResponse<>(getNettyHttpResponse(), transformer.apply(messageBody));
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

        NettyToServiceTalkHttpResponse<?> that = (NettyToServiceTalkHttpResponse<?>) o;

        return messageBody.equals(that.messageBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + messageBody.hashCode();
    }
}
