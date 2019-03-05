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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;

import javax.annotation.Nullable;

final class CollectingSingleSubscriber<T> implements Subscriber<T>, Cancellable {

    public static final Object NULL_RESULT = new Object();

    private final DelayedCancellable cancellable = new DelayedCancellable();

    /**
     * Despite being volatile, `terminal` does not need to be modified atomically.
     * When written from `Subscriber` methods, RS specifies that these may not be concurrent.
     * When written from the `take*` methods, anything that would require atomic writes is effectively a race
     * between test code, and the code under test, so atomic guarantees won't help any.
     */
    @Nullable
    private volatile Object terminal;
    private volatile boolean cancellableReceived;

    public boolean cancellableReceived() {
        return cancellableReceived;
    }

    public Cancellable cancellable() {
        return cancellable;
    }

    public boolean hasResult() {
        return terminal != null && !(terminal instanceof TerminalNotification);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public T result() {
        if (terminal instanceof TerminalNotification || terminal == NULL_RESULT) {
            return null;
        }
        return (T) terminal;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public T takeResult() {
        final Object terminal = this.terminal;
        if (terminal instanceof TerminalNotification) {
            return null;
        }
        this.terminal = null;
        return terminal == NULL_RESULT ? null : (T) terminal;
    }

    @Nullable
    public Throwable error() {
        final Object terminal = this.terminal;
        if (!(terminal instanceof TerminalNotification)) {
            return null;
        }
        return ((TerminalNotification) terminal).cause();
    }

    @Nullable
    public Throwable takeError() {
        final Object terminal = this.terminal;
        if (!(terminal instanceof TerminalNotification)) {
            return null;
        }
        this.terminal = null;
        return ((TerminalNotification) terminal).cause();
    }

    public boolean isSuccess() {
        return hasResult();
    }

    public boolean isErrored() {
        return terminal instanceof TerminalNotification;
    }

    @Override
    public void cancel() {
        cancellable.cancel();
    }

    @Override
    public void onSubscribe(final Cancellable s) {
        cancellable.delayedCancellable(s);
        cancellableReceived = true;
    }

    @Override
    public void onSuccess(final T result) {
        this.terminal = result != null ? result : NULL_RESULT;
    }

    @Override
    public void onError(final Throwable t) {
        terminal = TerminalNotification.error(t);
    }
}
