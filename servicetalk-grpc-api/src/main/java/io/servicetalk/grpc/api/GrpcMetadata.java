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

import static io.servicetalk.grpc.api.GrpcUtils.EIGHT_NINES;

/**
 * Metadata for a <a href="https://www.grpc.io">gRPC</a> call.
 */
public interface GrpcMetadata {

    /**
     * Maximum timeout which can be specified for a <a href="https://www.grpc.io">gRPC</a>
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">request</a>. Note that this
     * maximum is effectively infinite as the duration is more than 11,000 years.
     */
    Duration GRPC_MAX_TIMEOUT = Duration.ofHours(EIGHT_NINES);

    /**
     * Returns the path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     *
     * @return The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    String path();

    /**
     * Timeout after which the client no longer wants response.
     *
     * @return {@link Duration} of associated timeout. All durations greater than {@link #GRPC_MAX_TIMEOUT} will be
     * treated as infinite (no deadline).
     * @see <a href="https://grpc.io/blog/deadlines/">gRPC Deadlines</a>
     */
    Duration timeout();
}
