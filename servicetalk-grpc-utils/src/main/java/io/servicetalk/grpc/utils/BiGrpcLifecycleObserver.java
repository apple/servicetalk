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
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

import static java.util.Objects.requireNonNull;

final class BiGrpcLifecycleObserver implements GrpcLifecycleObserver {

    private final GrpcLifecycleObserver first;
    private final GrpcLifecycleObserver second;

    BiGrpcLifecycleObserver(final GrpcLifecycleObserver first, final GrpcLifecycleObserver second) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
    }

    @Override
    public GrpcExchangeObserver onNewExchange() {
        final GrpcExchangeObserver f;
        final GrpcExchangeObserver s;
        try {
            f = first.onNewExchange();
        } finally {
            s = second.onNewExchange();
        }
        return new BiGrpcExchangeObserver(f, s);
    }

    private static final class BiGrpcExchangeObserver implements GrpcExchangeObserver {

        private final GrpcExchangeObserver first;
        private final GrpcExchangeObserver second;

        BiGrpcExchangeObserver(final GrpcExchangeObserver first, final GrpcExchangeObserver second) {
            this.first = requireNonNull(first);
            this.second = requireNonNull(second);
        }

        @Override
        public void onConnectionSelected(final ConnectionInfo info) {
            try {
                first.onConnectionSelected(info);
            } finally {
                second.onConnectionSelected(info);
            }
        }

        @Override
        public GrpcRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            final GrpcRequestObserver f;
            final GrpcRequestObserver s;
            try {
                f = first.onRequest(requestMetaData);
            } finally {
                s = second.onRequest(requestMetaData);
            }
            return new BiGrpcRequestObserver(f, s);
        }

        @Override
        public GrpcResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            final GrpcResponseObserver f;
            final GrpcResponseObserver s;
            try {
                f = first.onResponse(responseMetaData);
            } finally {
                s = second.onResponse(responseMetaData);
            }
            return new BiGrpcResponseObserver(f, s);
        }

        @Override
        public void onResponseError(final Throwable cause) {
            try {
                first.onResponseError(cause);
            } finally {
                second.onResponseError(cause);
            }
        }

        @Override
        public void onResponseCancel() {
            try {
                first.onResponseCancel();
            } finally {
                second.onResponseCancel();
            }
        }

        @Override
        public void onExchangeFinally() {
            try {
                first.onExchangeFinally();
            } finally {
                second.onExchangeFinally();
            }
        }
    }

    private static final class BiGrpcRequestObserver implements GrpcRequestObserver {

        private final GrpcRequestObserver first;
        private final GrpcRequestObserver second;

        BiGrpcRequestObserver(final GrpcRequestObserver first, final GrpcRequestObserver second) {
            this.first = requireNonNull(first);
            this.second = requireNonNull(second);
        }

        @Override
        public void onRequestData(final Buffer data) {
            try {
                first.onRequestData(data);
            } finally {
                second.onRequestData(data);
            }
        }

        @Override
        public void onRequestTrailers(final HttpHeaders trailers) {
            try {
                first.onRequestTrailers(trailers);
            } finally {
                second.onRequestTrailers(trailers);
            }
        }

        @Override
        public void onRequestComplete() {
            try {
                first.onRequestComplete();
            } finally {
                second.onRequestComplete();
            }
        }

        @Override
        public void onRequestError(final Throwable cause) {
            try {
                first.onRequestError(cause);
            } finally {
                second.onRequestError(cause);
            }
        }

        @Override
        public void onRequestCancel() {
            try {
                first.onRequestCancel();
            } finally {
                second.onRequestCancel();
            }
        }
    }

    private static final class BiGrpcResponseObserver implements GrpcResponseObserver {

        private final GrpcResponseObserver first;
        private final GrpcResponseObserver second;

        private BiGrpcResponseObserver(final GrpcResponseObserver first, final GrpcResponseObserver second) {
            this.first = requireNonNull(first);
            this.second = requireNonNull(second);
        }

        @Override
        public void onResponseData(final Buffer data) {
            try {
                first.onResponseData(data);
            } finally {
                second.onResponseData(data);
            }
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
            try {
                first.onResponseTrailers(trailers);
            } finally {
                second.onResponseTrailers(trailers);
            }
        }

        @Override
        public void onGrpcStatus(final GrpcStatus status) {
            try {
                first.onGrpcStatus(status);
            } finally {
                second.onGrpcStatus(status);
            }
        }

        @Override
        public void onResponseComplete() {
            try {
                first.onResponseComplete();
            } finally {
                second.onResponseComplete();
            }
        }

        @Override
        public void onResponseError(final Throwable cause) {
            try {
                first.onResponseError(cause);
            } finally {
                second.onResponseError(cause);
            }
        }

        @Override
        public void onResponseCancel() {
            if (Thread.currentThread().getName().startsWith("server-")) {
                Thread.dumpStack();
            }
            try {
                first.onResponseCancel();
            } finally {
                second.onResponseCancel();
            }
        }
    }
}
