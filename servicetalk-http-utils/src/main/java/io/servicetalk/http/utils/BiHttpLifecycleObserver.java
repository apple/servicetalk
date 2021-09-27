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
import io.servicetalk.transport.api.ConnectionInfo;

import static java.util.Objects.requireNonNull;

final class BiHttpLifecycleObserver implements HttpLifecycleObserver {

    private final HttpLifecycleObserver first;
    private final HttpLifecycleObserver second;

    BiHttpLifecycleObserver(final HttpLifecycleObserver first, final HttpLifecycleObserver second) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
    }

    @Override
    public HttpExchangeObserver onNewExchange() {
        final HttpExchangeObserver f;
        final HttpExchangeObserver s;
        try {
            f = first.onNewExchange();
        } finally {
            s = second.onNewExchange();
        }
        return new BiHttpExchangeObserver(f, s);
    }

    private static final class BiHttpExchangeObserver implements HttpExchangeObserver {

        private final HttpExchangeObserver first;
        private final HttpExchangeObserver second;

        BiHttpExchangeObserver(final HttpExchangeObserver first, final HttpExchangeObserver second) {
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
        public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            final HttpRequestObserver f;
            final HttpRequestObserver s;
            try {
                f = first.onRequest(requestMetaData);
            } finally {
                s = second.onRequest(requestMetaData);
            }
            return new BiHttpRequestObserver(f, s);
        }

        @Override
        public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            final HttpResponseObserver f;
            final HttpResponseObserver s;
            try {
                f = first.onResponse(responseMetaData);
            } finally {
                s = second.onResponse(responseMetaData);
            }
            return new BiHttpResponseObserver(f, s);
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

    private static final class BiHttpRequestObserver implements HttpRequestObserver {

        private final HttpRequestObserver first;
        private final HttpRequestObserver second;

        BiHttpRequestObserver(final HttpRequestObserver first, final HttpRequestObserver second) {
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

    private static final class BiHttpResponseObserver implements HttpResponseObserver {

        private final HttpResponseObserver first;
        private final HttpResponseObserver second;

        private BiHttpResponseObserver(final HttpResponseObserver first, final HttpResponseObserver second) {
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
            try {
                first.onResponseCancel();
            } finally {
                second.onResponseCancel();
            }
        }
    }
}
