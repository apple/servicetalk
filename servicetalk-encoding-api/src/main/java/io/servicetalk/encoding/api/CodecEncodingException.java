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

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when something goes wrong during encoding.
 * @deprecated Use {@link BufferEncodingException}.
 */
@Deprecated
public final class CodecEncodingException extends RuntimeException {

    private static final long serialVersionUID = -3565785637300291924L;

    private final ContentCodec codec;

    /**
     * New instance.
     *
     * @param codec the codec in use.
     * @param message the reason of this exception.
     */
    public CodecEncodingException(final ContentCodec codec, final String message) {
        super(message);
        this.codec = requireNonNull(codec);
    }

    /**
     * New instance.
     *
     * @param codec the codec in use.
     * @param message the reason of this exception.
     * @param cause the cause of the exception.
     */
    public CodecEncodingException(final ContentCodec codec, final String message, final Throwable cause) {
        super(message, cause);
        this.codec = requireNonNull(codec);
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
