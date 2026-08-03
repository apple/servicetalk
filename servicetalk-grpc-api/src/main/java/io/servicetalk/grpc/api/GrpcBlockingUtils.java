/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.BlockingUtils;

/**
 * Utilities for bridging asynchronous sources to a blocking invocation for generated gRPC code.
 */
public final class GrpcBlockingUtils {

    private GrpcBlockingUtils() {
        // no instances
    }

    /**
     * Subscribes to a {@link Single} immediately and awaits the result. On interruption the current thread's interrupt
     * status is preserved and the underlying operation is cancelled.
     *
     * @param source the {@link Single} to await.
     * @param <T> the type of the result.
     * @return the result of the {@link Single}.
     * @throws Exception {@link InterruptedException} upon interruption or an unchecked exception for any other failure.
     */
    public static <T> T blockingInvocation(Single<T> source) throws Exception {
        return BlockingUtils.blockingInvocation(source);
    }

    /**
     * Subscribes to a {@link Completable} immediately and awaits completion. On interruption the current thread's
     * interrupt status is preserved and the underlying operation is cancelled.
     *
     * @param source the {@link Completable} to await.
     * @throws Exception {@link InterruptedException} upon interruption or an unchecked exception for any other failure.
     */
    public static void blockingInvocation(Completable source) throws Exception {
        BlockingUtils.blockingInvocation(source);
    }
}
