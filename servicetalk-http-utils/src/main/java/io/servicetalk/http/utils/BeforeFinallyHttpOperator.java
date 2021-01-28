/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

    /**
     * Create a new instance.
     *
     * @param beforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public BeforeFinallyHttpOperator(final TerminalSignalConsumer beforeFinally) {
        this.beforeFinally = requireNonNull(beforeFinally);
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

    @Override
    public SingleSource.Subscriber<? super StreamingHttpResponse> apply(
            final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling beforeFinally#onCancel when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(subscriber, beforeFinally);
    }

    private static final class ResponseCompletionSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {
        private static final int IDLE = 0;
        private static final int PROCESSING_PAYLOAD = 1;
        private static final int TERMINATED = 2;
        private static final AtomicIntegerFieldUpdater<ResponseCompletionSubscriber> stateUpdater =
                newUpdater(ResponseCompletionSubscriber.class, "state");

        private final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber;
        private final TerminalSignalConsumer beforeFinally;
        private volatile int state;

        ResponseCompletionSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> sub,
                                     final TerminalSignalConsumer beforeFinally) {
            this.subscriber = sub;
            this.beforeFinally = beforeFinally;
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
                                                    if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                            PROCESSING_PAYLOAD, TERMINATED)) {
                                                        beforeFinally.cancel();
                                                    }
                                                } finally {
                                                    subscription.cancel();
                                                }
                                            }
                                        });
                                    }

                                    @Override
                                    public void onNext(@Nullable final Object o) {
                                        subscriber.onNext(o);
                                    }

                                    @Override
                                    public void onError(final Throwable t) {
                                        try {
                                            if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                    PROCESSING_PAYLOAD, TERMINATED)) {
                                                beforeFinally.onError(t);
                                            }
                                        } finally {
                                            subscriber.onError(t);
                                        }
                                    }

                                    @Override
                                    public void onComplete() {
                                        try {
                                            if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                    PROCESSING_PAYLOAD, TERMINATED)) {
                                                beforeFinally.onComplete();
                                            }
                                        } finally {
                                            subscriber.onComplete();
                                        }
                                    }
                                })
                ));
            } else {
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == TERMINATED;
                subscriber.onSuccess(response.transformMessageBody(payload -> {
                    // We have been cancelled. Subscribe and cancel the content so that we do not hold up the
                    // connection and indicate that there is no one else that will subscribe.
                    toSource(payload).subscribe(CancelImmediatelySubscriber.INSTANCE);
                    return Publisher.failed(new CancellationException("Received response post cancel."));
                }));
            }
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    beforeFinally.onError(t);
                }
            } finally {
                subscriber.onError(t);
            }
        }

        private void sendNullResponse() {
            try {
                // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    beforeFinally.onComplete();
                }
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onSuccess(null);
        }
    }
}
