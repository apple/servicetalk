/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Helper operator for signaling the end of an HTTP Request/Response cycle.
 *
 * <p>{@link StreamingHttpRequest} and {@link StreamingHttpResponse} are nested sources ({@link Single} of meta-data
 * containing a payload {@link Publisher}), which makes it non-trivial to get a single signal at the end of this
 * Request/Response cycle. One needs to consider and coordinate between the multitude of outcomes: cancel/success/error
 * across both sources.</p>
 * <p>This operator ensures that the provided callback is triggered just once whenever the sources reach a terminal
 * state across both sources.</p>
 *
 * Example usage tracking the begin and end of a request:
 *
 * <pre>{@code
 *     // coarse grained, any terminal signal calls the provided `Runnable`
 *     return requester.request(strategy, request)
 *                     .beforeOnSubscribe(__ -> tracker.requestStarted())
 *                     .liftSync(new BeforeFinallyHttpOperator(tracker::requestFinished));
 *
 *     // fine grained, `tracker` implements `TerminalSignalConsumer`, terminal signal indicated by the callback method
 *     return requester.request(strategy, request)
 *                     .beforeOnSubscribe(__ -> tracker.requestStarted())
 *                     .liftSync(new BeforeFinallyHttpOperator(tracker));
 * }</pre>
 */
public final class BeforeFinallyHttpOperator implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {

    private final TerminalSignalConsumer beforeFinally;
    private final boolean discardEventsAfterCancel;

    /**
     * Create a new instance.
     *
     * @param beforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public BeforeFinallyHttpOperator(final TerminalSignalConsumer beforeFinally) {
        this(beforeFinally, false);
    }

    /**
     * Create a new instance.
     *
     * @param beforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public BeforeFinallyHttpOperator(final Runnable beforeFinally) {
        this(TerminalSignalConsumer.from(beforeFinally));
    }

    /**
     * Create a new instance.
     *
     * @param beforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     * @param discardEventsAfterCancel if {@code true} further events will be discarded if those arrive after
     * {@link TerminalSignalConsumer#cancel()} is invoked. Otherwise, events may still be delivered if they race with
     * cancellation.
     */
    public BeforeFinallyHttpOperator(final TerminalSignalConsumer beforeFinally, boolean discardEventsAfterCancel) {
        this.beforeFinally = requireNonNull(beforeFinally);
        this.discardEventsAfterCancel = discardEventsAfterCancel;
    }

    @Override
    public SingleSource.Subscriber<? super StreamingHttpResponse> apply(
            final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling beforeFinally#onCancel when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(subscriber, beforeFinally, discardEventsAfterCancel);
    }

    private static final class ResponseCompletionSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {
        private static final int IDLE = 0;
        private static final int PROCESSING_PAYLOAD = 1;
        private static final int DELIVERING_PAYLOAD = 2;
        private static final int AWAITING_CANCEL = 3;
        private static final int TERMINATED = 4;
        private static final AtomicIntegerFieldUpdater<ResponseCompletionSubscriber> stateUpdater =
                newUpdater(ResponseCompletionSubscriber.class, "state");
        private static final SingleSource.Subscriber<StreamingHttpResponse> NOOP_SUBSCRIBER =
                new SingleSource.Subscriber<StreamingHttpResponse>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
            }

            @Override
            public void onSuccess(@Nullable final StreamingHttpResponse result) {
            }

