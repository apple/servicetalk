/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent;

/**
 * An extension of {@link AutoCloseable} to add graceful closure semantics.
 */
public interface GracefulAutoCloseable extends AutoCloseable {

    /**
     * Used to close/shutdown a resource, similar to {@link #close()}, but attempts to cleanup state before
     * abruptly closing. This provides a hint that implementations can use to stop accepting new work and finish in
     * flight work. This method is implemented on a "best effort" basis and may be equivalent to {@link #close()}.
     * <p>
     * <b>Note</b>: Implementations may or may not apply a timeout for this operation to complete, if a caller does not
     * want to wait indefinitely, and are unsure if the implementation applies a timeout, it is advisable to apply a
     * timeout and force a call to {@link #close()}.
     *
     * @throws Exception if graceful closure failed.
     */
    default void closeGracefully() throws Exception {
        close();
    }
}
