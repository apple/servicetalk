/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Nullable;

/**
 * A function that given flattened stream of {@link HttpRequestMetaData}, payload and
 * trailers, for the passed {@link StreamingHttpRequest} returns a {@link Single}.
 *
 * @param <State> The {@code state} type to use.
 * @deprecated There is no use of this interface in our codebase, it will be removed in the future releases. If you
 * depend on it, consider replicating a similar interface in your codebase.
 */
@Deprecated
@FunctionalInterface
public interface ClientInvoker<State> { // FIXME: 0.43 - remove deprecated interface

    /**
     * Invokes the client.
     *
     * @param objectPublisher flattened stream of {@link HttpRequestMetaData}, payload and trailers.
     * @param state the state to pass.
     * @return a {@link Single} of the {@link StreamingHttpResponse}.
     */
    Single<StreamingHttpResponse> invokeClient(Publisher<Object> objectPublisher, @Nullable State state);
}
