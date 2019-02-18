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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;
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
 *     // coarse grained callback, any terminal signal calls the provided `Runnable`
 *     return requester.request(strategy, request)
 *                     .doBeforeSubscribe(__ -> tracker.requestStarted())
 *                     .liftSynchronous(new DoBeforeOnFinallyOnHttpResponseOperator(tracker::requestFinished));
 *
 *     // fine grained callback, `tracker` implements `OnFinally`, terminal signal indicated by the `OnFinally` method
 *     return requester.request(strategy, request)
 *                     .doBeforeSubscribe(__ -> tracker.requestStarted())
 *                     .liftSynchronous(new DoBeforeOnFinallyOnHttpResponseOperator(tracker));
 * }</pre>
 */
public final class DoBeforeOnFinallyOnHttpResponseOperator
        implements SingleOperator<StreamingHttpResponse, StreamingHttpResponse> {

    private final Supplier<OnFinally> doBeforeFinallySupplier;

    /**
     * Callback interface on which only a single method is ever called matching the terminal outcome of the associated
     * {@link DoBeforeOnFinallyOnHttpResponseOperator} operator.
     */
    public interface OnFinally {

        /**
         * Signals response metadata delivered successfully and payload consumed.
         */
        default void succeeded() {
        }

        /**
         * Signals failure to deliver response metadata or payload.
         *
         * @param throwable the {@link Exception} observed while processing the request/response.
         */
        default void failed(Throwable throwable) {
        }

        /**
         * Signals the request/response was canceled, either the metadata or the payload body.
         */
        default void canceled() {
        }
    }

    private static final class RunnableOnFinally implements OnFinally {

        private final Runnable onFinally;

        private RunnableOnFinally(Runnable onFinally) {
            this.onFinally = onFinally;
        }

        @Override
        public void succeeded() {
            onFinally.run();
        }

        @Override
        public void failed(final Throwable throwable) {
            onFinally.run();
        }

        @Override
        public void canceled() {
            onFinally.run();
        }
    }

    /**
     * Create a new instance.
     *
     * @param doBeforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public DoBeforeOnFinallyOnHttpResponseOperator(final OnFinally doBeforeFinally) {
        requireNonNull(doBeforeFinally);
        this.doBeforeFinallySupplier = () -> doBeforeFinally;
    }

    /**
     * Create a new instance.
     *
     * @param doBeforeFinally the callback which is executed just once whenever the sources reach a terminal state
     * across both sources.
     */
    public DoBeforeOnFinallyOnHttpResponseOperator(final Runnable doBeforeFinally) {
        this(new RunnableOnFinally(requireNonNull(doBeforeFinally)));
    }

    @Override
    public SingleSource.Subscriber<? super StreamingHttpResponse> apply(
            final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
        // Here we avoid calling doBeforeFinally.run() when we get a cancel() on the Single after we have handed out
        // the StreamingHttpResponse to the subscriber. In case, we do get an StreamingHttpResponse after we got
        // cancel(), we subscribe to the payload Publisher and cancel to indicate to the Connection that there is no
        // other Subscriber that will use the payload Publisher.
        return new ResponseCompletionSubscriber(subscriber, doBeforeFinallySupplier.get());
    }

    private static final class ResponseCompletionSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {
        private static final int IDLE = 0;
        private static final int CANCELLED = 1;
        private static final int TERMINATED_META = 2;
        private static final int TERMINATED_PAYLOAD = 3;
        private static final AtomicIntegerFieldUpdater<ResponseCompletionSubscriber> stateUpdater =
                newUpdater(ResponseCompletionSubscriber.class, "state");

        private final SingleSource.Subscriber<? super StreamingHttpResponse> subscriber;
        private final OnFinally doBeforeFinally;
        @SuppressWarnings("unused")
        private volatile int state;

        ResponseCompletionSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> sub,
                                     final OnFinally doBeforeFinally) {
            this.subscriber = sub;
            this.doBeforeFinally = doBeforeFinally;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(() -> {
                try {
                    if (stateUpdater.compareAndSet(this, IDLE, CANCELLED)) {
                        doBeforeFinally.canceled();
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
            } else if (stateUpdater.compareAndSet(this, IDLE, TERMINATED_META)) {
                subscriber.onSuccess(response.transformRawPayloadBody(payload ->
                        payload
                                .doBeforeCancel(() -> {
                                    if (stateUpdater.compareAndSet(this, TERMINATED_META, TERMINATED_PAYLOAD)) {
                                        doBeforeFinally.canceled();
                                    }
                                })
                                .doBeforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                                    @Override
                                    public void onSubscribe(final PublisherSource.Subscription subscription) {
                                        // Ignore.
                                    }

                                    @Override
                                    public void onNext(final Object o) {
                                        // Ignore.
                                    }

                                    @Override
                                    public void onError(final Throwable throwable) {
                                        if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                TERMINATED_META, TERMINATED_PAYLOAD)) {
                                            doBeforeFinally.failed(throwable);
                                        }
                                    }

                                    @Override
                                    public void onComplete() {
                                        if (stateUpdater.compareAndSet(ResponseCompletionSubscriber.this,
                                                TERMINATED_META, TERMINATED_PAYLOAD)) {
                                            doBeforeFinally.succeeded();
                                        }
                                    }
                                })
                ));
            } else {
                // Invoking a terminal method multiple times is not allowed by the RS spec, so we assume we have been
                // cancelled.
                assert state == CANCELLED;
                subscriber.onSuccess(response.transformRawPayloadBody(payload -> {
                    // We have been cancelled. Subscribe and cancel the content so that we do not hold up the
                    // connection and indicate that there is no one else that will subscribe.
                    toSource(payload).subscribe(CancelImmediatelySubscriber.INSTANCE);
                    return Publisher.error(new CancellationException("Received response post cancel."));
                }));
            }
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED_META)) {
                    doBeforeFinally.failed(t);
                }
            } finally {
                subscriber.onError(t);
            }
        }

        private void sendNullResponse() {
            try {
                // Since, we are not giving out a response, no subscriber will arrive for the payload Publisher.
                if (stateUpdater.compareAndSet(this, IDLE, TERMINATED_PAYLOAD)) {
                    doBeforeFinally.succeeded();
                }
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onSuccess(null);
        }
    }

    private static final class CancelImmediatelySubscriber implements PublisherSource.Subscriber<Object> {
        private static final Logger LOGGER = LoggerFactory.getLogger(CancelImmediatelySubscriber.class);
        static final CancelImmediatelySubscriber INSTANCE = new CancelImmediatelySubscriber();

        private CancelImmediatelySubscriber() {
            // Singleton
        }

        @Override
        public void onSubscribe(final PublisherSource.Subscription s) {
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
