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

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;

import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.fromNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static java.util.Objects.requireNonNull;

abstract class ServiceTalkToNettyHttpMessage extends ServiceTalkToNettyHttpHeaders implements HttpMessage {

    private DecoderResult result = DecoderResult.SUCCESS;

    ServiceTalkToNettyHttpMessage(final io.servicetalk.http.api.HttpHeaders serviceTalkHeaders) {
        super(serviceTalkHeaders);
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return toNettyHttpVersion(getHttpMetaData().getVersion());
    }

    @Override
    public HttpMessage setProtocolVersion(final HttpVersion version) {
        getHttpMetaData().setVersion(fromNettyHttpVersion(requireNonNull(version)));
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return this;
    }

    @Override
    public DecoderResult getDecoderResult() {
        return decoderResult();
    }

    @Override
    public DecoderResult decoderResult() {
        return result;
    }

    @Override
    public void setDecoderResult(final DecoderResult result) {
        this.result = requireNonNull(result);
    }

    abstract HttpMetaData getHttpMetaData();

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

        final ServiceTalkToNettyHttpMessage entries = (ServiceTalkToNettyHttpMessage) o;

        return getHttpMetaData().equals(entries.getHttpMetaData());
    }

    @Override
    public int hashCode() {
        int result1 = super.hashCode();
        result1 = 31 * result1 + getHttpMetaData().hashCode();
        return result1;
    }
}
