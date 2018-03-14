/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link HttpConnection} instances and distribute calls to {@link #request(HttpRequest)} amongst this collection.
 * @param <I> The type of content of the request.
 * @param <O> The type of content of the response.
 */
public interface HttpClient<I, O> extends HttpRequester<I, O> {
    /**
     * Reserve a {@link HttpConnection} for handling the provided {@link HttpRequest}
     * but <b>does not execute it</b>!
     * @param request Allows the underlying layers to know what {@link HttpConnection}s are valid to reserve. For example
     *                this may provide some insight into shard or other info.
     * @return a {@link ReservedHttpConnection}.
     */
    Single<ReservedHttpConnection<I, O>> reserveConnection(HttpRequest<I> request);

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link HttpConnection} associated with the {@link UpgradableHttpResponse} will be reserved for exclusive use.
     * The code responsible for determining the result of the upgrade attempt is responsible for calling
     * {@link UpgradableHttpResponse#getHttpConnection(boolean)}.
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the {@link HttpConnection} used for the upgrade.
     */
    Single<UpgradableHttpResponse<I, O>> upgradeConnection(HttpRequest<I> request);

    /**
     * A special type of {@link HttpConnection} for the exclusive use of the caller of {@link #reserveConnection(HttpRequest)}.
     * @param <I> The type of content of the request.
     * @param <O> The type of content of the response.
     */
    interface ReservedHttpConnection<I, O> extends HttpConnection<I, O> {
        /**
         * Releases this reserved {@link HttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on release.
         */
        Completable release();
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(HttpRequest)}. This objects
     * allows the upgrade code to inform the HTTP implementation if the {@link HttpConnection} can continue using the HTTP protocol or not.
     * @param <I> The type of content of the request.
     * @param <O> The type of content of the response.
     */
    interface UpgradableHttpResponse<I, O> extends HttpResponse<O> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link ReservedHttpConnection#release()} on the return value!
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link HttpConnection} associated with the return value can be used by this
         *     {@link HttpClient} when {@link ReservedHttpConnection#release()} is called. This typically means the upgrade
         *     attempt was unsuccessful, but you can continue talking HTTP. However this may also be used if the upgrade
         *     was successful, but the upgrade protocol shares semantics that are similar enough to HTTP that the same
         *     {@link HttpClient} API can still be used (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link HttpConnection} associated with the return value can <strong>not</strong>
         *     be used by this {@link HttpClient} when {@link ReservedHttpConnection#release()} is called. This typically means
         *     the upgrade attempt was successful and the semantics of the upgrade protocol are sufficiently different that
         *     the {@link HttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link ReservedHttpConnection} which contains the {@link HttpConnection} used for the upgrade attempt, and
         * controls the lifetime of the {@link HttpConnection} relative to this {@link HttpClient}.
         */
        ReservedHttpConnection<I, O> getHttpConnection(boolean releaseReturnsToClient);
    }
}
