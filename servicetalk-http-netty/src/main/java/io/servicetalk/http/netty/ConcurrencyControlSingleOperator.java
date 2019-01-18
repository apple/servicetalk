/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class ConcurrencyControlSingleOperator
        implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrencyControlSingleOperator.class);

    private static final int IDLE = 0;
    private static final int CANCELLED = 1;
    private static final int TERMINATED = 2;

    private static final AtomicIntegerFieldUpdater<ConcurrencyControlSingleOperator> stateUpdater =
            newUpdater(ConcurrencyControlSingleOperator.class, "state");

    private final RequestConcurrencyController limiter;

    @SuppressWarnings("unused")
    private volatile int state;

    ConcurrencyControlSingleOperator(RequestConcurrencyController limiter) {
        this.limiter = requireNonNull(limiter);
    }

    @Override
    public Subscriber<? super StreamingHttpResponse> apply(
            final Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling limiter.requestFinished() when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. Doing which will mean we double decrement the concurrency
        // controller. In case, we do get an StreamingHttpResponse after we got cancel(), we subscribe to the payload
        // Publisher and cancel to indicate to the Connection that there is no other Subscriber that will use the
        // payload Publisher.
        return new ConcurrencyControlManagingSubscriber(subscriber);
    }

    private final class ConcurrencyControlManagingSubscriber implements Subscriber<StreamingHttpResponse> {

        private final Subscriber<? super StreamingHttpResponse> subscriber;

        ConcurrencyControlManagingSubscriber(final Subscriber<? super StreamingHttpResponse> sub) {
            this.subscriber = sub;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                if (stateUpdater.compareAndSet(ConcurrencyControlSingleOperator.this, IDLE, CANCELLED)) {
                    limiter.requestFinished();
                }
                // Cancel unconditionally, let the original Single handle cancel post termination, if required
                cancellable.cancel();
            });
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            if (response == null) {
                sendNullResponse();
            } else if (stateUpdater.compareAndSet(ConcurrencyControlSingleOperator.this, IDLE, TERMINATED)) {
                subscriber.onSuccess(response.transformRawPayloadBody(payload ->
                        payload.doBeforeFinally(limiter::requestFinished)));
            } else if (state == CANCELLED) {
                subscriber.onSuccess(response.transformPayloadBody(payload -> {
                    // We have been cancelled. Subscribe and cancel the content so that we do not hold up the
                    // connection and indicate that there is no one else that will subscribe.
                    payload.subscribe(CancelImmediatelySubscriber.INSTANCE);
                    return Publisher.error(new CancellationException("Received response post cancel."));
                }));
                // If state != CANCELLED then it has to be TERMINATED since we failed the CAS from IDLE -> TERMINATED.
                // Hence, we do not need to do anything special.
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (stateUpdater.compareAndSet(ConcurrencyControlSingleOperator.this, IDLE, TERMINATED)) {
                limiter.requestFinished();
            }
            subscriber.onError(t);
        }

        private void sendNullResponse() {
            // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
            limiter.requestFinished();
            subscriber.onSuccess(null);
        }
    }

    private static final class CancelImmediatelySubscriber implements org.reactivestreams.Subscriber<Buffer> {

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
        public void onNext(final Buffer buffer) {
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
