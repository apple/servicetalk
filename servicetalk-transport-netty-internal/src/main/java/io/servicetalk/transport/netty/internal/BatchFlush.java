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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import static java.util.Objects.requireNonNull;

final class BatchFlush implements FlushStrategy {

    private final Publisher<?> boundaries;
    private final int batchSize;

    BatchFlush(Publisher<?> durationBoundaries, int batchSize) {
        this.boundaries = requireNonNull(durationBoundaries);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected > 0)");
        }
        this.batchSize = batchSize;
    }

    @Override
    public WriteEventsListener apply(final FlushSender sender) {
        return new BatchFlushListener(boundaries, batchSize, sender);
    }

    static final class BatchFlushListener implements WriteEventsListener {

        private final Publisher<?> boundaries;
        private final int batchSize;
        private final FlushSender sender;
        /**
         * This field is accessed from {@link #writeCancelled()} as well as {@link #writeTerminated()} which can be
         * concurrent, so we use {@link DelayedCancellable} to avoid concurrent invocation of the actual
         * {@link Cancellable}.
         */
        private final DelayedCancellable boundariesCancellable = new DelayedCancellable();
        private int unflushedCount;

        BatchFlushListener(final Publisher<?> boundaries, final int batchSize, final FlushSender sender) {
            this.boundaries = boundaries;
            this.batchSize = batchSize;
            this.sender = sender;
        }

        @Override
        public void writeStarted() {
            boundariesCancellable.delayedCancellable(boundaries.forEach(__ -> sender.flush()));
        }

        @Override
        public void itemWritten() {
            if (++unflushedCount == batchSize) {
                unflushedCount = 0;
                sender.flush();
            }
        }

        @Override
        public void writeTerminated() {
            boundariesCancellable.cancel();
            if (unflushedCount > 0) {
                // Since this is a terminal call and no other method apart from writeCancelled can be called, do not
                // reset state.
                sender.flush();
            }
        }

        @Override
        public void writeCancelled() {
            boundariesCancellable.cancel();
        }
    }
}
