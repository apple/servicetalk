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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Publisher} that delays the {@link PublisherSource#subscribe(Subscriber) subscribe}
 * operation to another {@link PublisherSource} until {@link #processSubscribers()} is called.
 */
final class DelayedSubscribePublisher<T> extends Publisher<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DelayedSubscribePublisher, Object> stateUpdater =
            newUpdater(DelayedSubscribePublisher.class, Object.class, "state");
    private static final Object ALLOW_SUBSCRIBE = new Object();
    private static final Object DRAINING_SUBSCRIBERS = new Object();

    private final PublisherSource<T> delayedPublisher;
    /**
     * One of the following:
     * <ul>
     *     <li>{@code null} - initial state</li>
     *     <li>{@link #ALLOW_SUBSCRIBE} - {@link #handleSubscribe(Subscriber)} methods will pass through to
     *     {@link #delayedPublisher}</li>
     *     <li>{@link #DRAINING_SUBSCRIBERS} - set in {@link #processSubscribers()} while calling
     *     {@link PublisherSource#subscribe(Subscriber)} on each {@link Publisher}</li>
     *     <li>{@link Subscriber} - if there is a single {@link #handleSubscribe(Subscriber)} pending</li>
     *     <li>{@code Object[]} - if there are multiple {@link #handleSubscribe(Subscriber)} calls pending</li>
     * </ul>
     */
    @Nullable
    private volatile Object state;

    /**
     * Create a new instance.
     *
     * @param delayedPublisher The {@link PublisherSource} to subscribe to when {@link #processSubscribers()} is
     * called.
     */
    DelayedSubscribePublisher(final PublisherSource<T> delayedPublisher) {
        this.delayedPublisher = requireNonNull(delayedPublisher);
    }

    /**
     * Any previous calls to {@link PublisherSource#subscribe(Subscriber)} on this object will be
     * forwarded to the delayed {@link PublisherSource}. All future calls to
     * {@link PublisherSource#subscribe(Subscriber)} will behave as a direct pass through to the
     * delayed {@link PublisherSource} after this method is called.
     */
    void processSubscribers() {
        for (;;) {
            Object currentState = state;
            if (currentState == null) {
                if (stateUpdater.compareAndSet(this, null, ALLOW_SUBSCRIBE)) {
                    break;
                }
            } else if (currentState == ALLOW_SUBSCRIBE) {
                break;
            } else if (currentState instanceof Subscriber) {
                @SuppressWarnings("unchecked")
                Subscriber<? super T> currentSubscriber = (Subscriber<? super T>) currentState;
                if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                    delayedPublisher.subscribe(currentSubscriber);
                    if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                        break;
                    }
                }
            } else if (stateUpdater.compareAndSet(this, currentState, DRAINING_SUBSCRIBERS)) {
                assert currentState != DRAINING_SUBSCRIBERS;
                @SuppressWarnings("unchecked")
                Subscriber<? super T>[] subscribers = (Subscriber<? super T>[]) currentState;
                for (Subscriber<? super T> subscriber : subscribers) {
                    delayedPublisher.subscribe(subscriber);
                }
                if (stateUpdater.compareAndSet(this, DRAINING_SUBSCRIBERS, ALLOW_SUBSCRIBE)) {
                    break;
                }
            }
        }
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        for (;;) {
            Object currentState = state;
            if (currentState == null || currentState == DRAINING_SUBSCRIBERS) {
                if (stateUpdater.compareAndSet(this, currentState, subscriber)) {
                    break;
                }
            } else if (currentState == ALLOW_SUBSCRIBE) {
                delayedPublisher.subscribe(subscriber);
                break;
            } else if (currentState instanceof Subscriber) {
                // Ideally we can propagate the onSubscribe ASAP to allow for cancellation but this publisher is
                // designed to defer the subscribe until some other condition occurs, so no work will actually be
                // done until that later time.
                @SuppressWarnings("unchecked")
                Subscriber<? super T> currentSubscriber = (Subscriber<? super T>) currentState;
                @SuppressWarnings("unchecked")
                Subscriber<? super T>[] subscribers = (Subscriber<? super T>[]) Array.newInstance(Subscriber.class, 2);
                subscribers[0] = currentSubscriber;
                subscribers[1] = subscriber;
                if (stateUpdater.compareAndSet(this, currentState, subscribers)) {
                    break;
                }
            } else {
                @SuppressWarnings("unchecked")
                Subscriber<? super T>[] subscribers = (Subscriber<? super T>[]) currentState;
                // Unmodifiable collection to avoid issues with concurrent adding/draining with processSubscribers.
                // The expected cardinality of the array will be low, so copy/resize is "good enough" for now.
                Subscriber<? super T>[] newSubscribers = Arrays.copyOf(subscribers, subscribers.length + 1);
                newSubscribers[subscribers.length] = subscriber;
                if (stateUpdater.compareAndSet(this, currentState, newSubscribers)) {
                    break;
                }
            }
        }
    }

    @Override
    public String toString() {
        return DelayedSubscribePublisher.class.getSimpleName() + "(" + delayedPublisher + ")";
    }
}
