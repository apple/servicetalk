/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Used to close/shutdown a resource.
 */
@FunctionalInterface
public interface AsyncCloseable {

    /**
     * Used to close/shutdown a resource.
     *
     * @return A {@link Completable} that is notified once the close is complete.
     */
    Completable closeAsync();

    /**
     * Used to close/shutdown a resource, similar to {@link #closeAsync()}, but attempts to close gracefully. This is
     * generally done by signalling a desire to close/shutdown to internal components/resources, and waiting for them
     * to finish what they're doing and close/shutdown themselves.
     * <p>
     * <b>Note</b>: This has no timeout, so the previously mentioned waiting may wait indefinitely. In this case, the
     * returned {@link Completable} will not complete until {@link #closeAsync()} or
     * {@link #closeAsyncGracefully(long, TimeUnit)} is called.
     * </p>
     * <p>
     * Implementations <i>may</i> choose to ignore the "graceful" part of this and simply call {@link #closeAsync}.
     *
     * @return A {@link Completable} that is notified once the close is complete.
     */
    default Completable closeAsyncGracefully() {
        return closeAsync();
    }

    /**
     * Used to close/shutdown a resource, similar to {@link #closeAsync()}, but attempts to close gracefully. This is
     * generally done by:
     * <ol>
     * <li>signalling a desire to close/shutdown to internal components/resources,</li>
     * <li>waiting some amount of time for them to finish what they're doing and close/shutdown themselves,</li>
     * <li>after the time has elapsed, non-gracefully closing anything that hadn't yet finished.</li>
     * </ol>
     * Implementations <i>may</i> choose to ignore the "graceful" part of this and simply call {@link #closeAsync}.
     *
     * @param timeout approximate upper bound on how long to wait for the graceful closure to complete before closing
     * non-gracefully.
     * @param timeoutUnit The units for {@code timeout}.
     * @return A {@link Completable} that is notified once the close is complete.
     */
    default Completable closeAsyncGracefully(long timeout, TimeUnit timeoutUnit) {
        return closeAsyncGracefully().timeout(timeout, timeoutUnit).onErrorResume(
                t -> t instanceof TimeoutException ? closeAsync() : Completable.error(t)
        );
    }
}
