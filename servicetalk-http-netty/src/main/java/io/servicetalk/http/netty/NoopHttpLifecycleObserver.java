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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

final class NoopHttpLifecycleObserver implements HttpLifecycleObserver {

    static final HttpLifecycleObserver INSTANCE = new NoopHttpLifecycleObserver();

    private NoopHttpLifecycleObserver() {
        // Singleton
    }

    @Override
    public HttpExchangeObserver onNewExchange() {
        return NoopHttpExchangeObserver.INSTANCE;
    }

    static final class NoopHttpExchangeObserver implements HttpExchangeObserver {

        static final HttpExchangeObserver INSTANCE = new NoopHttpExchangeObserver();

        private NoopHttpExchangeObserver() {
            // Singleton
        }

        @Override
        public void onConnectionSelected(final ConnectionInfo info) {
        }

        @Override
        public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
            return NoopHttpRequestObserver.INSTANCE;
        }

        @Override
        public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
            return NoopHttpResponseObserver.INSTANCE;
        }

        @Override
        public void onResponseError(final Throwable cause) {
        }

        @Override
        public void onResponseCancel() {
        }

        @Override
        public void onExchangeFinally() {
        }
    }

    static final class NoopHttpRequestObserver implements HttpRequestObserver {

        static final HttpRequestObserver INSTANCE = new NoopHttpRequestObserver();

        private NoopHttpRequestObserver() {
            // Singleton
        }

        @Override
        public void onRequestDataRequested(final long n) {
        }

        @Override
        public void onRequestData(final Buffer data) {
        }

        @Override
        public void onRequestTrailers(final HttpHeaders trailers) {
        }

        @Override
        public void onRequestComplete() {
        }

        @Override
        public void onRequestError(final Throwable cause) {
        }

        @Override
        public void onRequestCancel() {
        }
    }

    static final class NoopHttpResponseObserver implements HttpResponseObserver {

        static final HttpResponseObserver INSTANCE = new NoopHttpResponseObserver();

        private NoopHttpResponseObserver() {
            // Singleton
        }

        @Override
        public void onResponseDataRequested(final long n) {
        }

        @Override
        public void onResponseData(final Buffer data) {
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
        }

        @Override
        public void onResponseComplete() {
        }

        @Override
        public void onResponseError(final Throwable cause) {
        }

        @Override
        public void onResponseCancel() {
        }
    }
}
