/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.time.Duration.ofNanos;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

abstract class AbstractTimeoutHttpFilter implements HttpExecutionStrategyInfluencer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTimeoutHttpFilter.class);

    /**
     * Establishes the timeout for a given request.
     */
    private final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest;

    /**
     * If {@code true} then timeout is for full request/response transaction otherwise only the response metadata must
     * complete before the timeout.
     */
    private final boolean fullRequestResponse;

    /**
     * Optional executor that will be used for scheduling and execution of timeout actions. If unspecified, the
     */
    @Nullable
    private final Executor timeoutExecutor;

    @Deprecated // FIXME: 0.43 - remove deprecated method
    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse) {
        requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.timeoutForRequest = (request, timeSource) -> timeoutForRequest.apply(request);
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    AbstractTimeoutHttpFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                              final boolean fullRequestResponse) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    @Deprecated // FIXME: 0.43 - remove deprecated method
    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse,
                              final Executor timeoutExecutor) {
        requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.timeoutForRequest = (request, timeSource) -> timeoutForRequest.apply(request);
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    AbstractTimeoutHttpFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                              final boolean fullRequestResponse,
                              final Executor timeoutExecutor) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    abstract boolean isService();

    @Override
    public final HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    /**
     * Returns the response single for the provided request which will be completed within the specified timeout or
     * generate an error with a timeout exception. The timer begins when the {@link Single} is subscribed. The timeout
     * action, if it occurs, will execute on the filter's chosen timeout executor, or if none is specified, the executor
     * from the request or connection context or, if neither are specified, the global executor.
     *
     * @param request The request requiring a response.
     * @param responseFunction Function which generates the response.
     * @param context {@link ExecutionContext} to extract fallback {@link Executor} to be used for the timeout terminal
     * signal if no specific timeout executor is defined for the filter.
     * @return response single
     */
    final Single<StreamingHttpResponse> withTimeout(final StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> responseFunction,
            final ExecutionContext<HttpExecutionStrategy> context) {
        return Single.defer(() -> {
            final Executor useForTimeout = null != this.timeoutExecutor ?
                    this.timeoutExecutor : contextExecutor(request, context);
            final Duration timeout = timeoutForRequest.apply(request, useForTimeout);
            Single<StreamingHttpResponse> response = responseFunction.apply(request);
            if (null != timeout) {
                final Single<StreamingHttpResponse> timeoutResponse = response
                        .<StreamingHttpResponse>liftSync(CleanupSubscriber::new)
                        .timeout(timeout, useForTimeout);

                if (fullRequestResponse) {
                    final long deadline = useForTimeout.currentTime(NANOSECONDS) + timeout.toNanos();
                    response = timeoutResponse.map(resp -> resp.transformMessageBody(body -> defer(() -> {
                        final Duration remaining = ofNanos(deadline - useForTimeout.currentTime(NANOSECONDS));
                        return body.timeoutTerminal(remaining, useForTimeout)
                                .onErrorMap(TimeoutException.class, t ->
                                        new MappedTimeoutException("message body timeout after " + timeout.toMillis() +
                                                "ms", t))
                                .shareContextOnSubscribe();
                    })));
                } else {
                    response = timeoutResponse;
                }
            }

            return response.shareContextOnSubscribe();
        });
    }

    private Executor contextExecutor(final HttpRequestMetaData requestMetaData,
                                     final ExecutionContext<HttpExecutionStrategy> context) {
        if (isService()) {
            return context.executionStrategy().isSendOffloaded() ? context.executor() : context.ioExecutor();
        }
        // For clients, we have to consider the strategy associated with the request.
        final HttpExecutionStrategy strategy = requestMetaData.context()
                .getOrDefault(HTTP_EXECUTION_STRATEGY_KEY, context.executionStrategy());
        assert strategy != null;
        return strategy.isMetadataReceiveOffloaded() || strategy.isDataReceiveOffloaded() ?
                context.executor() : context.ioExecutor();
    }

    // This is responsible for ensuring that we don't abandon the response resources due to the use of Single.timeout.
    // It does so by retaining a reference to the StreamingHttpResponse in case we receive a cancellation. It will
    // then attempt to drain the resource. We're protected from multiple subscribes because the TimeoutSingle will
    // short circuit also on upstream cancellation, although that is not obviously correct behavior.
    private static final class CleanupSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {

        private static final AtomicReferenceFieldUpdater<CleanupSubscriber, Object> stateUpdater =
                AtomicReferenceFieldUpdater.newUpdater(CleanupSubscriber.class, Object.class, "state");

        private static final String COMPLETE = "complete";

        private final SingleSource.Subscriber<? super StreamingHttpResponse> delegate;
        @Nullable
        private volatile Object state;

        CleanupSubscriber(final SingleSource.Subscriber<? super StreamingHttpResponse> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            delegate.onSubscribe(() -> {
                try {
                    Object current = stateUpdater.getAndSet(this, COMPLETE);
                    if (current instanceof StreamingHttpResponse) {
                        // Because of the nature of the Single.timeout(..) operator we know that if we get a cancel
                        // call the message will never be escape the `.timeout(..)` operator because it will have
                        // 'completed'. Therefore, it is our responsibility to clean up the message body. This behavior
                        // is verified by the `responseCompletesBeforeTimeoutWithFollowingCancel` test in the suite.
                        clean((StreamingHttpResponse) current);
                    }
                } finally {
                    cancellable.cancel();
                }
            });
        }

        @Override
        public void onSuccess(@Nullable StreamingHttpResponse result) {
            assert result != null;
            if (stateUpdater.compareAndSet(this, null, result)) {
                try {
                    // We win the race: forward the response.
                    delegate.onSuccess(result);
                    return;
                } catch (Throwable t) {
                    // We can't call onError since we already called onSuccess so just log and fall through to cleanup.
                    LOGGER.warn("Exception thrown by onSuccess of Subscriber {}. Draining response.", delegate, t);
                }
            }
            // We lost to cancellation or there was an exception in onSuccess, so we must dispose of the resource.
            clean(result);
        }

        @Override
        public void onError(Throwable t) {
            assert !(state instanceof StreamingHttpResponse);
            delegate.onError(t);
        }

        private void clean(StreamingHttpResponse httpResponse) {
            toSource(httpResponse.messageBody()).subscribe(CancelImmediatelySubscriber.INSTANCE);
        }
    }

    private static final class MappedTimeoutException extends TimeoutException {
        private static final long serialVersionUID = -8230476062001221272L;

        MappedTimeoutException(String message, Throwable cause) {
            super(message);
            initCause(cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            // This is a wrapping exception class that always has an original cause and does not require stack trace.
            return this;
        }
    }

    /**
     * {@link BiFunction}&lt;{@link HttpRequestMetaData}, {@link TimeSource}, {@link Duration}&gt;
     * implementation which returns the provided default duration as the timeout duration to
     * be used for any request.
     */
    static final class FixedDuration implements BiFunction<HttpRequestMetaData, TimeSource, Duration> {

        private final Duration duration;

        FixedDuration(final Duration duration) {
            this.duration = ensurePositive(duration, "duration");
        }

        @Override
        public Duration apply(final HttpRequestMetaData request, final TimeSource timeSource) {
            return duration;
        }
    }
}
