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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Completable.Subscriber;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Holder of {@link Throwable}.
 */
public final class TerminalNotification {

    private static final TerminalNotification COMPLETE = new TerminalNotification();

    @Nullable
    private final Throwable cause;

    /**
     * New instance.
     *
     * @param cause to hold.
     */
    private TerminalNotification(Throwable cause) {
        this.cause = requireNonNull(cause);
    }

    /**
     * New instance.
     */
    private TerminalNotification() {
        this.cause = null;
    }

    /**
     * Invoke {@link org.reactivestreams.Subscriber#onComplete()} or {@link org.reactivestreams.Subscriber#onError(Throwable)} on the passed {@code subscriber}.
     *
     * @param subscriber to terminate.
     */
    public void terminate(org.reactivestreams.Subscriber<?> subscriber) {
        if (this == COMPLETE) {
            subscriber.onComplete();
        } else {
            subscriber.onError(cause);
        }
    }

    /**
     * Invoke {@link org.reactivestreams.Subscriber#onError(Throwable)} with the passed {@link Throwable} if this {@link TerminalNotification} is for completion.<p>
     * If this {@link TerminalNotification} is for error, then the passed {@link Throwable} will be added as a suppressed cause to the existing {@link Throwable}.
     *
     * <b>If this is an error notification, the associated cause will get updated with the {@code additionalCause}.</b>
     *
     * @param subscriber to terminate.
     * @param additionalCause {@link Throwable} which is used as the caus
     */
    public void terminate(org.reactivestreams.Subscriber<?> subscriber, Throwable additionalCause) {
        if (this == COMPLETE) {
            subscriber.onError(additionalCause);
        } else {
            assert cause != null;
            cause.addSuppressed(additionalCause);
            subscriber.onError(cause);
        }
    }

    /**
     * Invoke {@link Subscriber#onError(Throwable)} with the passed {@link Throwable} if this {@link TerminalNotification} is for completion.<p>
     * If this {@link TerminalNotification} is for error, then the passed {@link Throwable} will be added as a suppressed cause to the existing {@link Throwable}.
     *
     * <b>If this is an error notification, the associated cause will get updated with the {@code additionalCause}.</b>
     *
     * @param subscriber to terminate.
     * @param additionalCause {@link Throwable} which is used as the caus
     */
    public void terminate(Subscriber subscriber, Throwable additionalCause) {
        if (this == COMPLETE) {
            subscriber.onError(additionalCause);
        } else {
            assert cause != null;
            cause.addSuppressed(additionalCause);
            subscriber.onError(cause);
        }
    }

    /**
     * Invoke {@link Subscriber#onComplete()} or {@link Subscriber#onError(Throwable)} on the passed {@code subscriber}.
     *
     * @param subscriber to terminate.
     */
    public void terminate(Subscriber subscriber) {
        if (this == COMPLETE) {
            subscriber.onComplete();
        } else {
            assert cause != null;
            subscriber.onError(cause);
        }
    }

    /**
     * Returns the cause of error if this is an error notification.
     *
     * @return {@link Throwable} if this is an error notification, otherwise {@code null}.
     */
    @Nullable
    public Throwable cause() {
        return cause;
    }

    /**
     * Returns a {@link TerminalNotification} for {@code cause}.
     *
     * @param cause for the notification.
     * @return {@link TerminalNotification} for {@code cause}.
     */
    public static TerminalNotification error(Throwable cause) {
        return new TerminalNotification(cause);
    }

    /**
     * Returns a {@link TerminalNotification} for completion.
     *
     * @return {@link TerminalNotification} for completion.
     */
    public static TerminalNotification complete() {
        return COMPLETE;
    }
}
