/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpExchangeObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpRequestObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpResponseObserver;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;
import io.servicetalk.http.netty.NoopHttpLifecycleObserver.NoopHttpExchangeObserver;
import io.servicetalk.http.netty.NoopHttpLifecycleObserver.NoopHttpRequestObserver;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.transport.api.ConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.concurrent.api.Single.defer;
import static java.util.Objects.requireNonNull;

abstract class AbstractLifecycleObserverHttpFilter implements HttpExecutionStrategyInfluencer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLifecycleObserverHttpFilter.class);
    static final Key<Consumer<ConnectionInfo>> ON_CONNECTION_SELECTED_CONSUMER =
            newKey("ON_CONNECTION_SELECTED_CONSUMER");

    private final HttpLifecycleObserver observer;

    AbstractLifecycleObserverHttpFilter(final HttpLifecycleObserver observer) {
        this.observer = requireNonNull(observer);
    }

    /**
     * Returns a {@link Single} tracking the request/response, capturing lifecycle events as they are observed.
     *
     * @param connInfo {@link ConnectionInfo} connection information.
     * @param request the {@link StreamingHttpRequest}.
     * @param responseFunction produces {@link Single}&lt;{@link StreamingHttpResponses}&gt;.
     * @return a {@link Single} tracking the request/response, capturing lifecycle events as they are observed.
     */
    final Single<StreamingHttpResponse> trackLifecycle(@Nullable final ConnectionInfo connInfo,
            final StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> responseFunction) {

        return defer(() -> {
            final HttpExchangeObserver onExchange = safeReport(observer::onNewExchange, observer, "onNewExchange",
                    NoopHttpExchangeObserver.INSTANCE);
            if (connInfo != null) {
                safeReport(onExchange::onConnectionSelected, connInfo, onExchange, "onConnectionSelected");
            } else {
                // Pass it down to LoadBalancedStreamingHttpClient
                // FIXME: switch to RequestContext when it's available
                AsyncContext.put(ON_CONNECTION_SELECTED_CONSUMER, selectedConnection -> safeReport(
                        onExchange::onConnectionSelected, selectedConnection, onExchange, "onConnectionSelected"));
            }
            final ExchangeContext exchangeContext = new ExchangeContext(onExchange);
            final HttpRequestObserver onRequest = safeReport(onExchange::onRequest, request, onExchange,
                    "onRequest", NoopHttpRequestObserver.INSTANCE);
            final StreamingHttpRequest transformed = request
                    .transformMessageBody(p -> p.beforeOnNext(item -> {
                        if (item instanceof Buffer) {
                            safeReport(onRequest::onRequestData, (Buffer) item, onRequest, "onRequestData");
                        } else if (item instanceof HttpHeaders) {
                            safeReport(onRequest::onRequestTrailers, (HttpHeaders) item, onRequest,
                                    "onRequestTrailers");
                        } else {
                            LOGGER.warn(
                                    "Programming mistake: unexpected message body item is received on the request: {}",
                                    item.getClass().getName());
                        }
                    }).beforeFinally(new TerminalSignalConsumer() {
                        @Override
                        public void onComplete() {
                            safeReport(onRequest::onRequestComplete, onRequest, "onRequestComplete");
                            exchangeContext.decrementRemaining();
                        }

                        @Override
                        public void onError(final Throwable cause) {
                            safeReport(onRequest::onRequestError, cause, onRequest, "onRequestError");
                            exchangeContext.decrementRemaining();
                        }

                        @Override
                        public void cancel() {
                            safeReport(onRequest::onRequestCancel, onRequest, "onRequestCancel");
                            exchangeContext.decrementRemaining();
                        }
                    }));
            final Single<StreamingHttpResponse> responseSingle;
            try {
                responseSingle = responseFunction.apply(transformed);
            } catch (Throwable t) {
                onExchange.onResponseError(t);
                return Single.<StreamingHttpResponse>failed(t).subscribeShareContext();
            }
            return responseSingle
                    .liftSync(new BeforeFinallyHttpOperator(exchangeContext))
                    // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                    // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                    // completion of Meta. So in order for downstream operators to get a consistent view of the data
                    // path map() needs to be applied last.
                    .map(resp -> {
                        exchangeContext.onResponse(resp);
                        return resp.transformMessageBody(p -> p.beforeOnNext(exchangeContext::onResponseBody));
                    }).subscribeShareContext();
        });
    }

    @Override
    public final HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return strategy;    // no influence since we do not block and the observer is not expected to block either
    }

    private static final class ExchangeContext implements TerminalSignalConsumer {

        private static final AtomicIntegerFieldUpdater<ExchangeContext> remainingUpdater =
                AtomicIntegerFieldUpdater.newUpdater(ExchangeContext.class, "remaining");

        private final HttpExchangeObserver onExchange;
        @Nullable
        private HttpResponseObserver onResponse;
        private volatile int remaining = 2;

        private ExchangeContext(final HttpExchangeObserver onExchange) {
            this.onExchange = onExchange;
        }

        void onResponse(HttpResponseMetaData responseMetaData) {
            this.onResponse = safeReport(onExchange::onResponse, responseMetaData, onExchange, "onResponse",
                    NoopHttpLifecycleObserver.NoopHttpResponseObserver.INSTANCE);
        }

        void onResponseBody(final Object item) {
            assert onResponse != null;
            if (item instanceof Buffer) {
                safeReport(onResponse::onResponseData, (Buffer) item, onResponse, "onResponseData");
            } else if (item instanceof HttpHeaders) {
                safeReport(onResponse::onResponseTrailers, (HttpHeaders) item, onResponse, "onResponseTrailers");
            } else {
                LOGGER.warn("Programming mistake: unexpected message body item is received on the response: {}",
                        item.getClass().getName());
            }
        }

        @Override
        public void onComplete() {
            if (onResponse != null) {
                safeReport(onResponse::onResponseComplete, onResponse, "onResponseComplete");
            }
            decrementRemaining();
        }

        @Override
        public void onError(final Throwable t) {
            if (onResponse == null) {
                safeReport(onExchange::onResponseError, t, onExchange, "onResponseError");
            } else {
                safeReport(onResponse::onResponseError, t, onResponse, "onResponseError");
            }
            decrementRemaining();
        }

        @Override
        public void cancel() {
            if (onResponse == null) {
                safeReport(onExchange::onResponseCancel, onExchange, "onResponseCancel");
            } else {
                safeReport(onResponse::onResponseCancel, onResponse, "onResponseCancel");
            }
            decrementRemaining();
        }

        void decrementRemaining() {
            if (remainingUpdater.decrementAndGet(this) == 0) {
                // Exchange completes only if both request and response terminate
                safeReport(onExchange::onExchangeFinally, onExchange, "onExchangeFinally");
            }
        }
    }

    private static <T> T safeReport(final Supplier<T> supplier, final Object observer, final String eventName,
                                    final T defaultValue) {
        try {
            return requireNonNull(supplier.get());
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a '{}' event", observer, eventName, unexpected);
            return defaultValue;
        }
    }

    private static <T, A> T safeReport(final Function<A, T> function, final A argument, final Object observer,
                                       final String eventName, final T defaultValue) {
        try {
            return requireNonNull(function.apply(argument));
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a '{}' event", observer, eventName, unexpected);
            return defaultValue;
        }
    }

    private static <T> void safeReport(final Consumer<T> consumer, final T t, final Object observer,
                                       final String eventName) {
        try {
            consumer.accept(t);
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a '{}' event", observer, eventName, unexpected);
        }
    }

    private static void safeReport(final Runnable runnable, final Object observer, final String eventName) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting a '{}' event", observer, eventName, unexpected);
        }
    }

    private static void safeReport(final Consumer<Throwable> onError, final Throwable t, final Object observer,
                                   final String eventName) {
        try {
            onError.accept(t);
        } catch (Throwable unexpected) {
            unexpected.addSuppressed(t);
            LOGGER.warn("Unexpected exception from {} while reporting a '{}' event", observer, eventName, unexpected);
        }
    }
}
