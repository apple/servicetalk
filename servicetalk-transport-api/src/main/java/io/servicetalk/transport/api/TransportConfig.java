/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

/**
 * Configuration for transport settings.
 *
 * @see TransportConfigBuilder
 */
public interface TransportConfig {

    /**
     * Maximum number of times the transport will attempt to read data when the selector notifies that there is
     * read data pending.
     * <p>
     * The value must be positive. If this value is greater than {@code 1}, the transport might attempt to read multiple
     * times to procure multiple messages.
     *
     * @return Maximum number of times the transport will attempt to read data when the selector notifies that there is
     * read data pending
     */
    int maxReadAttemptsPerSelect();

    /**
     * Maximum number of bytes per read operation.
     * <p>
     * The transport may gradually increase the expected number of readable bytes up to this value if the previous read
     * fully filled the allocated buffer. It may also gradually decrease the expected number of readable bytes if the
     * read operation was not able to fill a predicted amount of the allocated bytes some number of times consecutively.
     * <p>
     * The value must be positive.
     *
     * @return Maximum number of bytes per read operation
     */
    int maxBytesPerRead();
}
