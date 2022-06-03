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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.IoExecutor;

/**
 * An observer interface that provides visibility into HTTP lifecycle events.
 * <p>
 * In order to deliver events at accurate time, callbacks on this interface can be invoked from the {@link IoExecutor}.
 * Implementation of this observer <b>must</b> be non-blocking. If the consumer of events may block (uses a blocking
 * library or <a href="https://logging.apache.org/log4j/2.x/manual/async.html">logger configuration is not async</a>),
 * it has to offload publications to another {@link Executor} <b>after</b> capturing timing of events. If blocking code
 * is executed inside callbacks without offloading, it will negatively impact {@link IoExecutor} and overall performance
 * of the application.
 * <p>
 * To install this observer for the server use {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver)}, for
 * the client use {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)} with
 * {@code io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter}.
 */
@FunctionalInterface
public interface HttpLifecycleObserver {

    /**
     * Callback when a new HTTP exchange starts.
     *
     * @return an {@link HttpExchangeObserver} that provides visibility into exchange events
     */
    HttpExchangeObserver onNewExchange();

    /**
     * An observer interface that provides visibility into events associated with a single HTTP exchange.
     * <p>
     * An exchange is represented by a {@link HttpRequestObserver request} and a {@link HttpResponseObserver response}.
     * Both can be observed independently and may publish their events concurrently because connections are full-duplex.
     * The {@link #onExchangeFinally() terminal event} for the exchange is signaled only when nested observers signal
     * terminal events. Cancellation is the best effort, more events may be signaled after cancel.
     */
    interface HttpExchangeObserver {

        /**
         * Callback when a connection is selected for this exchange execution.
         *
         * @param info {@link ConnectionInfo} of the selected connection
         */
        void onConnectionSelected(ConnectionInfo info);

        /**
         * Callback when a request starts.
         *
         * @param requestMetaData The corresponding {@link HttpRequestMetaData}
         * @return an {@link HttpRequestObserver} that provides visibility into request events
         */
        HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData);

        /**
         * Callback when a response meta-data was observed.
         *
         * @param responseMetaData the corresponding {@link HttpResponseMetaData}
         * @return an {@link HttpResponseObserver} that provides visibility into response events
         */
        HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData);

        /**
         * Callback if the response meta-data was not received due to an error.
         *
         * @param cause the cause of a response meta-data failure
         */
        void onResponseError(Throwable cause);

        /**
         * Callback if the response meta-data was cancelled.
         * <p>
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        void onResponseCancel();

        /**
         * Callback when the exchange completes.
         * <p>
         * This is the final callback that is invoked after {@link HttpRequestObserver} terminate and either
         * {@link HttpResponseObserver} terminate or {@link #onResponseError(Throwable)}/{@link #onResponseCancel()}
         * method is invoked.
         */
        void onExchangeFinally();
    }

    /**
     * An observer interface that provides visibility into events associated with a single HTTP request.
     * <p>
     * The request is considered complete when one of the terminal events is invoked. It's guaranteed only one terminal
     * event will be invoked per request.
     */
    interface HttpRequestObserver {

        /**
         * Callback when subscriber requests {@code n} items of the request payload body.
         * <p>
         * May be invoked multiple times. Helps to track when items are requested and when they are
         * {@link #onRequestData(Buffer) delivered}.
         *
         * @param n number of requested items
         */
        default void onRequestDataRequested(long n) {   // FIXME: 0.43 - consider removing default impl
        }

        /**
         * Callback when the request payload body data chunk was observed.
         * <p>
         * May be invoked multiple times if the payload body is split into multiple chunks.
         *
         * @param data the request payload body data chunk
         */
        void onRequestData(Buffer data);

        /**
         * Callback when request trailers were observed.
         *
         * @param trailers trailers of the request
         */
        void onRequestTrailers(HttpHeaders trailers);

        /**
         * Callback if the request completes successfully.
         * <p>
         * This is one of the possible terminal events.
         */
        void onRequestComplete();

        /**
         * Callback if the request fails with an error.
         * <p>
         * This is one of the possible terminal events.
         *
         * @param cause {@link Throwable} that fails this request
         */
        void onRequestError(Throwable cause);

        /**
         * Callback if the request is cancelled.
         * <p>
         * This is one of the possible terminal events.
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        void onRequestCancel();
    }

    /**
     * An observer interface that provides visibility into events associated with a single HTTP response.
     * <p>
     * The response is considered complete when one of the terminal events is invoked. It's guaranteed only one terminal
     * event will be invoked per response.
     */
    interface HttpResponseObserver {

        /**
         * Callback when subscriber requests {@code n} items of the response payload body.
         * <p>
         * May be invoked multiple times. Helps to track when items are requested and when they are
         * {@link #onResponseData delivered}.
         *
         * @param n number of requested items
         */
        void onResponseDataRequested(long n);   // FIXME: 0.43 - consider removing default impl

        /**
         * Callback when the response payload body data chunk was observed.
         * <p>
         * May be invoked multiple times if the payload body is split into multiple chunks.
         *
         * @param data the response payload body data chunk
         */
        void onResponseData(Buffer data);

        /**
         * Callback when response trailers were observed.
         *
         * @param trailers trailers of the response
         */
        void onResponseTrailers(HttpHeaders trailers);

        /**
         * Callback when the response completes successfully.
         * <p>
         * This is one of the possible terminal events.
         */
        void onResponseComplete();

        /**
         * Callback when the response fails with an error.
         * <p>
         * This is one of the possible terminal events.
         *
         * @param cause {@link Throwable} that terminated this response
         */
        void onResponseError(Throwable cause);

        /**
         * Callback when the response is cancelled.
         * <p>
         * This is one of the possible terminal events.
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        void onResponseCancel();
    }
}
