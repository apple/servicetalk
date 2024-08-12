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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * Builder for {@link TransportConfig}.
 */
public final class TransportConfigBuilder {

    private static final int DEFAULT_MAX_READ_ATTEMPTS_PER_SELECT = 4;
    private static final int DEFAULT_MAX_BYTES_PER_READ = 65_536;

    private int maxReadAttemptsPerSelect = DEFAULT_MAX_READ_ATTEMPTS_PER_SELECT;
    private int maxBytesPerRead = DEFAULT_MAX_BYTES_PER_READ;

    /**
     * Sets maximum number of times the transport will attempt to read data when the selector notifies that there is
     * read data pending.
     * <p>
     * The value must be positive. If this value is greater than {@code 1}, the transport might attempt to read multiple
     * times to procure multiple messages.
     *
     * @param maxReadAttemptsPerSelect Maximum number of times the transport will attempt to read data when the selector
     * notifies that there is read data pending
     * @return {@code this}
     * @see TransportConfig#maxReadAttemptsPerSelect()
     */
    public TransportConfigBuilder maxReadAttemptsPerSelect(final int maxReadAttemptsPerSelect) {
        this.maxReadAttemptsPerSelect = ensurePositive(maxReadAttemptsPerSelect, "maxReadAttemptsPerSelect");
        return this;
    }

    /**
     * Sets maximum number of bytes per read operation.
     * <p>
     * The transport may gradually increase the expected number of readable bytes up to this value if the previous read
     * fully filled the allocated buffer. It may also gradually decrease the expected number of readable bytes if the
     * read operation was not able to fill a predicted amount of the allocated bytes some number of times consecutively.
     * <p>
     * The value must be positive.
     *
     * @param maxBytesPerRead Maximum number of bytes per read operation
     * @return {@code this}
     * @see TransportConfig#maxBytesPerRead()
     */
    public TransportConfigBuilder maxBytesPerRead(final int maxBytesPerRead) {
        this.maxBytesPerRead = ensurePositive(maxBytesPerRead, "maxBytesPerRead");
        return this;
    }

    /**
     * Builds a new {@link TransportConfig}.
     *
     * @return a new {@link TransportConfig}
     */
    public TransportConfig build() {
        return new DefaultTransportConfig(maxReadAttemptsPerSelect, maxBytesPerRead);
    }

    private static final class DefaultTransportConfig implements TransportConfig {

        private final int maxReadAttemptsPerSelect;
        private final int maxBytesPerRead;

        private DefaultTransportConfig(final int maxReadAttemptsPerSelect, final int maxBytesPerRead) {
            this.maxReadAttemptsPerSelect = maxReadAttemptsPerSelect;
            this.maxBytesPerRead = maxBytesPerRead;
        }

        @Override
        public int maxReadAttemptsPerSelect() {
            return maxReadAttemptsPerSelect;
        }

        @Override
        public int maxBytesPerRead() {
            return maxBytesPerRead;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DefaultTransportConfig that = (DefaultTransportConfig) o;
            return maxReadAttemptsPerSelect == that.maxReadAttemptsPerSelect && maxBytesPerRead == that.maxBytesPerRead;
        }

        @Override
        public int hashCode() {
            int result = maxReadAttemptsPerSelect;
            result = 31 * result + maxBytesPerRead;
            return result;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{maxReadAttemptsPerSelect=" + maxReadAttemptsPerSelect +
                    ", maxBytesPerRead=" + maxBytesPerRead +
                    '}';
        }
    }
}
