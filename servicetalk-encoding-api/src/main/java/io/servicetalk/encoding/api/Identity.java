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

/**
 * Utility class that constructs and provides the default, always supported NOOP 'identity' {@link ContentCodec}.
 */
public final class Identity {
    @Deprecated
    private static final ContentCodec IDENTITY = new IdentityContentCodec();

    private Identity() {
        // no instances
    }

    /**
     * Returns the default, always supported NOOP 'identity' {@link ContentCodec}.
     * @deprecated Use {@link #identityEncoder()}.
     * @return the default, always supported NOOP 'identity' {@link ContentCodec}.
     */
    @Deprecated
    public static ContentCodec identity() {
        return IDENTITY;
    }

    /**
     * Get a {@link BufferEncoderDecoder} which provides
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.4">"no encoding"</a>.
     * @return a {@link BufferEncoderDecoder} which provides
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.4">"no encoding"</a>.
     */
    public static BufferEncoderDecoder identityEncoder() {
        return IdentityBufferEncoderDecoder.INSTANCE;
    }
}
