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

import java.util.List;
import javax.annotation.Nullable;

/**
 * A group of {@link BufferDecoder}s used when multiple options may be supported.
 */
public interface BufferDecoderGroup {
    /**
     * Get the supported {@link BufferDecoder} for this group.
     * @return the supported {@link BufferDecoder} for this group.
     */
    List<BufferDecoder> decoders();

    /**
     * Get the combined encoding to advertise. This is typically a combination of
     * {@link BufferDecoder#encodingName()} contained in this group.
     * @return the combined encoding to advertise. This is typically a combination of
     * {@link BufferDecoder#encodingName()} contained in this group.
     */
    @Nullable
    CharSequence advertisedMessageEncoding();
}
