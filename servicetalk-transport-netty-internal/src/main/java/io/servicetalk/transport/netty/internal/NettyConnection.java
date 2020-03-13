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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import java.util.function.Supplier;

/**
 * A wrapper over a physical connection providing a way to read/write as a {@link Publisher}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public interface NettyConnection<Read, Write> extends NettyConnectionContext {
    /**
     * Returns {@link Publisher} that emits all items as read from this connection.
     *
     * @return {@link Publisher} that emits all items as read from this connection.
     * Concurrent subscribes when a {@link Subscriber} is already active) are disallowed but sequential subscribes when
     * a previous {@link Subscriber} has terminated) are allowed.
     */
    Publisher<Read> read();

    /**
     * Writes all elements emitted by the passed {@link Publisher} on this connection.
     *
     * @param write {@link Publisher}, all objects emitted from which are written on this connection.
     *
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if any item emitted can not be written successfully on the connection.</li>
     *     <li>With an error, if {@link Publisher} emits an error.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully when {@link Publisher} completes and all items emitted are written successfully.</li>
     * </ul>
     */
    Completable write(Publisher<Write> write);

    /**
     * Writes all elements emitted by the passed {@link Publisher} on this connection.
     *
     * @param write {@link Publisher}, all objects emitted from which are written on this connection.
     * @param flushStrategySupplier {@link Supplier} of {@link FlushStrategy} which controls the flush operations
     * for this write.
     * @param demandEstimatorSupplier A {@link Supplier} of {@link WriteDemandEstimator} for this write.
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if any item emitted can not be written successfully on the connection.</li>
     *     <li>With an error, if {@link Publisher} emits an error.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully when {@link Publisher} completes and all items emitted are written successfully.</li>
     * </ul>
     */
    Completable write(Publisher<Write> write,
                      Supplier<FlushStrategy> flushStrategySupplier,
                      Supplier<WriteDemandEstimator> demandEstimatorSupplier);
}
