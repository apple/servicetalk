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

import javax.annotation.Nullable;

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

    static final class BatchFlushListener extends NoOpWriteEventsListener {

        private final Publisher<?> boundaries;
        private final int batchSize;
        private final FlushSender sender;
        @Nullable
        private Cancellable boundariesCancellable;
        private int unflushedCount;

        BatchFlushListener(final Publisher<?> boundaries, final int batchSize, final FlushSender sender) {
            this.boundaries = boundaries;
            this.batchSize = batchSize;
            this.sender = sender;
        }

        @Override
        public void writeStarted() {
            boundariesCancellable = boundaries.forEach(__ -> sender.flush());
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
            assert boundariesCancellable != null; // Guaranteed to be called after writeStarted()
            boundariesCancellable.cancel();
            if (unflushedCount > 0) {
                unflushedCount = 0;
                sender.flush();
            }
        }

        @Override
        public void writeCancelled() {
            assert boundariesCancellable != null; // Guaranteed to be called after writeStarted()
            boundariesCancellable.cancel();
        }
    }
}
