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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

final class CollectingCompletableSubscriber implements Subscriber, Cancellable {

    private final DelayedCancellable cancellable = new DelayedCancellable();

    /**
     * Despite being volatile, `terminal` does not need to be modified atomically.
     * When written from `Subscriber` methods, RS specifies that these may not be concurrent.
     * When written from the `take*` methods, anything that would require atomic writes is effectively a race
     * between test code, and the code under test, so atomic guarantees won't help any.
     */
    @Nullable
    private volatile TerminalNotification terminal;
    private volatile boolean cancellableReceived;

    public boolean cancellableReceived() {
        return cancellableReceived;
    }

    public Cancellable cancellable() {
        return cancellable;
    }

    @Nullable
    public TerminalNotification terminal() {
        return terminal;
    }

    @Nullable
    public TerminalNotification takeTerminal() {
        TerminalNotification terminal = this.terminal;
        this.terminal = null;
        return terminal;
    }

    @Nullable
    public Throwable error() {
        final TerminalNotification terminal = this.terminal;
        if (terminal == null) {
            return null;
        }
        return terminal == complete() ? null : terminal.cause();
    }

    @Nullable
    public Throwable takeError() {
        final Throwable error = error();
        this.terminal = null;
        return error;
    }

    public boolean isCompleted() {
        return terminal == complete();
    }

    public boolean isErrored() {
        final TerminalNotification terminal = this.terminal;
        return terminal != null && terminal != complete();
    }

    public boolean isTerminated() {
        return terminal != null;
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
    public void onComplete() {
        terminal = complete();
    }

    @Override
    public void onError(final Throwable t) {
        terminal = TerminalNotification.error(t);
    }
}
