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

import io.servicetalk.encoding.api.ContentCodec;

/**
 * Common available encoding implementations.
 * @deprecated Use {@link NettyCompression} and {@link NettyBufferEncoders}.
 */
@Deprecated
public final class ContentCodings {

    private static final ContentCodec DEFAULT_GZIP = gzip().build();

    private static final ContentCodec DEFAULT_DEFLATE = deflate().build();

    private ContentCodings() {
    }

    /**
     * Returns the default GZIP {@link ContentCodec}.
     * @return default GZIP based {@link ContentCodec}
     */
    public static ContentCodec gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Returns a GZIP based {@link ZipContentCodecBuilder} that allows building
     * a customizable {@link ContentCodec}.
     * @return a GZIP based {@link ZipContentCodecBuilder} that allows building
     * a customizable GZIP {@link ContentCodec}
     */
    public static ZipContentCodecBuilder gzip() {
        return new ZipContentCodecBuilder.GzipContentCodecBuilder();
    }

    /**
     * Returns the default DEFLATE based {@link ContentCodec}.
     * @return default DEFLATE based {@link ContentCodec}
     */
    public static ContentCodec deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Returns a DEFLATE based {@link ZipContentCodecBuilder} that allows building
     * a customizable {@link ContentCodec}.
     * @return a DEFLATE based {@link ZipContentCodecBuilder} that allows building
     *          a customizable DEFLATE {@link ContentCodec}
     */
    public static ZipContentCodecBuilder deflate() {
        return new ZipContentCodecBuilder.DeflateContentCodecBuilder();
    }
}
