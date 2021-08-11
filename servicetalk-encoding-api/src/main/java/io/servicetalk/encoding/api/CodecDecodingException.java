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
package io.servicetalk.encoding.api;

import io.servicetalk.serializer.api.SerializationException;

/**
 * Exception thrown when something goes wrong during decoding.
 * @deprecated Use {@link BufferEncodingException}.
 */
@Deprecated
public final class CodecDecodingException extends SerializationException {

    private static final long serialVersionUID = 5569510372715687762L;

    private final ContentCodec codec;

    /**
     * New instance.
     *
     * @param codec the codec in use.
     * @param message the reason of this exception.
     */
    public CodecDecodingException(final ContentCodec codec, final String message) {
        super(message);
        this.codec = codec;
    }

    /**
     * New instance.
     *
     * @param codec the codec in use.
     * @param message the reason of this exception.
     * @param cause the cause of the exception.
     */
    public CodecDecodingException(final ContentCodec codec, final String message, final Throwable cause) {
        super(message, cause);
        this.codec = codec;
    }

    /**
     * Returns the codec in use when this exception occurred.
     *
     * @return the codec in use when this exception occurred.
     */
    public ContentCodec codec() {
        return codec;
    }
}
