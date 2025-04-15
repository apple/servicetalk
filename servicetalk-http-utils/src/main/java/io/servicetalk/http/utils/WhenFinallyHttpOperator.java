/*
 * Copyright © 2019-2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

class WhenFinallyHttpOperator implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {

    private final TerminalSignalConsumer whenFinally;
    private final boolean discardEventsAfterCancel;
    private final boolean afterFinallyBehavior;

    WhenFinallyHttpOperator(final TerminalSignalConsumer whenFinally, boolean discardEventsAfterCancel,
                            boolean afterFinallyBehavior) {
        this.whenFinally = requireNonNull(whenFinally);
        this.discardEventsAfterCancel = discardEventsAfterCancel;
        this.afterFinallyBehavior = afterFinallyBehavior;
    }

    @Override
    public final SingleSource.Subscriber<? super StreamingHttpResponse> apply(
            final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling beforeFinally#onCancel when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(
                subscriber, whenFinally, discardEventsAfterCancel, afterFinallyBehavior);
    }

    private static final class ResponseCompletionSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {

        private static final int IDLE = 0;
        private static final int PROCESSING_PAYLOAD = 1;
        private static final int RESPONSE_COMPLETE = -1;
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
        private final TerminalSignalConsumer whenFinally;
        private final boolean discardEventsAfterCancel;
        private final boolean afterFinallyBehavior;
        private volatile int state;

        ResponseCompletionSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> sub,
                                     final TerminalSignalConsumer whenFinally,
                                     final boolean discardEventsAfterCancel, final boolean afterFinallyBehavior) {
            this.subscriber = sub;
            this.whenFinally = whenFinally;
            this.discardEventsAfterCancel = discardEventsAfterCancel;
            this.afterFinallyBehavior = afterFinallyBehavior;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                if (afterFinallyBehavior) {
                    try {
                        // Cancel unconditionally, let the original Single handle cancel post termination, if required
                        cancellable.cancel();
                    } finally {
                        tryTriggerCancel();
                    }
                } else {
                    try {
                        tryTriggerCancel();
                    } finally {
                        // Cancel unconditionally, let the original Single handle cancel post termination, if required
                        cancellable.cancel();
                    }
                }
            });
        }

        private void tryTriggerCancel() {
            final int previous = stateUpdater.getAndSet(this, RESPONSE_COMPLETE);
            if (previous == IDLE || previous == PROCESSING_PAYLOAD) {
                whenFinally.cancel();
            }
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            if (response == null) {
                sendNullResponse();
            } else if (stateUpdater.compareAndSet(this, IDLE, PROCESSING_PAYLOAD)) {
                System.err.println(Thread.currentThread().getName() + ": Response received");
                subscriber.onSuccess(response.transformMessageBody(payload ->
                        payload.liftSync(messageBodySubscriber ->
                                // Only the first subscriber needs to be wrapped. Followup subscribers will
                                // most likely fail because duplicate subscriptions to message bodies are not allowed.
                                stateUpdater.compareAndSet(this,
                                        PROCESSING_PAYLOAD, RESPONSE_COMPLETE) ? new MessageBodySubscriber(
                                                messageBodySubscriber, whenFinally, discardEventsAfterCancel,
                                        afterFinallyBehavior) : messageBodySubscriber)
                ));
            } else {
                System.err.println(Thread.currentThread().getName() + ": Response received after cancel");
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == RESPONSE_COMPLETE;
                // The request has been cancelled, but we still received a response. We need to discard the response
                // body or risk leaking hot resources which are commonly attached to a message body.
                toSource(response.messageBody()).subscribe(CancelImmediatelySubscriber.INSTANCE);
                if (!discardEventsAfterCancel) {
                    // Wrap with defer to capture the Subscriber's stack-trace
                    subscriber.onSuccess(response.transformMessageBody(payload -> Publisher.defer(() ->
                            Publisher.failed(new CancellationException("Received response post cancel")))));
                }
            }
            dereferenceSubscriber();
        }

        @Override
        public void onError(final Throwable t) {
            System.err.println(Thread.currentThread().getName() + ": WhenFinallyHttpOperator.onError(" + t + ")");
            new Exception("WhenFinallyHttpOperator.onError(..) stack trace").printStackTrace(System.err);
            if (afterFinallyBehavior) {
                doAfterOnError(t);
            } else {
                doBeforeOnError(t);
            }
        }

        private void doBeforeOnError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
                    whenFinally.onError(t);
                } else if (discardEventsAfterCancel) {
                    return;
                }
            } catch (Throwable cause) {
                addSuppressed(t, cause);
            }
            subscriber.onError(t);
            dereferenceSubscriber();
        }

        private void doAfterOnError(final Throwable t) {
            boolean didComplete = stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE);
            try {
                if (didComplete || !discardEventsAfterCancel) {
                    subscriber.onError(t);
                    dereferenceSubscriber();
                }
            } finally {
                if (didComplete) {
                    whenFinally.onError(t);
                }
            }
        }

        private void sendNullResponse() {
            if (afterFinallyBehavior) {
                doAfterSendNullResponse();
            } else {
                doBeforeSendNullResponse();
            }
        }

        private void doBeforeSendNullResponse() {
            try {
                // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
                if (stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
                    whenFinally.onComplete();
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

        private void doAfterSendNullResponse() {
            boolean didComplete = stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE);
            try {
                if (didComplete || !discardEventsAfterCancel) {
                    subscriber.onSuccess(null);
                    dereferenceSubscriber();
                }
            } finally {
                if (didComplete) {
                    whenFinally.onComplete();
                }
            }
        }

        private void dereferenceSubscriber() {
            // After terminating the Single subscriber we need to dereference the Single subscriber as otherwise
            // we may retain a reference to a downstream Subscriber and violate RS 3.13.
            // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.4/README.md#3.13
            subscriber = NOOP_SUBSCRIBER;
        }
    }

    private static final class MessageBodySubscriber implements Subscriber<Object> {

        private static final int PROCESSING_PAYLOAD = 0;
        private static final int DELIVERING_PAYLOAD = 1;
        private static final int AWAITING_CANCEL = 2;
        private static final int TERMINATED = -1;

        private static final AtomicIntegerFieldUpdater<MessageBodySubscriber> stateUpdater =
                newUpdater(MessageBodySubscriber.class, "state");

        private final Subscriber<? super Object> subscriber;
        private final TerminalSignalConsumer whenFinally;
        private final boolean discardEventsAfterCancel;
        private final boolean afterFinallyBehavior;
        private volatile int state;
        @Nullable
        private Subscription subscription;

        MessageBodySubscriber(final Subscriber<? super Object> subscriber,
                              final TerminalSignalConsumer whenFinally,
                              final boolean discardEventsAfterCancel, final boolean afterFinallyBehavior) {
            this.subscriber = subscriber;
            this.whenFinally = whenFinally;
            this.discardEventsAfterCancel = discardEventsAfterCancel;
            this.afterFinallyBehavior = afterFinallyBehavior;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            this.subscription = subscription;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    subscription.request(n);
                }

                private void tryWhenFinallyCancel() {
                    if (stateUpdater.compareAndSet(WhenFinallyHttpOperator.MessageBodySubscriber.this,
                            PROCESSING_PAYLOAD, TERMINATED)) {
                        whenFinally.cancel();
                    }
                }

                @Override
                public void cancel() {
                    if (!discardEventsAfterCancel) {
                        if (afterFinallyBehavior) {
                            try {
                                subscription.cancel();
                            } finally {
                                tryWhenFinallyCancel();
                            }
                        } else {
                            try {
                                tryWhenFinallyCancel();
                            } finally {
                                subscription.cancel();
                            }
                        }
                        return;
                    }

                    for (;;) {
                        final int state = WhenFinallyHttpOperator.MessageBodySubscriber.this.state;
                        if (state == PROCESSING_PAYLOAD) {
                            if (tryCancelFromState(PROCESSING_PAYLOAD)) {
                                break;
                            }
                        } else if (state == DELIVERING_PAYLOAD) {
                            if (stateUpdater.compareAndSet(WhenFinallyHttpOperator.MessageBodySubscriber.this,
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
                final int state = this.state;
                if (state == TERMINATED) {
                    // We already cancelled and have to discard further events
                    return;
                }
                if (state == DELIVERING_PAYLOAD || state == AWAITING_CANCEL) {
                    reentry = true;
                    break;
                }
                if (stateUpdater.compareAndSet(this, PROCESSING_PAYLOAD, DELIVERING_PAYLOAD)) {
                    break;
                }
            }

            try {
                subscriber.onNext(o);
            } finally {
                // Re-entry -> don't unlock
                if (!reentry) {
                    for (;;) {
                        final int state = this.state;
                        assert state != PROCESSING_PAYLOAD;
                        if (state == TERMINATED) {
                            break;
                        }
                        if (state == DELIVERING_PAYLOAD) {
                            if (stateUpdater.compareAndSet(this, DELIVERING_PAYLOAD, PROCESSING_PAYLOAD)) {
                                break;
                            }
                        } else if (tryCancelFromState(AWAITING_CANCEL)) {
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!discardEventsAfterCancel) {
                if (afterFinallyBehavior) {
                    try {
                        subscriber.onError(t);
                    } finally {
                        tryWhenFinallyError(t);
                    }
                } else {
                    try {
                        tryWhenFinallyError(t);
                    } catch (Throwable cause) {
                        addSuppressed(t, cause);
                    }
                    subscriber.onError(t);
                }
                return;
            }

            final int prevState = setTerminalState();
            if (prevState == TERMINATED) {
                // We already cancelled and have to discard further events
                return;
            }
            // Propagate original cancel to let Subscription observe it
            final boolean propagateCancel = prevState == AWAITING_CANCEL;

            if (afterFinallyBehavior) {
                try {
                    subscriber.onError(t);
                } finally {
                    try {
                        whenFinally.onError(t);
                    } finally {
                        cancel0(propagateCancel);
                    }
                }
            } else {
                try {
                    whenFinally.onError(t);
                } catch (Throwable cause) {
                    addSuppressed(t, cause);
                }
                try {
                    subscriber.onError(t);
                } finally {
                    cancel0(propagateCancel);
                }
            }
        }

        @Override
        public void onComplete() {
            if (!discardEventsAfterCancel) {
                if (afterFinallyBehavior) {
                    try {
                        subscriber.onComplete();
                    } finally {
                        tryWhenFinallyComplete();
                    }
                } else {
                    try {
                        tryWhenFinallyComplete();
                    } catch (Throwable cause) {
                        subscriber.onError(cause);
                        return;
                    }
                    subscriber.onComplete();
                }
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
                if (afterFinallyBehavior) {
                    try {
                        subscriber.onComplete();
                    } finally {
                        whenFinally.onComplete();
                    }
                } else {
                    try {
                        whenFinally.onComplete();
                    } catch (Throwable cause) {
                        subscriber.onError(cause);
                        return;
                    }
                    subscriber.onComplete();
                }
            } finally {
                cancel0(propagateCancel);
            }
        }

        private boolean tryCancelFromState(final int expectedState) {
            if (stateUpdater.compareAndSet(WhenFinallyHttpOperator.MessageBodySubscriber.this,
                    expectedState, TERMINATED)) {
                assert subscription != null;
                if (afterFinallyBehavior) {
                    try {
                        subscription.cancel();
                    } finally {
                        whenFinally.cancel();
                    }
                } else {
                    try {
                        whenFinally.cancel();
                    } finally {
                        subscription.cancel();
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        private void tryWhenFinallyError(Throwable t) {
            if (stateUpdater.compareAndSet(this, PROCESSING_PAYLOAD, TERMINATED)) {
                whenFinally.onError(t);
            }
        }

        private void tryWhenFinallyComplete() {
            if (stateUpdater.compareAndSet(this, PROCESSING_PAYLOAD, TERMINATED)) {
                whenFinally.onComplete();
            }
        }

        private int setTerminalState() {
            return stateUpdater.getAndSet(this, TERMINATED);
        }

        private void cancel0(final boolean propagateCancel) {
            if (propagateCancel) {
                assert subscription != null;
                subscription.cancel();
            }
        }
    }
}
