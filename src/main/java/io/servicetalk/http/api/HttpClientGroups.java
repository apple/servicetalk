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

import java.util.function.Function;

/**
 * Factory methods for creating {@link HttpClientGroup}s.
 */
public final class HttpClientGroups {

    private HttpClientGroups() {
        // no instances
    }

    /**
     * Creates an {@link HttpClientGroup} instance which will use provided client factory function
     * to build inner {@link HttpClient}s.
     *
     * @param clientFactory A factory to create a {@link HttpClient} for the passed {@link GroupKey}.
     * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
     * @param <I> The type of payload for the requests sent by the returned {@link HttpClientGroup}.
     * @param <O> The type of payload for the response received by the returned {@link HttpClientGroup}.
     * @return A new {@link HttpClientGroup}.
     */
    public static <UnresolvedAddress, I, O> HttpClientGroup<UnresolvedAddress, I, O> newHttpClientGroup(
            final Function<GroupKey<UnresolvedAddress>, HttpClient<I, O>> clientFactory) {
        return new DefaultHttpClientGroup<>(clientFactory);
    }
}
