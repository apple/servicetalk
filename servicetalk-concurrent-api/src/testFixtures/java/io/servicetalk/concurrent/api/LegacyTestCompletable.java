/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deprecated.
 *
 * @deprecated Use {@link TestCompletable} instead.
 */
@Deprecated
public class LegacyTestCompletable extends Completable implements CompletableSource.Subscriber {
    private final Queue<Subscriber> subscribers = new ConcurrentLinkedQueue<>();
    private final CancellableSet dynamicCancellable = new CancellableSet();
    private final boolean invokeListenerPostCancel;
    private boolean deferOnSubscribe;
    @Nullable
    private TerminalNotification terminalNotification;

    public LegacyTestCompletable(boolean invokeListenerPostCancel, boolean deferOnSubscribe) {
        this.invokeListenerPostCancel = invokeListenerPostCancel;
        this.deferOnSubscribe = deferOnSubscribe;
    }

    public LegacyTestCompletable() {
        this(false, false);
    }

    @Override
    public synchronized void handleSubscribe(Subscriber subscriber) {
        subscribers.add(subscriber);
        dynamicCancellable.add(() -> {
            if (!invokeListenerPostCancel) {
                subscribers.remove(subscriber);
            }
        });
        if (!deferOnSubscribe) {
            subscriber.onSubscribe(dynamicCancellable);
        }
        if (terminalNotification != null) {
            subscribers.remove(subscriber);
            terminalNotification.terminate(subscriber);
        }
    }

    public void sendOnSubscribe() {
        assert deferOnSubscribe;
        deferOnSubscribe = false;
        subscribers.forEach(s -> s.onSubscribe(dynamicCancellable));
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        dynamicCancellable.add(cancellable);
    }

    @Override
    public synchronized void onComplete() {
        for (Subscriber subscriber : subscribers) {
            subscriber.onComplete();
        }
        subscribers.clear();
        terminalNotification = complete();
    }

    @Override
    public synchronized void onError(Throwable t) {
        for (Subscriber subscriber : subscribers) {
            subscriber.onError(t);
        }
        subscribers.clear();
        terminalNotification = TerminalNotification.error(t);
    }

    public boolean isCancelled() {
        return dynamicCancellable.isCancelled();
    }

    public LegacyTestCompletable verifyListenCalled() {
        assertThat("Listen not called.", subscribers, hasSize(greaterThan(0)));
        return this;
    }

    public LegacyTestCompletable verifyListenNotCalled() {
        assertThat("Listen called.", subscribers, hasSize(0));
        return this;
    }

    public LegacyTestCompletable verifyCancelled() {
        assertTrue(isCancelled(), "Subscriber did not cancel.");
        return this;
    }

    public LegacyTestCompletable verifyNotCancelled() {
        assertFalse(isCancelled(), "Subscriber cancelled.");
        return this;
    }
}
