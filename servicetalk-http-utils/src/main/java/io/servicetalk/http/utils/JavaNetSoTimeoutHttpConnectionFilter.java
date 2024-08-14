/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.ThrowableUtils;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpContextKeys;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaderValues;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.AbstractTimeoutHttpFilter.FixedDuration;
import io.servicetalk.transport.api.ExecutionContext;

import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;

/**
 * A filter that mimics {@link SocketOptions#SO_TIMEOUT} behavior on the client-side.
 * <p>
 * While {@link TimeoutHttpRequesterFilter} applies a timeout for the overall duration to receive either the response
 * metadata (headers) or the complete reception of the response (including headers, payload body, optional trailers, as
 * well as time to send the request), this filter applies timeout to every independent read operation: read of 100
 * (Continue) response, read of response headers, read of every response payload body chunk, read of optional trailers.
 * Note that the <b>time to send the request is not accounted</b>. Also, if the remote server is sending a large payload
 * body, the timeout will be applied on every chunk read, which may result in unpredictable time to read the full
 * response if the remote slowly sends 1 byte within the timeout boundaries. Use this filter only for compatibility with
 * classic blocking Java libraries. To protect from the described use-cases, consider also appending
 * {@link TimeoutHttpRequesterFilter} before applying this filter in the filter chain.
 * <p>
 * This filter implements only {@link StreamingHttpConnectionFilterFactory} and therefore can be applied only at the
 * connection level. This restriction ensures that the timeout is applied only for the response read operations
 * (similar to {@link SocketOptions#SO_TIMEOUT} used by Java blocking API), without waiting for selecting or
 * establishing a connection.
 * <p>
 * {@link SocketTimeoutException} (or its subtype) will be propagated when the timeout is reached.
 *
 * @see TimeoutHttpRequesterFilter
 * @see SocketOptions#SO_TIMEOUT
 * @see java.net.Socket#setSoTimeout(int)
 */
public final class JavaNetSoTimeoutHttpConnectionFilter implements StreamingHttpConnectionFilterFactory {

