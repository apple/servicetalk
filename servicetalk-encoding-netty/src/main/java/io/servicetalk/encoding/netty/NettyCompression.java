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

/**
 * Common available compression implementations.
 */
public final class NettyCompression {
    private static final SerializerDeserializer<Buffer> DEFAULT_GZIP = gzip().build();
    private static final SerializerDeserializer<Buffer> DEFAULT_DEFLATE = deflate().build();
    private static final StreamingSerializerDeserializer<Buffer> DEFAULT_STREAM_GZIP = gzip().buildStreaming();
    private static final StreamingSerializerDeserializer<Buffer> DEFAULT_STREAM_DEFLATE = deflate().buildStreaming();

    private NettyCompression() {
    }

    /**
     * Returns the default GZIP based {@link StreamingSerializerDeserializer}.
     * @return default GZIP based {@link StreamingSerializerDeserializer}
     */
    public static SerializerDeserializer<Buffer> gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Returns the default GZIP based {@link StreamingSerializerDeserializer}.
     * @return default GZIP based {@link StreamingSerializerDeserializer}
     */
    public static StreamingSerializerDeserializer<Buffer> gzipDefaultStreaming() {
        return DEFAULT_STREAM_GZIP;
    }

    /**
     * Returns a GZIP based {@link ZipCompressionBuilder}.
     * @return a GZIP based {@link ZipCompressionBuilder}.
     */
    public static ZipCompressionBuilder gzip() {
        return new GzipCompressionBuilder();
    }

    /**
     * Returns the default DEFLATE based {@link SerializerDeserializer}.
     * @return default DEFLATE based {@link SerializerDeserializer}
     */
    public static SerializerDeserializer<Buffer> deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Returns the default DEFLATE based {@link StreamingSerializerDeserializer}.
     * @return default DEFLATE based {@link StreamingSerializerDeserializer}
     */
    public static StreamingSerializerDeserializer<Buffer> deflateDefaultStreaming() {
        return DEFAULT_STREAM_DEFLATE;
    }

    /**
     * Returns a DEFLATE based {@link ZipCompressionBuilder}.
     * @return a DEFLATE based {@link ZipCompressionBuilder}.
     */
    public static ZipCompressionBuilder deflate() {
        return new DeflateCompressionBuilder();
    }
}
