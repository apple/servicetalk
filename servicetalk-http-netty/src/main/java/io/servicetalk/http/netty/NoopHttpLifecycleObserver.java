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

import io.servicetalk.http.api.HttpLifecycleObserver;

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
    }

    static final class NoopHttpRequestObserver implements HttpRequestObserver {

        static final HttpRequestObserver INSTANCE = new NoopHttpRequestObserver();

        private NoopHttpRequestObserver() {
            // Singleton
        }
    }

    static final class NoopHttpResponseObserver implements HttpResponseObserver {

        static final HttpResponseObserver INSTANCE = new NoopHttpResponseObserver();

        private NoopHttpResponseObserver() {
            // Singleton
        }
    }
}
