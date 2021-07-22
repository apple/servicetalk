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

import io.servicetalk.concurrent.GracefulCloseable;

/**
 * A blocking client to a <a href="https://www.grpc.io">gRPC</a> service.
 *
 * @param <Client> The corresponding {@link GrpcClient}
 */
public interface BlockingGrpcClient<Client extends GrpcClient> extends GracefulCloseable {

    /**
     * Converts this {@link BlockingGrpcClient} to a client.
     *
     * @return This {@link BlockingGrpcClient} as a {@link Client}.
     */
    Client asClient();

    /**
     * Get the {@link GrpcExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link GrpcExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link GrpcExecutionContext} used during construction of this object.
     */
    GrpcExecutionContext executionContext();
}
