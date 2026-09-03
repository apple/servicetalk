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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.lang.Thread.currentThread;

/**
 * Shared helpers for {@link BlockingToStreamingService} and {@link BlockingStreamingToStreamingService} to make the
 * {@link Thread#interrupt() thread interrupt} they issue on cancellation optional, per
 * {@link HttpServerBuilder#interruptBlockingServiceOnCancel(boolean)}.
 */
final class ThreadInterruptingCancellableUtils {

    private ThreadInterruptingCancellableUtils() {
    }

    @Nullable
    static ThreadInterruptingCancellable newCancellableIfInterrupting(final boolean interruptOnCancel) {
        return interruptOnCancel ? new ThreadInterruptingCancellable(currentThread()) : null;
    }

    static Cancellable cancellableForSubscribe(@Nullable final ThreadInterruptingCancellable tiCancellable) {
        return tiCancellable != null ? tiCancellable : IGNORE_CANCEL;
    }

    static void setDone(@Nullable final ThreadInterruptingCancellable tiCancellable) {
        if (tiCancellable != null) {
            tiCancellable.setDone();
        }
    }

    static void setDone(@Nullable final ThreadInterruptingCancellable tiCancellable, final Throwable cause) {
        if (tiCancellable != null) {
            tiCancellable.setDone(cause);
        } else if (cause instanceof InterruptedException) {
            // Mirrors ThreadInterruptingCancellable#setDone(Throwable): even when this handler doesn't own an
            // interrupting Cancellable, clear a stale interrupt flag before the (likely pooled) thread is reused,
            // in case something other than cancellation (e.g. executor shutdown) interrupted it during handling.
            Thread.interrupted();
        }
    }
}
