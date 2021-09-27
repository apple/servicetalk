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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcExchangeObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcRequestObserver;
import io.servicetalk.grpc.api.GrpcLifecycleObserver.GrpcResponseObserver;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.grpc.api.GrpcStatusCode.fromCodeValue;
import static java.util.Objects.requireNonNull;

final class GrpcToHttpLifecycleObserverBridge implements HttpLifecycleObserver {

    private final GrpcLifecycleObserver observer;

    GrpcToHttpLifecycleObserverBridge(final GrpcLifecycleObserver observer) {
        this.observer = requireNonNull(observer);
    }

    @Override
    public HttpExchangeObserver onNewExchange() {
        return new GrpcToHttpExchangeObserverBridge(observer.onNewExchange());
    }

    private static final class GrpcToHttpExchangeObserverBridge implements HttpExchangeObserver {

        private final GrpcExchangeObserver observer;

        GrpcToHttpExchangeObserverBridge(final GrpcExchangeObserver observer) {
            this.observer = observer;
        }

        @Override
        public void onConnectionSelected(final ConnectionInfo info) {
            observer.onConnectionSelected(info);
        }

        @Override
        public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            return new GrpcToHttpRequestObserver(observer.onRequest(requestMetaData));
        }

        @Override
        public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            return new GrpcToHttpResponseObserver(observer.onResponse(responseMetaData), responseMetaData);
        }

        @Override
        public void onResponseError(final Throwable cause) {
            observer.onResponseError(cause);
        }

        @Override
        public void onResponseCancel() {
            observer.onResponseCancel();
        }

        @Override
        public void onExchangeFinally() {
            observer.onExchangeFinally();
        }
    }

    private static final class GrpcToHttpRequestObserver implements HttpRequestObserver {

        private final GrpcRequestObserver observer;

        GrpcToHttpRequestObserver(final GrpcRequestObserver observer) {
            this.observer = observer;
        }

        @Override
        public void onRequestData(final Buffer data) {
            observer.onRequestData(data);
        }

        @Override
        public void onRequestTrailers(final HttpHeaders trailers) {
            observer.onRequestTrailers(trailers);
        }

        @Override
        public void onRequestComplete() {
            observer.onRequestComplete();
        }

        @Override
        public void onRequestError(final Throwable cause) {
            observer.onRequestError(cause);
        }

        @Override
        public void onRequestCancel() {
            observer.onRequestCancel();
        }
    }

    private static final class GrpcToHttpResponseObserver implements HttpResponseObserver {

        private static final CharSequence GRPC_STATUS_CODE_TRAILER = newAsciiString("grpc-status");
        private static final CharSequence GRPC_STATUS_MESSAGE_TRAILER = newAsciiString("grpc-message");

        private final GrpcResponseObserver observer;

        GrpcToHttpResponseObserver(final GrpcResponseObserver observer, final HttpResponseMetaData responseMetaData) {
            this.observer = observer;
            GrpcStatus grpcStatus = status(responseMetaData.headers());
            if (grpcStatus != null) {
                observer.onGrpcStatus(grpcStatus);
            }
        }

        @Override
        public void onResponseData(final Buffer data) {
            observer.onResponseData(data);
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
            observer.onResponseTrailers(trailers);
            GrpcStatus grpcStatus = status(trailers);
            if (grpcStatus != null) {
                observer.onGrpcStatus(grpcStatus);
            }
        }

        @Override
        public void onResponseComplete() {
            observer.onResponseComplete();
        }

        @Override
        public void onResponseError(final Throwable cause) {
            observer.onResponseError(cause);
        }

        @Override
        public void onResponseCancel() {
            observer.onResponseCancel();
        }

        @Nullable
        static GrpcStatus status(@Nullable final HttpHeaders headers) {
            if (headers == null) {
                return null;
            }
            final CharSequence statusStr = headers.get(GRPC_STATUS_CODE_TRAILER);
            if (statusStr == null) {
                return null;
            }
            return new GrpcStatus(fromCodeValue(statusStr), null, headers.get(GRPC_STATUS_MESSAGE_TRAILER));
        }
    }
}
