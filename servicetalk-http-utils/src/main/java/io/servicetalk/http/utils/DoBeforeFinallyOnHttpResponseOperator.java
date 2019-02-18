/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

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
 *     return requester.request(strategy, request)
 *                     .doBeforeSubscribe(__ -> tracker.requestStarted())
 *                     .liftSynchronous(new DoBeforeFinallyOnHttpResponseOperator(tracker::requestFinished));
 * }</pre>
 */
public final class DoBeforeFinallyOnHttpResponseOperator
        implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {
    private final Runnable doBeforeFinally;

    /**
     * Create a new instance.
     *
     * @param doBeforeFinally the callback that is executed just once whenever the sources reach a terminal state across
     * both sources
     */
    public DoBeforeFinallyOnHttpResponseOperator(final Runnable doBeforeFinally) {
        this.doBeforeFinally = requireNonNull(doBeforeFinally);
    }

    @Override
    public Subscriber<? super StreamingHttpResponse> apply(
            final Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling doBeforeFinally.run() when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(subscriber, doBeforeFinally);
    }

    private static final class ResponseCompletionSubscriber implements Subscriber<StreamingHttpResponse> {
        private static final int IDLE = 0;
        private static final int CANCELLED = 1;
        private static final int TERMINATED = 2;
        private static final AtomicIntegerFieldUpdater<ResponseCompletionSubscriber> stateUpdater =
                newUpdater(ResponseCompletionSubscriber.class, "state");

        private final Subscriber<? super StreamingHttpResponse> subscriber;
        private final Runnable doBeforeFinally;
        @SuppressWarnings("unused")
        private volatile int state;

        ResponseCompletionSubscriber(final Subscriber<? super StreamingHttpResponse> sub,
                                     final Runnable doBeforeFinally) {
            this.subscriber = sub;
            this.doBeforeFinally = doBeforeFinally;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                try {
                    if (stateUpdater.compareAndSet(this, IDLE, CANCELLED)) {
                        doBeforeFinally.run();
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
            } else if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                subscriber.onSuccess(response.transformRawPayloadBody(payload ->
                        payload.doBeforeFinally(doBeforeFinally)));
            } else {
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == CANCELLED;
                subscriber.onSuccess(response.transformRawPayloadBody(payload -> {
                    // We have been cancelled. Subscribe and cancel the content so that we do not hold up the
                    // connection and indicate that there is no one else that will subscribe.
                    payload.subscribe(CancelImmediatelySubscriber.INSTANCE);
                    return Publisher.error(new CancellationException("Received response post cancel."));
                }));
            }
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    doBeforeFinally.run();
                }
            } finally {
                subscriber.onError(t);
            }
        }

        private void sendNullResponse() {
            try {
                // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED)) {
                    doBeforeFinally.run();
                }
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onSuccess(null);
        }
    }

    private static final class CancelImmediatelySubscriber implements org.reactivestreams.Subscriber<Object> {
        private static final Logger LOGGER = LoggerFactory.getLogger(CancelImmediatelySubscriber.class);
        static final CancelImmediatelySubscriber INSTANCE = new CancelImmediatelySubscriber();

        private CancelImmediatelySubscriber() {
            // Singleton
        }

        @Override
        public void onSubscribe(final Subscription s) {
            // Cancel immediately so that the connection can handle this as required.
            s.cancel();
        }

        @Override
        public void onNext(final Object obj) {
            // Can not be here since we never request.
        }

        @Override
        public void onError(final Throwable t) {
            LOGGER.debug("Ignoring error from response payload, since subscriber has already cancelled.", t);
        }

        @Override
        public void onComplete() {
            // Ignore.
        }
    }
}
