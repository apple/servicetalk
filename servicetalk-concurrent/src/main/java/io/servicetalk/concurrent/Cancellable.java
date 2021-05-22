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
package io.servicetalk.concurrent;

/**
 * An entity that can be cancelled.
 *
 * <p>Cancellations are a hint from a consumer of any asynchronous result to the producer that it is no more interested
 * in the result. ServiceTalk does not provide any guarantees that at a certain time, whether the asynchronous execution
 * is cancellable or not. It is up to the producer of data to take any action on cancellation or ignore the same.
 * Thus, a consumer of the result must <em>not</em> assume that it will not receive any callback post cancellation i.e.
 * after {@link #cancel()} returns.
 */
@FunctionalInterface
public interface Cancellable {

    /**
     * A {@code no-op} instance of {@link Cancellable}.
     */
    Cancellable IGNORE_CANCEL = () -> { };

    /**
     * Sends a hint to the producer of the associated asynchronous execution that the consumer related to this
     * {@code Cancellable} is not interested in the outcome of the execution.
     */
    void cancel();
}