    private final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest;
    @Nullable
    private final Executor timeoutExecutor;

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}, must be {@code > 0}
     */
    public JavaNetSoTimeoutHttpConnectionFilter(final Duration duration) {
        this(new FixedDuration(duration));
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}, must be {@code > 0}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public JavaNetSoTimeoutHttpConnectionFilter(final Duration duration, final Executor timeoutExecutor) {
        this(new FixedDuration(duration), timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout value from a request or other runtime sources.
     * A timeout of {@code null} or {@link Duration#ZERO zero (0)} is interpreted as an infinite timeout, all other
     * values must be {@code > 0}
     */
    public JavaNetSoTimeoutHttpConnectionFilter(
            final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest);
        this.timeoutExecutor = null;
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout value from a request or other runtime sources.
     * A timeout of {@code null} or {@link Duration#ZERO zero (0)} is interpreted as an infinite timeout, all other
     * values must be {@code > 0}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public JavaNetSoTimeoutHttpConnectionFilter(
            final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
            final Executor timeoutExecutor) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.defer(() -> {
                    final Executor timeoutExecutor = contextExecutor(request, executionContext());
                    @Nullable
                    final Duration timeout = timeoutForRequest.apply(request, timeoutExecutor);
                    if (timeout == null || timeout.isZero()) {
                        return delegate().request(request).shareContextOnSubscribe();
                    }

                    if (timeout.isNegative()) {
                        return Single.<StreamingHttpResponse>failed(
                                        new IllegalArgumentException("timeout: " + timeout + " (expected > 0)"))
                                .shareContextOnSubscribe();
                    }

                    final CompletableSource.Processor requestProcessor = Processors.newCompletableProcessor();
                    final Cancellable continueTimeout;
                    final boolean expectContinue = request.headers()
                            .contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
                    if (expectContinue) {
                        // For "Expect: 100-continue" case we start the timer when we send request meta-data and cancel
                        // it when transport subscribes to the request payload publisher, which indicates that we
                        // received 100 (Continue) response from the server.
                        continueTimeout = timeoutExecutor.schedule(() ->
                                requestProcessor.onError(newStacklessSocketTimeoutException("Read timed out after " +
                                        timeout.toMillis() + "ms waiting for 100 (Continue) response")), timeout);
                    } else {
                        continueTimeout = null;
                    }
                    return delegate().request(request.transformMessageBody(p -> {
                                // Signal when request is fully written to start "read timeout"
                                Publisher<?> body = p.beforeFinally(requestProcessor::onComplete);
                                if (continueTimeout != null) {
                                    // Subscribe to the request payload body indicates we received 100 (Continue)
                                    return body.beforeOnSubscribe(__ -> continueTimeout.cancel());
                                }
                                return body;
                            }))
                            // Defer timeout counter until after the request payload body is complete
                            .<StreamingHttpResponse>liftSync(subscriber ->
                                    new RequestTimeoutSubscriber(subscriber,
                                            SourceAdapters.fromSource(requestProcessor), timeout, timeoutExecutor))
                            .shareContextOnSubscribe();
                });
            }
        };
    }

    private static final class RequestTimeoutSubscriber implements Subscriber<StreamingHttpResponse> {

        private static final AtomicIntegerFieldUpdater<RequestTimeoutSubscriber> onceUpdater =
                AtomicIntegerFieldUpdater.newUpdater(RequestTimeoutSubscriber.class, "once");

        private final DelayedCancellable requestCancellable = new DelayedCancellable();
        private final DelayedCancellable timeoutCancellable = new DelayedCancellable();
        private final Subscriber<? super StreamingHttpResponse> delegate;

        private final Completable requestComplete;
        private final Duration timeout;
        private final Executor timeoutExecutor;
        @SuppressWarnings("unused")
        private volatile int once;

        RequestTimeoutSubscriber(Subscriber<? super StreamingHttpResponse> delegate, Completable requestComplete,
                                        Duration timeout, Executor timeoutExecutor) {
            this.delegate = delegate;
            this.requestComplete = requestComplete;
            this.timeout = timeout;
            this.timeoutExecutor = timeoutExecutor;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            delegate.onSubscribe(() -> {
                // We don't need to condition cancellation here on the `once()` call because the `DelayedCancellable`
                // will already enforce idempotency of the cancel call, and it's fine if we're racing cancels with the
                // results since cleanup is gated on the `once()` call.
                once();
                timeoutCancellable.cancel();
                requestCancellable.cancel();
            });
            requestCancellable.delayedCancellable(cancellable);
            timeoutCancellable.delayedCancellable(requestComplete.concat(Completable.never()
                    .timeout(timeout, timeoutExecutor)).beforeOnError(this::handleInterruptions).subscribe());
        }

        private void handleInterruptions(Throwable t) {
            if (once()) {
                requestCancellable.cancel();
                Throwable result = t;
                // We can get a SocketTimeoutException waiting for a 100 Continue response.
                if (t instanceof TimeoutException) {
                result = newStacklessSocketTimeoutException(
                        "Read timed out after " + timeout.toMillis() +
                                "ms waiting for response meta-data")
                        .initCause(t);
                }
                delegate.onError(result);
            }
        }

        @Override
        public void onSuccess(@Nullable StreamingHttpResponse result) {
            if (once()) {
                timeoutCancellable.cancel();
                if (result != null) {
                    result = result.transformMessageBody(p -> p.timeout(timeout, timeoutExecutor)
                            .onErrorMap(TimeoutException.class, t -> newStacklessSocketTimeoutException(
                                    "Read timed out after " + timeout.toMillis() +
                                            "ms waiting for the next response payload body chunk")
                                    .initCause(t)));
                }
                delegate.onSuccess(result);
            } else {
                if (result != null) {
                    toSource(result.messageBody()).subscribe(CancelImmediatelySubscriber.INSTANCE);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (once()) {
                timeoutCancellable.cancel();
                delegate.onError(t);
            }
        }

        private boolean once() {
            return 0 == onceUpdater.getAndSet(this, 1);
        }
    }

    private Executor contextExecutor(final HttpRequestMetaData requestMetaData,
            final ExecutionContext<HttpExecutionStrategy> context) {
        if (timeoutExecutor != null) {
            return timeoutExecutor;
        }
        // We have to consider the execution strategy associated with the request.
        final HttpExecutionStrategy strategy = requestMetaData.context()
                .getOrDefault(HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY, context.executionStrategy());
        assert strategy != null;
        return strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
               context.executor() : context.ioExecutor();
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    private static StacklessSocketTimeoutException newStacklessSocketTimeoutException(final String message) {
        return StacklessSocketTimeoutException.newInstance(message, JavaNetSoTimeoutHttpConnectionFilter.class,
                "request");
    }

    private static final class StacklessSocketTimeoutException extends SocketTimeoutException {
        private static final long serialVersionUID = -6407427631101487627L;

        private StacklessSocketTimeoutException(String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            // Don't fill in the stacktrace to reduce performance overhead
            return this;
        }

        static StacklessSocketTimeoutException newInstance(final String message, final Class<?> clazz,
                                                           final String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessSocketTimeoutException(message), clazz, method);
        }
    }
}
