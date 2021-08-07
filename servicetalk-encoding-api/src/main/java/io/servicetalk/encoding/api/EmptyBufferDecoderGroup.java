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

import static java.util.Collections.emptyList;

/**
 * A {@link BufferDecoderGroup} which is empty.
 */
public final class EmptyBufferDecoderGroup implements BufferDecoderGroup {
    public static final BufferDecoderGroup INSTANCE = new EmptyBufferDecoderGroup();

    private EmptyBufferDecoderGroup() {
    }

    @Override
    public List<BufferDecoder> decoders() {
        return emptyList();
    }

    @Nullable
    @Override
    public CharSequence advertisedMessageEncoding() {
        return null;
    }
}
