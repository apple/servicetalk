/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
 * Builder for {@link ContentCodec}.
 * @deprecated encoding-api based solution is being replaced with a netty implementation available under
 * servicetalk-encoding-netty dependency.
 */
@Deprecated
public interface ContentCodecBuilder {

    /**
     * Sets the maximum allowed decompressed payload size that the codec can process.
     * This can help prevent malicious attempts to decode malformed payloads that can drain resources of the
     * running instance.
     *
     * @param maxAllowedPayloadSize the maximum allowed payload size
     * @return {@code this}
     * @see <a href="https://en.wikipedia.org/wiki/Zip_bomb">Zip Bomb</a>
     */
    ContentCodecBuilder setMaxAllowedPayloadSize(int maxAllowedPayloadSize);

    /**
     * Build and return an instance of the {@link ContentCodec} with the configuration of the builder.
     * @return the {@link ContentCodec} with the configuration of the builder
     */
    ContentCodec build();
}
