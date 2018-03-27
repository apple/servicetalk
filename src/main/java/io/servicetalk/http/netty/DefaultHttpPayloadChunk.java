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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.http.api.HttpPayloadChunk;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;

class DefaultHttpPayloadChunk implements HttpPayloadChunk {

    protected final Buffer content;

    DefaultHttpPayloadChunk(BufferAllocator allocator, HttpContent nettyContent) {
        content = BufferUtil.newBufferFrom(nettyContent.content());
    }

    DefaultHttpPayloadChunk(Buffer content) {
        this.content = content;
    }

    @Override
    public Buffer getContent() {
        return content;
    }

    @Override
    public HttpPayloadChunk duplicate() {
        return new DefaultHttpPayloadChunk(content.duplicate());
    }

    @Override
    public HttpPayloadChunk replace(Buffer content) {
        return new DefaultHttpPayloadChunk(content);
    }

    static HttpContent toNettyContent(Channel owningChannel, HttpPayloadChunk from) {
        assert owningChannel.eventLoop().inEventLoop() : "Can not be called outside the eventloop.";

        ByteBuf content = BufferUtil.extractByteBufOrCreate(from.getContent());
        return new DefaultHttpContent(content);
    }
}
