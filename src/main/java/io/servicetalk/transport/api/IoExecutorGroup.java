/**
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A group of {@link IoExecutor}s.
 */
public interface IoExecutorGroup extends ListenableAsyncCloseable {
    /**
     * Get the next executor to use.
     * @return the next executor to use.
     */
    IoExecutor next();

    /**
     * Determine if UDS are supported.
     * @return {@code true} if UDS are supported.
     */
    boolean isUnixDomainSocketSupported();

    /**
     * Determine if fd addresses are supported.
     * @return {@code true} if supported
     */
    boolean isFileDescriptorSocketAddressSupported();

    /**
     * Signals this object should be closed with a delay.
     * @param quietPeriod the object will not accept new work within this time duration.
     * @param timeout     the maximum amount of time to wait until this object is closed
     *                    regardless if new work was submitted during the quiet period.
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}.
     * @return a future that will complete when this object is closed.
     */
    Completable closeAsync(long quietPeriod, long timeout, TimeUnit unit);

    @Override
    default Completable closeAsync() {
        return closeAsync(2, 15, SECONDS);
    }
}
