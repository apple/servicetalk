/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

class DefaultGrpcMetadata implements GrpcMetadata {

    private final String path;

    /**
     * Timeout for an individual request. All values greater than {@link GrpcMetadata#GRPC_MAX_TIMEOUT} should be
     * regarded as infinite or no timeout.
     */
    private final Duration timeout;

    DefaultGrpcMetadata(final String path, final Duration timeout) {
        this.path = requireNonNull(path, "path");
        if (Duration.ZERO.compareTo(requireNonNull(timeout, "timeout")) >= 0) {
            throw new IllegalArgumentException("timeout: " + timeout + " (expected > 0)");
        }
        this.timeout = timeout;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }
}
