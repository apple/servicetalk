/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Completable} that delays the {@link CompletableSource#subscribe(Subscriber) subscribe}
 * operation to another {@link CompletableSource} until {@link #processSubscribers()} is called.
 */
final class DelayedSubscribeCompletable extends Completable {
    private static final AtomicReferenceFieldUpdater<DelayedSubscribeCompletable, Object> stateUpdater =
            newUpdater(DelayedSubscribeCompletable.class, Object.class, "state");
    private static final Object ALLOW_SUBSCRIBE = new Object();
    private static final Object DRAINING_SUBSCRIBERS = new Object();

    private final CompletableSource delayedCompletable;
    /**
     * One of the following:
     * <ul>
     *     <li>{@code null} - initial state</li>
     *     <li>{@link Throwable} - if {@link #failSubscribers(Throwable)} is called</li>
     *     <li>{@link #ALLOW_SUBSCRIBE} - {@link #handleSubscribe(Subscriber)} methods will
     *     pass through to {@link #delayedCompletable}</li>
     *     <li>{@link #DRAINING_SUBSCRIBERS} - set in {@link #processSubscribers()} while calling
     *     {@link CompletableSource#subscribe(Subscriber)} on each {@link Completable}</li>
     *     <li>{@link Subscriber} - if there is a single{@link #handleSubscribe(Subscriber)} pending</li>
     *     <li>{@code Object[]} - if there are multiple {@link #handleSubscribe(Subscriber)} calls pending</li>
     * </ul>
     */
    @Nullable
    private volatile Object state;

    /**
     * Create a new instance.
     *
     * @param delayedCompletable The {@link CompletableSource} to subscribe to when {@link #processSubscribers()} is
     * called.
     */
    DelayedSubscribeCompletable(final CompletableSource delayedCompletable) {
        this.delayedCompletable = requireNonNull(delayedCompletable);
    }

    /**
     * Terminate any pending/future {@link Subscriber}s via  {@link Subscriber#onError(Throwable)}.
     * @param cause The {@link Throwable} to use for {@link Subscriber#onError(Throwable)}.
     */
    public void failSubscribers(Throwable cause) {
        for (;;) {
            Object currentState = state;
            if (currentState == null || currentState == ALLOW_SUBSCRIBE) {
                if (stateUpdater.compareAndSet(this, currentState, cause)) {
                    break;
                }
            } else if (currentState instanceof Throwable) {
                break;
            } else if (currentState instanceof Subscriber) {
                Subscriber currentSubscriber = (Subscriber) currentState;
                if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                    deliverTerminalFromSource(currentSubscriber, cause);
                    if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, cause)) {
                        break;
                    }
                }
            } else if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                assert currentState != DRAINING_SUBSCRIBERS;
                Subscriber[] subscribers = (Subscriber[]) currentState;
                for (Subscriber subscriber : subscribers) {
                    deliverTerminalFromSource(subscriber, cause);
                }
                if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, cause)) {
                    break;
                }
            }
        }
    }

    /**
     * Any previous calls to {@link CompletableSource#subscribe(Subscriber)} on this object will be
     * forwarded to the delayed {@link CompletableSource}. All future calls to
     * {@link CompletableSource#subscribe(Subscriber)} will behave as a direct pass through to the
     * delayed {@link CompletableSource} after this method is called.
     */
    public void processSubscribers() {
        for (;;) {
            Object currentState = state;
            if (currentState == null) {
                if (stateUpdater.compareAndSet(this, null, ALLOW_SUBSCRIBE)) {
                    break;
                }
            } else if (currentState == ALLOW_SUBSCRIBE || currentState instanceof Throwable) {
                break;
            } else if (currentState instanceof Subscriber) {
                Subscriber currentSubscriber = (Subscriber) currentState;
                if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                    delayedCompletable.subscribe(currentSubscriber);
                    if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                }
            } else if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                assert currentState != DRAINING_SUBSCRIBERS;
                Subscriber[] subscribers = (Subscriber[]) currentState;
                for (Subscriber subscriber : subscribers) {
                    delayedCompletable.subscribe(subscriber);
                }
                if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                    break;
                }
            }
        }
    }

    @Override
    protected void handleSubscribe(final Subscriber subscriber) {
        for (;;) {
            Object currentState = state;
            if (currentState == null || currentState == DRAINING_SUBSCRIBERS) {
                if (stateUpdater.compareAndSet(this, currentState, subscriber)) {
                    break;
                }
            } else if (currentState == ALLOW_SUBSCRIBE) {
                delayedCompletable.subscribe(subscriber);
                break;
            } else if (currentState instanceof Throwable) {
                deliverTerminalFromSource(subscriber, (Throwable) currentState);
                break;
            } else if (currentState instanceof Subscriber) {
                // Ideally we can propagate the onSubscribe ASAP to allow for cancellation but this completable is
                // designed to defer the subscribe until some other condition occurs, so no work will actually be
                // done until that later time.
                Subscriber currentSubscriber = (Subscriber) currentState;
                Subscriber[] subscribers = (Subscriber[]) Array.newInstance(Subscriber.class, 2);
                subscribers[0] = currentSubscriber;
                subscribers[1] = subscriber;
                if (stateUpdater.compareAndSet(this, currentState, subscribers)) {
                    break;
                }
            } else {
                Subscriber[] subscribers = (Subscriber[]) currentState;
                // Unmodifiable collection to avoid issues with concurrent adding/draining with processSubscribers.
                // The expected cardinality of the array will be low, so copy/resize is "good enough" for now.
                Subscriber[] newSubscribers = Arrays.copyOf(subscribers, subscribers.length + 1);
                newSubscribers[subscribers.length] = subscriber;
                if (stateUpdater.compareAndSet(this, currentState, newSubscribers)) {
                    break;
                }
            }
        }
    }

    @Override
    public String toString() {
        return DelayedSubscribeCompletable.class.getSimpleName() + "(" + delayedCompletable + ")";
    }
}
