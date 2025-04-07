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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Helper operator for signaling the end of an HTTP Request/Response cycle.
 *
 * <p>{@link StreamingHttpRequest} and {@link StreamingHttpResponse} are nested sources ({@link Single} of meta-data
 * containing a payload {@link Publisher}), which makes it non-trivial to get a single signal at the end of this
 * Request/Response cycle. One needs to consider and coordinate between the multitude of outcomes: cancel/success/error
 * across both sources.</p>
 * <p>This operator ensures that the provided callback is triggered just once whenever the sources reach a terminal
 * state across both sources. An important question is when the ownership of the callback is transferred from the
 * {@link Single} source and the payload {@link Publisher}. In this case ownership is transferred the first time the
 * payload is subscribed to. This means that if a cancellation of the response {@link Single} occurs after the response
 * has been emitted but before the message body has been subscribed to, the callback will observe a cancel. However, if
 * the message payload has been subscribed to, the cancellation of the {@link Single} will have no effect and the result
 * is dictated by the terminal event of the payload body. If the body is subscribed to multiple times, only the first
 * subscribe will receive ownership of the terminal events.</p>
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
final class AfterFinallyHttpOperator implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {

    private final TerminalSignalConsumer afterFinally;

    /**
     * Create a new instance.
     *
     * @param afterFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    AfterFinallyHttpOperator(final TerminalSignalConsumer afterFinally) {
        this.afterFinally = requireNonNull(afterFinally);
    }

    @Override
    public SingleSource.Subscriber<? super StreamingHttpResponse> apply(
            final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling beforeFinally#onCancel when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(subscriber, afterFinally);
    }

    private static final class ResponseCompletionSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {
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
        private final TerminalSignalConsumer afterFinally;

        ResponseCompletionSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> sub,
                                     final TerminalSignalConsumer afterFinally) {
            this.subscriber = sub;
            this.afterFinally = afterFinally;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                try {
                    // Cancel unconditionally, let the original Single handle cancel post termination, if required
                    cancellable.cancel();
                } finally {
                    afterFinally.cancel();
                }
            });
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            assert response != null;
            subscriber.onSuccess(response.transformMessageBody(payload ->
                    payload.liftSync(messageBodySubscriber ->
                            new MessageBodySubscriber(messageBodySubscriber, afterFinally))));
            dereferenceSubscriber();
        }

        @Override
        public void onError(final Throwable t) {
            try {
                subscriber.onError(t);
            } finally {
                dereferenceSubscriber();
                afterFinally.onError(t);
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

        private final Subscriber<? super Object> subscriber;
        private final TerminalSignalConsumer afterFinally;
        @Nullable
        private Subscription subscription;

        MessageBodySubscriber(final Subscriber<? super Object> subscriber,
                              final TerminalSignalConsumer afterFinally) {
            this.subscriber = subscriber;
            this.afterFinally = afterFinally;
        }

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
                    try {
                        subscription.cancel();
                    } finally {
                        afterFinally.cancel();
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
                subscriber.onError(t);
            } finally {
                afterFinally.onError(t);
            }
        }

        @Override
        public void onComplete() {
            try {
                subscriber.onComplete();
            } catch (Throwable cause) {
                subscriber.onError(cause);
            } finally {
                afterFinally.onComplete();
            }
        }
    }
}
