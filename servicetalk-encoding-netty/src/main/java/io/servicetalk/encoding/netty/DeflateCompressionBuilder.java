/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

final class DeflateCompressionBuilder extends ZipCompressionBuilder {
    @Override
    public SerializerDeserializer<Buffer> build() {
        return new NettyCompressionSerializer(
                () -> new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel()),
                () -> new JdkZlibDecoder(ZlibWrapper.ZLIB, maxChunkSize()));
    }

    @Override
    public StreamingSerializerDeserializer<Buffer> buildStreaming() {
        return new NettyCompressionStreamingSerializer(
                () -> new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel()),
                () -> new JdkZlibDecoder(ZlibWrapper.ZLIB, maxChunkSize())
        );
    }
}
