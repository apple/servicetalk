/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

import java.util.Queue;
import javax.annotation.Nullable;

final class TestHttpLifecycleObserver implements HttpLifecycleObserver {

    static final AttributeKey<String> ON_NEW_EXCHANGE_KEY = AttributeKey.stringKey("onNewExchange");
    static final AttributeKey<String> ON_EXCHANGE_FINALLY_KEY = AttributeKey.stringKey("onExchangeFinally");

    static final AttributeKey<String> ON_REQUEST_KEY = AttributeKey.stringKey("onRequest");
    static final AttributeKey<String> ON_REQUEST_DATA_KEY = AttributeKey.stringKey("onRequestData");
    static final AttributeKey<String> ON_REQUEST_TRAILERS_KEY = AttributeKey.stringKey("onRequestTrailers");
    static final AttributeKey<String> ON_REQUEST_COMPLETE_KEY = AttributeKey.stringKey("onRequestComplete");
    static final AttributeKey<String> ON_REQUEST_ERROR_KEY = AttributeKey.stringKey("onRequestError");
    static final AttributeKey<String> ON_REQUEST_CANCEL_KEY = AttributeKey.stringKey("onRequestCancel");

    static final AttributeKey<String> ON_RESPONSE_KEY = AttributeKey.stringKey("onResponse");
    static final AttributeKey<String> ON_RESPONSE_ERROR_KEY = AttributeKey.stringKey("onResponseError");
    static final AttributeKey<String> ON_RESPONSE_CANCEL_KEY = AttributeKey.stringKey("onResponseCancel");
    static final AttributeKey<String> ON_RESPONSE_DATA_KEY = AttributeKey.stringKey("onResponseData");
    static final AttributeKey<String> ON_RESPONSE_TRAILERS_KEY = AttributeKey.stringKey("onResponseTrailers");
    static final AttributeKey<String> ON_RESPONSE_COMPLETE_KEY = AttributeKey.stringKey("onResponseComplete");
    static final AttributeKey<String> ON_RESPONSE_BODY_ERROR_KEY = AttributeKey.stringKey("onResponseBodyError");
    static final AttributeKey<String> ON_RESPONSE_BODY_CANCEL_KEY = AttributeKey.stringKey("onResponseBodyCancel");

    private final Queue<Error> errorQueue;

    // Protected by synchronization
    @Nullable
    private Span initialSpan;

    TestHttpLifecycleObserver(Queue<Error> errorQueue) {
        this.errorQueue = errorQueue;
    }

    @Override
    public HttpExchangeObserver onNewExchange() {
        setKey(ON_NEW_EXCHANGE_KEY);
        return new HttpExchangeObserver() {

            @Override
            public HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                setKey(ON_REQUEST_KEY);
                return new HttpRequestObserver() {
                    @Override
                    public void onRequestData(Buffer data) {
                        setKey(ON_REQUEST_DATA_KEY);
                    }

                    @Override
                    public void onRequestTrailers(HttpHeaders trailers) {
                        setKey(ON_REQUEST_TRAILERS_KEY);
                    }

                    @Override
                    public void onRequestComplete() {
                        setKey(ON_REQUEST_COMPLETE_KEY);
                    }

                    @Override
                    public void onRequestError(Throwable cause) {
                        setKey(ON_REQUEST_ERROR_KEY);
                    }

                    @Override
                    public void onRequestCancel() {
                        setKey(ON_REQUEST_CANCEL_KEY);
                    }
                };
            }

            @Override
            public HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
                setKey(ON_RESPONSE_KEY);
                return new HttpResponseObserver() {
                    @Override
                    public void onResponseData(Buffer data) {
                        setKey(ON_RESPONSE_DATA_KEY);
                    }

                    @Override
                    public void onResponseTrailers(HttpHeaders trailers) {
                        setKey(ON_RESPONSE_TRAILERS_KEY);
                    }

                    @Override
                    public void onResponseComplete() {
                        setKey(ON_RESPONSE_COMPLETE_KEY);
                    }

                    @Override
                    public void onResponseError(Throwable cause) {
                        setKey(ON_RESPONSE_BODY_ERROR_KEY);
                    }

                    @Override
                    public void onResponseCancel() {
                        setKey(ON_RESPONSE_BODY_CANCEL_KEY);
                    }
                };
            }

            @Override
            public void onResponseError(Throwable cause) {
                setKey(ON_RESPONSE_ERROR_KEY);
            }

            @Override
            public void onResponseCancel() {
                setKey(ON_RESPONSE_CANCEL_KEY);
            }

            @Override
            public void onExchangeFinally() {
                setKey(ON_EXCHANGE_FINALLY_KEY);
            }
        };
    }

    private void setKey(AttributeKey<String> key) {
        final Span current = Span.current();
        current.setAttribute(key, "set");
        final Span initialSpan;
        synchronized (this) {
            if (this.initialSpan == null) {
                this.initialSpan = current;
            }
            initialSpan = this.initialSpan;
        }
        if (Span.getInvalid().equals(current)) {
            errorQueue.offer(new AssertionError("Detected the invalid span"));
        } else if (!current.equals(initialSpan)) {
            errorQueue.offer(new AssertionError("Found unexpected unrelated span detected in " +
                    key.getKey() + ". Initial: " + initialSpan + ", current: " + current));
        }
    }
}