            @Override
            public void onError(final Throwable t) {
            }
        };

        private SingleSource.Subscriber<? super StreamingHttpResponse> subscriber;
        private final TerminalSignalConsumer beforeFinally;
        private final boolean discardEventsAfterCancel;
        private volatile int state;

        ResponseCompletionSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> sub,
                                     final TerminalSignalConsumer beforeFinally,
                                     final boolean discardEventsAfterCancel) {
            this.subscriber = sub;
            this.beforeFinally = beforeFinally;
            this.discardEventsAfterCancel = discardEventsAfterCancel;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                try {
                    if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                        beforeFinally.cancel();
                    }
                } finally {
                    // Cancel unconditionally, let the original Single handle cancel post termination, if required
                    cancellable.cancel();
                }
            });
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            if (response == null) {
                sendNullResponse();
            } else if (stateUpdater.compareAndSet(this, IDLE, PROCESSING_PAYLOAD)) {
                subscriber.onSuccess(response.transformMessageBody(payload ->
                        payload.liftSync(subscriber ->
                                new Subscriber<Object>() {
                                    @Nullable
                                    private Subscription subscription;

                                    @Override
                                    public void onSubscribe(final Subscription subscription) {
                                        this.subscription = subscription;
                                        subscriber.onSubscribe(new Subscription() {
                                            @Override
                                            public void request(final long n) {
                                                subscription.request(n);
                                            }

                                            @Override
                                            public void cancel() {
                                                if (!discardEventsAfterCancel) {
                                                    try {
                                                        if (stateUpdater.compareAndSet(
                                                                ResponseCompletionSubscriber.this,
                                                                PROCESSING_PAYLOAD, TERMINATED)) {
                                                            beforeFinally.cancel();
                                                        }
                                                    } finally {
                                                        subscription.cancel();
                                                    }
                                                    return;
                                                }

                                                for (;;) {
                                                    final int state = ResponseCompletionSubscriber.this.state;
                                                    assert state != IDLE;
                                                    if (state == PROCESSING_PAYLOAD) {
                                                        if (stateUpdater.compareAndSet(
                                                                ResponseCompletionSubscriber.this,
                                                                PROCESSING_PAYLOAD, TERMINATED)) {
                                                            try {
                                                                beforeFinally.cancel();
                                                            } finally {
                                                                subscription.cancel();
                                                            }
                                                            break;
                                                        }
                                                    } else if (state == DELIVERING_PAYLOAD) {
                                                        if (stateUpdater.compareAndSet(
                                                                ResponseCompletionSubscriber.this,
                                                                DELIVERING_PAYLOAD, AWAITING_CANCEL)) {
                                                            break;
                                                        }
                                                    } else if (state == TERMINATED) {
                                                        // still propagate cancel to the original subscription:
                                                        subscription.cancel();
                                                        break;
                                                    } else {
                                                        // cancel can be invoked multiple times
                                                        assert state == AWAITING_CANCEL;
                                                        break;
                                                    }
                                                }
                                            }
                                        });
                                    }

                                    @Override
                                    public void onNext(@Nullable final Object o) {
                                        if (!discardEventsAfterCancel) {
                                            subscriber.onNext(o);
                                            return;
                                        }

                                        boolean reentry = false;
                                        for (;;) {
                                            final int state = ResponseCompletionSubscriber.this.state;
                                            assert state != IDLE;
                                            if (state == TERMINATED) {
                                                // We already cancelled and have to discard further events
                                                return;
                                            }
                                            if (state == DELIVERING_PAYLOAD || state == AWAITING_CANCEL) {
                                                reentry = true;
                                                break;
                                            }
                                            if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                    PROCESSING_PAYLOAD, DELIVERING_PAYLOAD)) {
                                                break;
                                            }
                                        }

                                        try {
                                            subscriber.onNext(o);
                                        } finally {
                                            // Re-entry -> don't unlock
                                            if (!reentry) {
                                                for (;;) {
                                                    final int state = ResponseCompletionSubscriber.this.state;
                                                    assert state != IDLE;
                                                    assert state != PROCESSING_PAYLOAD;
                                                    if (state == TERMINATED) {
                                                        break;
                                                    }
                                                    if (state == DELIVERING_PAYLOAD) {
                                                        if (stateUpdater.compareAndSet(
                                                                ResponseCompletionSubscriber.this,
                                                                DELIVERING_PAYLOAD, PROCESSING_PAYLOAD)) {
                                                            break;
                                                        }
                                                    } else if (stateUpdater.compareAndSet(
                                                            ResponseCompletionSubscriber.this,
                                                            AWAITING_CANCEL, TERMINATED)) {
                                                        try {
                                                            beforeFinally.cancel();
                                                        } finally {
                                                            assert subscription != null;
                                                            subscription.cancel();
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(final Throwable t) {
                                        if (!discardEventsAfterCancel) {
                                            try {
                                                if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                        PROCESSING_PAYLOAD, TERMINATED)) {
                                                    beforeFinally.onError(t);
                                                }
                                            } catch (Throwable cause) {
                                                addSuppressed(t, cause);
                                            }
                                            subscriber.onError(t);
                                            return;
                                        }

                                        final int prevState = setTerminalState();
                                        if (prevState == TERMINATED) {
                                            // We already cancelled and have to discard further events
                                            return;
                                        }
                                        // Propagate original cancel to let Subscription observe it
                                        final boolean propagateCancel = prevState == AWAITING_CANCEL;

                                        try {
                                            beforeFinally.onError(t);
                                        } catch (Throwable cause) {
                                            addSuppressed(t, cause);
                                        }
                                        try {
                                            subscriber.onError(t);
                                        } finally {
                                            cancel0(propagateCancel);
                                        }
                                    }

                                    @Override
                                    public void onComplete() {
                                        if (!discardEventsAfterCancel) {
                                            try {
                                                if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                        PROCESSING_PAYLOAD, TERMINATED)) {
                                                    beforeFinally.onComplete();
                                                }
                                            } catch (Throwable cause) {
                                                subscriber.onError(cause);
                                                return;
                                            }
                                            subscriber.onComplete();
                                            return;
                                        }

                                        final int prevState = setTerminalState();
                                        if (prevState == TERMINATED) {
                                            // We already cancelled and have to discard further events
                                            return;
                                        }
                                        // Propagate original cancel to let Subscription observe it
                                        final boolean propagateCancel = prevState == AWAITING_CANCEL;

                                        try {
                                            try {
                                                beforeFinally.onComplete();
                                            } catch (Throwable cause) {
                                                subscriber.onError(cause);
                                                return;
                                            }
                                            subscriber.onComplete();
                                        } finally {
                                            cancel0(propagateCancel);
                                        }
                                    }

                                    private int setTerminalState() {
                                        for (;;) {
                                            final int state = ResponseCompletionSubscriber.this.state;
                                            assert state != IDLE;
                                            if (state == TERMINATED) {
                                                // We already cancelled and have to discard further events
                                                return state;
                                            }
                                            if (state == PROCESSING_PAYLOAD) {
                                                if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                        PROCESSING_PAYLOAD, TERMINATED)) {
                                                    return state;
                                                }
                                            } else if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                    state, TERMINATED)) {
                                                // re-entry, but we can terminate because this is a final event:
                                                return state;
                                            }
                                        }
                                    }

                                    private void cancel0(final boolean propagateCancel) {
                                        if (propagateCancel) {
                                            assert subscription != null;
                                            subscription.cancel();
                                        }
                                    }
                                })
                ));
            } else {
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == TERMINATED;
                if (discardEventsAfterCancel) {
                    return;
                }
                subscriber.onSuccess(response.transformMessageBody(payload -> {
                    // We have been cancelled. Subscribe and cancel the content so that we do not hold up the
                    // connection and indicate that there is no one else that will subscribe.
                    toSource(payload).subscribe(CancelImmediatelySubscriber.INSTANCE);
                    return Publisher.failed(new CancellationException("Received response post cancel."));
                }));
            }
            dereferenceSubscriber();
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    beforeFinally.onError(t);
                } else if (discardEventsAfterCancel) {
                    return;
                }
            } catch (Throwable cause) {
                addSuppressed(t, cause);
            }
            subscriber.onError(t);
            dereferenceSubscriber();
        }

        private void sendNullResponse() {
            try {
                // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    beforeFinally.onComplete();
                } else if (discardEventsAfterCancel) {
                    return;
                }
            } catch (Throwable cause) {
                subscriber.onError(cause);
                dereferenceSubscriber();
                return;
            }
            subscriber.onSuccess(null);
            dereferenceSubscriber();
        }

        private void dereferenceSubscriber() {
            // After terminating the Single subscriber we need to dereference the Single subscriber as otherwise
            // we may retain a reference to a downstream Subscriber and violate RS 3.13.
            // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.4/README.md#3.13
            subscriber = NOOP_SUBSCRIBER;
        }
    }
}
