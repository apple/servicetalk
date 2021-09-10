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
package io.servicetalk.grpc.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;
import io.servicetalk.transport.api.ConnectionInfo;

import javax.annotation.Nullable;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Logging implementation of {@link GrpcLifecycleObserver}.
 */
public final class LoggingGrpcLifecycleObserver implements GrpcLifecycleObserver {

    private final FixedLevelLogger logger;

    public LoggingGrpcLifecycleObserver(final String loggerName, final LogLevel logLevel) {
        this.logger = newLogger(loggerName, logLevel);
    }

    @Override
    public GrpcExchangeObserver onNewExchange() {
        return new LoggingGrpcExchangeObserver(logger);
    }

    private static final class LoggingGrpcExchangeObserver implements GrpcExchangeObserver,
                                                                      GrpcRequestObserver, GrpcResponseObserver {

        private final long startTime = nanoTime();
        private final FixedLevelLogger logger;
        @Nullable
        private ConnectionInfo connInfo;
        @Nullable
        private HttpRequestMethod requestMethod;
        @Nullable
        private String requestTarget;
        @Nullable
        private HttpProtocolVersion requestVersion;
        private long requestSize;
        @Nullable
        private Object requestResult;
        @Nullable
        private HttpResponseStatus responseStatus;
        @Nullable
        private GrpcStatus grpcStatus;
        private long responseSize;
        @Nullable
        private Object responseResult;

        private LoggingGrpcExchangeObserver(final FixedLevelLogger logger) {
            this.logger = logger;
        }

        @Override
        public void onConnectionSelected(final ConnectionInfo info) {
            assert this.connInfo == null;
            this.connInfo = info;
        }

        @Override
        public GrpcRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            assert this.requestMethod == null;
            assert this.requestTarget == null;
            assert this.requestVersion == null;
            this.requestMethod = requestMetaData.method();
            this.requestTarget = requestMetaData.requestTarget();
            this.requestVersion = requestMetaData.version();
            return this;
        }

        @Override
        public void onRequestData(final Buffer data) {
            requestSize += data.readableBytes();
        }

        @Override
        public void onRequestTrailers(final HttpHeaders trailers) {
            // ignore for this implementation
        }

        @Override
        public void onRequestComplete() {
            assert requestResult == null;
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
        public GrpcResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            assert this.responseStatus == null;
            this.responseStatus = responseMetaData.status();
            return this;
        }

        @Override
        public void onGrpcStatus(final GrpcStatus status) {
            assert this.grpcStatus == null;
            this.grpcStatus = status;
        }

        @Override
        public void onResponseData(final Buffer data) {
            responseSize += data.readableBytes();
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
            // ignore for this implementation
        }

        @Override
        public void onResponseComplete() {
            assert responseResult == null;
            assert responseStatus != null;
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
            assert requestMethod != null;
            assert requestTarget != null;
            assert requestVersion != null;
            final HttpResponseStatus responseStatus = this.responseStatus;
            if (responseStatus != null) {
                logger.log("connection={} request=\"{} {} {}\" requestSize={} requestResult={} " +
                                "responseCode={} responseSize={} grpcStatus={} responseResult={} duration={}ms",
                        connInfo == null ? "unknown" : connInfo,
                        requestMethod, requestTarget, requestVersion, requestSize, unwrapResult(requestResult),
                        responseStatus.code(), responseSize, grpcStatus == null ? "null" : grpcStatus.code(),
                        unwrapResult(responseResult),
                        NANOSECONDS.toMillis(nanoTime() - startTime), merge(responseResult, requestResult));
            } else {
                logger.log("connection={} request=\"{} {} {}\" requestSize={} requestResult={} responseResult={} " +
                                "duration={}ms",
                        connInfo == null ? "unknown" : connInfo,
                        requestMethod, requestTarget, requestVersion, requestSize, unwrapResult(requestResult),
                        unwrapResult(responseResult),
                        NANOSECONDS.toMillis(nanoTime() - startTime), merge(responseResult, requestResult));
            }
        }

        @Nullable
        private static Throwable merge(@Nullable final Object response, @Nullable final Object request) {
            if (response instanceof Throwable) {
                if (request instanceof Throwable) {
                    final Throwable result = (Throwable) response;
                    result.addSuppressed((Throwable) request);
                    return result;
                } else {
                    return (Throwable) response;
                }
            } else if (request instanceof Throwable) {
                return (Throwable) request;
            } else {
                return null;
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
