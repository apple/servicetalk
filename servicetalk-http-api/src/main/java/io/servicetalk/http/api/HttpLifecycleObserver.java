/*
 * Copyright © 2021-2023, 2025 Apple Inc. and the ServiceTalk project authors
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
 * To deliver events at accurate time, callbacks on this interface can be invoked from the {@link IoExecutor}.
 * Implementation of this observer <b>must</b> be non-blocking. If the consumer of events may block (uses a blocking
 * library or <a href="https://logging.apache.org/log4j/2.x/manual/async.html">logger configuration is not async</a>),
 * it must offload publications to another {@link Executor} <b>after</b> capturing timing of events. If blocking code
 * is executed inside callbacks without offloading, it will negatively impact {@link IoExecutor} and overall performance
 * of the application.
 * <p>
 * To install this observer for the server use
 * {@link HttpServerBuilder#lifecycleObserver(HttpLifecycleObserver) lifecycleObserver} method or
 * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)
 * appendNonOffloadingServiceFilter} with
 * {@code io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter}. For the client use either
 * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory) appendClientFilter} or
 * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)
 * appendConnectionFilter} with {@code io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter}.
 */
@FunctionalInterface
public interface HttpLifecycleObserver {

    /**
     * Callback when a new HTTP exchange starts.
     * <p>
     * Depending on the order in which the observer is applied, this callback can be invoked either for every retry
     * attempt (if an observer is added after the retrying filter) or only once per exchange.
     *
     * @return an {@link HttpExchangeObserver} that provides visibility into exchange events
     */
    HttpExchangeObserver onNewExchange();

    /**
     * An observer interface that provides visibility into events associated with a single HTTP exchange.
     * <p>
     * An exchange is represented by a {@link HttpRequestObserver request} and a {@link HttpResponseObserver response}.
     * Both can be observed independently via their corresponding observers and may publish their events concurrently
     * with each other because connections are full-duplex. The {@link #onExchangeFinally() final event} for the
     * exchange is signaled only after both nested observers terminate. It guarantees visibility of both observers
     * internal states inside the final callback.
     */
    interface HttpExchangeObserver {

        /**
         * Callback when a connection is selected for this exchange execution.
         *
         * @param info {@link ConnectionInfo} of the selected connection
         */
        default void onConnectionSelected(ConnectionInfo info) {
        }

        /**
         * Callback when a request starts.
         *
         * @param requestMetaData The corresponding {@link HttpRequestMetaData}
         * @return an {@link HttpRequestObserver} that provides visibility into request events
         */
        default HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
            return NoopHttpLifecycleObservers.NOOP_HTTP_REQUEST_OBSERVER;
        }

