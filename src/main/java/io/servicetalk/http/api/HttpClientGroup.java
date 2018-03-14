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

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

/**
 * Logically this interface provides a <pre>{@code Map<GroupKey, HttpClient>}</pre>, and also the ability to create new
 * {@link HttpClient} objects if none yet exist.
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 * @param <I> The type of content of the request.
 * @param <O> The type of content of the response.
 */
public interface HttpClientGroup<UnresolvedAddress, I, O> extends ListenableAsyncCloseable {
    /**
     * Locate or create a client and delegate to {@link HttpClient#request(HttpRequest)}.
     * @param key identifies the {@link HttpClient} to use, or provides enough information to create a {@link HttpClient} if non exist.
     * @param request the request to send.
     * @return The response.
     * @see HttpClient#request(HttpRequest)
     */
    Single<HttpResponse<O>> request(GroupKey<UnresolvedAddress> key, HttpRequest<I> request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#reserveConnection(HttpRequest)}.
     * @param key identifies the {@link HttpClient} to use, or provides enough information to create a {@link HttpClient} if non exist.
     * @param request the request which may provide more information about which {@link HttpConnection} to reserve.
     * @return A {@link ReservedHttpConnection}.
     * @see HttpClient#reserveConnection(HttpRequest)
     */
    Single<ReservedHttpConnection<I, O>> reserveConnection(GroupKey<UnresolvedAddress> key, HttpRequest<I> request);
}
