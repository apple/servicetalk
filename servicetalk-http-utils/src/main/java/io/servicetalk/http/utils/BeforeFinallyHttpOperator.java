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
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * What are the invariants that we want to support:
 * - Single terminal event is called, eg only one of onComplete(), onError(..), or cancel().
 * - if discardEventsAfterCancel is called and the `cancel()` operation 'wins', the `onError` and `onSuccess(response)`
 *   pathways will not emit an event.
 *   - Does that means we quit emitting from the message body as well?
 *     - It has its own cancel notion: does that take over once the message body has been subscribed to, and does that
 *       cancel() need honor the `discardEventsAfterCancel` flag?
 * - Support multiple subscribes to the message body
 *   - Does this mean that the first terminal event wins, or that the first subscribe gets to set the terminal event?
 *     - I'd say first subscribe 'wins' since in most real cases the second will result in a
 *       `DuplicateSubscribeException`.
 *
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
        this.beforeFinally = new OnceTerminalSignalConsumer(requireNonNull(beforeFinally));
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
                    final int previous = stateUpdater.getAndSet(this, RESPONSE_COMPLETE);
                    if ((previous == IDLE || previous == PROCESSING_PAYLOAD)) {
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
                        payload.liftSync(messageBodySubscriber ->
                                // TODO: is this legal to do here? It seems intrinsically racy in the error case but
                                //  perhaps that will always be undefined behavior.
                                // Only the first subscriber needs to be wrapped. Followup subscribers will
                                // most likely fail because duplicate subscriptions to message bodies are not allowed.
                                stateUpdater.compareAndSet(this,
                                        PROCESSING_PAYLOAD, RESPONSE_COMPLETE) ? new MessageBodySubscriber(
                                                messageBodySubscriber, beforeFinally, discardEventsAfterCancel) :
                                        messageBodySubscriber)
                ));
            } else {
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == RESPONSE_COMPLETE;
                // The request has been cancelled, but we still received a response. We need to discard the response
                // body or risk leaking hot resources which are commonly attached to a message body.
                toSource(response.messageBody()).subscribe(CancelImmediatelySubscriber.INSTANCE);
                if (!discardEventsAfterCancel) {
                    subscriber.onSuccess(response.transformMessageBody(payload ->
                            Publisher.failed(new CancellationException("Received response post cancel."))));
                }
            }
            dereferenceSubscriber();
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
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
                if (stateUpdater.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
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

    private static final class MessageBodySubscriber implements Subscriber<Object> {

        private static final int PROCESSING_PAYLOAD = 0;
        private static final int CANCELLED = 2;
        private static final int TERMINATED = -1;

        private static final AtomicIntegerFieldUpdater<MessageBodySubscriber> stateUpdater =
                newUpdater(MessageBodySubscriber.class, "state");

        private final Subscriber<? super Object> subscriber;
        private final TerminalSignalConsumer beforeFinally;
        private final boolean discardEventsAfterCancel;
        private volatile int state;

        MessageBodySubscriber(final Subscriber<? super Object> subscriber,
                              final TerminalSignalConsumer beforeFinally,
                              final boolean discardEventsAfterCancel) {
            this.subscriber = subscriber;
            this.beforeFinally = beforeFinally;
            this.discardEventsAfterCancel = discardEventsAfterCancel;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        beforeFinally.cancel();
                    } finally {
                        if (CANCELLED != stateUpdater.getAndSet(MessageBodySubscriber.this, CANCELLED)) {
                            subscription.cancel();
                        }
                    }
                }
            });
        }

        @Override
        public void onNext(@Nullable final Object o) {
            if (!discardEventsAfterCancel || state == PROCESSING_PAYLOAD) {
                subscriber.onNext(o);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (stateUpdater.compareAndSet(this, PROCESSING_PAYLOAD, TERMINATED) || !discardEventsAfterCancel) {
                beforeFinally.onError(t);
                subscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (stateUpdater.compareAndSet(this, PROCESSING_PAYLOAD, TERMINATED) || !discardEventsAfterCancel) {
                beforeFinally.onComplete();
                subscriber.onComplete();
            }
        }
    }

    private static final class OnceTerminalSignalConsumer implements TerminalSignalConsumer {

        private final TerminalSignalConsumer delegate;
        // TODO: inline.
        private final AtomicBoolean once = new AtomicBoolean();

        OnceTerminalSignalConsumer(final TerminalSignalConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onComplete() {
            if (once()) {
                delegate.onComplete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (once()) {
                delegate.onError(throwable);
            }
        }

        @Override
        public void cancel() {
            if (once()) {
                delegate.cancel();
            }
        }

        private boolean once() {
            return !once.getAndSet(true);
        }
    }
}
