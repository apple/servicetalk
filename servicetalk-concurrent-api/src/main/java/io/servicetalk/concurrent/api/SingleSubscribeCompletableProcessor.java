/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.SubscribableSources.SubscribableCompletable;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;

/**
 * Equivalent of {@link Processors#newCompletableProcessor()} that doesn't handle multiple
 * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
 */
final class SingleSubscribeCompletableProcessor extends SubscribableCompletable
        implements CompletableSource.Processor, Cancellable {

    private static final Object CANCELLED = new Object();

    private static final AtomicReferenceFieldUpdater<SingleSubscribeCompletableProcessor, Object> stateUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SingleSubscribeCompletableProcessor.class, Object.class, "state");

    @Nullable
    private volatile Object state;

    @Override
    protected void handleSubscribe(final Subscriber subscriber) {
        try {
            subscriber.onSubscribe(this);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }
        for (; ; ) {
            final Object cState = state;
            if (cState instanceof TerminalNotification) {
                TerminalNotification terminalNotification = (TerminalNotification) cState;
                terminalNotification.terminate(subscriber);
                break;
            } else if (cState instanceof Subscriber) {
                subscriber.onError(new DuplicateSubscribeException(cState, subscriber));
                break;
            } else if (cState == CANCELLED ||
                    cState == null && stateUpdater.compareAndSet(this, null, subscriber)) {
                break;
            }
        }
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        // no op, we never cancel as Subscribers and subscribes are decoupled.
    }

    @Override
    public void onComplete() {
        final Object oldState = stateUpdater.getAndSet(this, TerminalNotification.complete());
        if (oldState instanceof Subscriber) {
            ((Subscriber) oldState).onComplete();
        }
    }

    @Override
    public void onError(final Throwable t) {
        final Object oldState = stateUpdater.getAndSet(this, TerminalNotification.error(t));
        if (oldState instanceof Subscriber) {
            ((Subscriber) oldState).onError(t);
        }
    }

    @Override
    public void cancel() {
        state = CANCELLED;
    }
}
