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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static java.util.Collections.emptyList;

/**
 * Builder for {@link BufferDecoderGroup}s.
 */
public final class BufferDecoderGroupBuilder {
    private static final char CONTENT_ENCODING_SEPARATOR = ',';
    private final StringBuilder messageEncoding;
    private final List<BufferDecoder> decoders;

    /**
     * Create a new instance.
     */
    public BufferDecoderGroupBuilder() {
        this(2);
    }

    /**
     * Create a new instance.
     * @param decodersSizeEstimate estimate as to how many {@link BufferDecoder} will be included in the
     * {@link BufferDecoderGroup} built by this builder.
     */
    public BufferDecoderGroupBuilder(int decodersSizeEstimate) {
        messageEncoding = new StringBuilder(decodersSizeEstimate * 8);
        decoders = new ArrayList<>(decodersSizeEstimate);
    }

    /**
     * Add a new {@link BufferDecoder} to the {@link BufferDecoderGroup} built by this builder.
     * @param decoder The decoder to add.
     * @param advertised {@code true} if the decoder should be included in
     * {@link BufferDecoderGroup#advertisedMessageEncoding()}.
     * @return {@code this}.
     */
    public BufferDecoderGroupBuilder add(BufferDecoder decoder, boolean advertised) {
        decoders.add(decoder);
        if (advertised) {
            if (messageEncoding.length() > 0) {
                messageEncoding.append(CONTENT_ENCODING_SEPARATOR);
            }
            messageEncoding.append(decoder.encodingName());
        }
        return this;
    }

    /**
     * Build a new {@link BufferDecoderGroup}.
     * @return a new {@link BufferDecoderGroup}.
     */
    public BufferDecoderGroup build() {
        return new BufferDecoderGroup() {
            private final List<BufferDecoder> bufferEncoders = decoders.isEmpty() ? emptyList() :
                    new ArrayList<>(decoders);
            @Nullable
            private final CharSequence advertisedMessageEncoding = messageEncoding.length() == 0 ?
                    null : newAsciiString(messageEncoding);

            @Override
            public List<BufferDecoder> decoders() {
                return bufferEncoders;
            }

            @Nullable
            @Override
            public CharSequence advertisedMessageEncoding() {
                return advertisedMessageEncoding;
            }
        };
    }
}
