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

import io.servicetalk.concurrent.Cancellable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_EMITTING;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_IDLE;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerCollectionDelayThrow;

final class QueueDynamicCompositeCancellable implements DynamicCompositeCancellable {
    private static final AtomicIntegerFieldUpdater<QueueDynamicCompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(QueueDynamicCompositeCancellable.class, "cancelled");
    @SuppressWarnings("unused") private volatile int cancelled;

    private static final AtomicIntegerFieldUpdater<QueueDynamicCompositeCancellable> drainingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(QueueDynamicCompositeCancellable.class, "draining");
    @SuppressWarnings("unused") private volatile int draining;

    // TODO(scott): consider using a MPSC queue from JCTools once remove is supported.
    // https://github.com/JCTools/JCTools/pull/193#issuecomment-329958251
    private final Queue<Cancellable> cancellables = new ConcurrentLinkedQueue<>();

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
            cancelAll();
        }
    }

    @Override
    public boolean add(Cancellable toAdd) {
        if (isCancelled()) {
            toAdd.cancel();
            return false;
        } else {
            boolean offered = cancellables.offer(toAdd);
            assert offered : "queue shouldn't reject offer because it should be unbounded!";
            if (isCancelled()) {
                cancelAll();
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        return cancellables.remove(toRemove);
    }

    @Override
    public boolean isCancelled() {
        return cancelled != 0;
    }

    private void cancelAll() {
        drainSingleConsumerCollectionDelayThrow(cancellables, Cancellable::cancel, drainingUpdater, this);
    }
}
