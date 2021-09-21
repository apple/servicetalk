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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;
import io.servicetalk.transport.api.ConnectionInfo;

import javax.annotation.Nullable;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;
import static io.servicetalk.utils.internal.ThrowableUtils.combine;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Logging implementation of {@link HttpLifecycleObserver}.
 */
final class LoggingHttpLifecycleObserver implements HttpLifecycleObserver {

    private final FixedLevelLogger logger;

    /**
     * Create a new instance.
     *
     * @param loggerName The name of the logger to use
     * @param logLevel The level to log at
     */
    LoggingHttpLifecycleObserver(final String loggerName, final LogLevel logLevel) {
        this.logger = newLogger(loggerName, logLevel);
    }

    @Override
    public HttpExchangeObserver onNewExchange() {
        return new LoggingHttpExchangeObserver(logger);
    }

    private static final class LoggingHttpExchangeObserver implements HttpExchangeObserver,
                                                                      HttpRequestObserver, HttpResponseObserver {

        private final long startTime = nanoTime();
        private final FixedLevelLogger logger;
        @Nullable
        private ConnectionInfo connInfo;
        @Nullable
        private HttpRequestMetaData requestMetaData;
        private long requestSize;
        private int requestTrailersCount;
        @Nullable
        private Object requestResult;
        @Nullable
        private HttpResponseMetaData responseMetaData;
        private long responseSize;
        private int responseTrailersCount;
        @Nullable
        private Object responseResult;

        private LoggingHttpExchangeObserver(final FixedLevelLogger logger) {
            this.logger = logger;
        }

        @Override
        public void onConnectionSelected(final ConnectionInfo info) {
            assert this.connInfo == null;
            this.connInfo = info;
        }

        @Override
        public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            assert this.requestMetaData == null;
            this.requestMetaData = requestMetaData;
            return this;
        }

        @Override
        public void onRequestData(final Buffer data) {
            requestSize += data.readableBytes();
        }

        @Override
        public void onRequestTrailers(final HttpHeaders trailers) {
            requestTrailersCount = trailers.size();
        }

        @Override
        public void onRequestComplete() {
            assert requestResult == null;
            assert requestMetaData != null;
            requestResult = Result.complete;
        }

        @Override
        public void onRequestError(final Throwable cause) {
            assert requestResult == null;
            requestResult = cause;
        }

        @Override
        public void onRequestCancel() {
            assert requestResult == null;
            requestResult = Result.cancelled;
        }

        @Override
        public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            assert this.responseMetaData == null;
            this.responseMetaData = responseMetaData;
            return this;
        }

        @Override
        public void onResponseData(final Buffer data) {
            responseSize += data.readableBytes();
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
            responseTrailersCount = trailers.size();
        }

        @Override
        public void onResponseComplete() {
            assert responseResult == null;
            assert responseMetaData != null;
            responseResult = Result.complete;
        }

        @Override
        public void onResponseError(final Throwable cause) {
            assert responseResult == null;
            responseResult = cause;
        }

        @Override
        public void onResponseCancel() {
            assert responseResult == null;
            responseResult = Result.cancelled;
        }

        @Override
        public void onExchangeFinally() {
            // request info always expected to be available:
            final HttpRequestMetaData requestMetaData = this.requestMetaData;
            assert requestMetaData != null;
            final HttpResponseMetaData responseMetaData = this.responseMetaData;
            Object requestResult = unwrapResult(this.requestResult);
            if (requestResult == null) {
                // It's possible that request can be cancelled before transport subscribed to its payload body
                requestResult = Result.cancelled;
            }
            if (responseMetaData != null) {
                logger.log("connection={} " +
                "request=\"{} {} {}\" requestHeadersCount={} requestSize={} requestTrailersCount={} requestResult={} " +
                "responseCode={} responseHeadersCount={} responseSize={} responseTrailersCount={} responseResult={} " +
                "duration={}ms",
                connInfo == null ? "unknown" : connInfo,
                requestMetaData.method(), requestMetaData.requestTarget(), requestMetaData.version(),
                requestMetaData.headers().size(), requestSize, requestTrailersCount, requestResult,
                responseMetaData.status().code(), responseMetaData.headers().size(), responseSize,
                    responseTrailersCount, unwrapResult(responseResult),
                NANOSECONDS.toMillis(nanoTime() - startTime), combine(responseResult, requestResult));
            } else {
                logger.log("connection={} " +
                "request=\"{} {} {}\" requestHeadersCount={} requestSize={} requestTrailersCount={} requestResult={} " +
                "responseResult={} duration={}ms",
                connInfo == null ? "unknown" : connInfo,
                requestMetaData.method(), requestMetaData.requestTarget(), requestMetaData.version(),
                requestMetaData.headers().size(), requestSize, requestTrailersCount, requestResult,
                unwrapResult(responseResult),
                NANOSECONDS.toMillis(nanoTime() - startTime), combine(responseResult, requestResult));
            }
        }

        @Nullable
        private static Object unwrapResult(@Nullable Object result) {
            return result instanceof Throwable ? Result.error : result;
        }

        private enum Result {
            complete, error, cancelled
        }
    }
}
