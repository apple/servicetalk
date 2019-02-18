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

import io.servicetalk.concurrent.internal.DelayedCancellable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class TimerCompletable extends Completable {
    private final Executor timeoutExecutor;
    private final long delayNs;

    TimerCompletable(final Duration delay,
                     final Executor timeoutExecutor) {
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
        this.delayNs = delay.toNanos();
    }

    TimerCompletable(final long delay,
                     final TimeUnit unit,
                     final Executor timeoutExecutor) {
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
        this.delayNs = unit.toNanos(delay);
    }

    @Override
    protected void handleSubscribe(final Subscriber subscriber) {
        DelayedCancellable cancellable = new DelayedCancellable();
        subscriber.onSubscribe(cancellable);
        try {
            cancellable.delayedCancellable(
                    timeoutExecutor.schedule(subscriber::onComplete, delayNs, NANOSECONDS));
        } catch (Throwable cause) {
            subscriber.onError(cause);
        }
    }
}