        /**
         * Callback when a response meta-data was observed.
         * <p>
         * Either this or {@link #onResponseError(Throwable)} callback can be invoked, but not both.
         *
         * @param responseMetaData the corresponding {@link HttpResponseMetaData}
         * @return an {@link HttpResponseObserver} that provides visibility into response events
         */
        default HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
            return NoopHttpLifecycleObservers.NOOP_HTTP_RESPONSE_OBSERVER;
        }

        /**
         * Callback if the response meta-data was not received due to an error.
         * <p>
         * Either this or {@link #onResponse(HttpResponseMetaData)} callback can be invoked, but not both.
         *
         *
         * @param cause the cause of a response meta-data failure
         */
        default void onResponseError(Throwable cause) {
        }

        /**
         * Callback if the response meta-data was cancelled.
         * <p>
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        default void onResponseCancel() {
        }

        /**
         * Callback when the exchange completes.
         * <p>
         * This is the final callback that is invoked after {@link HttpRequestObserver} terminates and either
         * {@link HttpResponseObserver} terminates or {@link #onResponseError(Throwable)}/{@link #onResponseCancel()}
         * method is invoked. Inside this callback, users can safely consume the internal state of all other callbacks
         * without the need for additional synchronization.
         */
        default void onExchangeFinally() {
        }
    }

    /**
     * An observer interface that provides visibility into events associated with a single HTTP request.
     * <p>
     * The request is considered complete when one of the terminal events is invoked. It is guaranteed only one terminal
     * event will be invoked per request.
     */
    interface HttpRequestObserver {

        /**
         * Callback when the subscriber requests {@code n} items of the request payload body.
         * <p>
         * May be invoked multiple times and concurrently with other callbacks on this observer. Therefore, it should
         * have its own isolated state or should be synchronized if the state is shared with other callbacks.
         * It can help track when items are requested and when they are {@link #onRequestData(Buffer) delivered}.
         *
         * @param n number of requested items
         */
        default void onRequestDataRequested(long n) {
        }

        /**
         * Callback when a request payload body data chunk was observed.
         * <p>
         * May be invoked multiple times if the payload body is split into multiple chunks. All invocations are
         * sequential between this and other callbacks except {@link #onRequestDataRequested(long)}.
         *
         * @param data the request payload body data chunk
         */
        default void onRequestData(Buffer data) {
        }

        /**
         * Callback when request trailers were observed.
         * <p>
         * May be invoked zero times (if no trailers are present in the request) or once after all
         * {@link #onRequestData(Buffer) request data chunks} are observed.
         *
         * @param trailers trailers of the request
         */
        default void onRequestTrailers(HttpHeaders trailers) {
        }

        /**
         * Callback if the request completes successfully.
         * <p>
         * This is one of the possible terminal events.
         */
        default void onRequestComplete() {
        }

        /**
         * Callback if the request fails with an error.
         * <p>
         * This is one of the possible terminal events.
         *
         * @param cause {@link Throwable} that fails this request
         */
        default void onRequestError(Throwable cause) {
        }

        /**
         * Callback if the request is cancelled.
         * <p>
         * This is one of the possible terminal events.
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        default void onRequestCancel() {
        }
    }

    /**
     * An observer interface that provides visibility into events associated with a single HTTP response.
     * <p>
     * The response is considered complete when one of the terminal events is invoked. It is guaranteed only one
     * terminal event will be invoked per response.
     */
    interface HttpResponseObserver {

        /**
         * Callback when the subscriber requests {@code n} items of the response payload body.
         * <p>
         * May be invoked multiple times and concurrently with other callbacks on this observer. Therefore, it should
         * have its own isolated state or should be synchronized if the state is shared with other callbacks.
         * It can help track when items are requested and when they are {@link #onResponseData(Buffer) delivered}.
         *
         * @param n number of requested items
         */
        default void onResponseDataRequested(long n) {
        }

        /**
         * Callback when a response payload body data chunk was observed.
         * <p>
         * May be invoked multiple times if the payload body is split into multiple chunks. All invocations are
         * sequential between this and other callbacks except {@link #onResponseDataRequested(long)}.
         *
         * @param data the response payload body data chunk
         */
        default void onResponseData(Buffer data) {
        }

        /**
         * Callback when response trailers were observed.
         * <p>
         * May be invoked zero times (if no trailers are present in the response) or once after all
         * {@link #onResponseData(Buffer) response data chunks} are observed.
         *
         * @param trailers trailers of the response
         */
        default void onResponseTrailers(HttpHeaders trailers) {
        }

        /**
         * Callback when the response completes successfully.
         * <p>
         * This is one of the possible terminal events.
         */
        default void onResponseComplete() {
        }

        /**
         * Callback when the response fails with an error.
         * <p>
         * This is one of the possible terminal events.
         *
         * @param cause {@link Throwable} that terminated this response
         */
        default void onResponseError(Throwable cause) {
        }

        /**
         * Callback when the response is cancelled.
         * <p>
         * This is one of the possible terminal events.
         * Cancellation is the best effort, more events may be signaled after cancel.
         */
        default void onResponseCancel() {
        }
    }
}
