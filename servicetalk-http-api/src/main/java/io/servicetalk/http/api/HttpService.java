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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.http.api.HttpApiConversions.DEFAULT_SERVICE_STRATEGY;

/**
 * Same as {@link StreamingHttpService} but that accepts {@link HttpRequest} and returns {@link HttpResponse}.
 */
@FunctionalInterface
public interface HttpService extends AsyncCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param responseFactory used to create {@link HttpResponse} objects.
     * @return {@link Single} of HTTP response.
     */
    Single<HttpResponse> handle(HttpServiceContext ctx, HttpRequest request, HttpResponseFactory responseFactory);

    /**
     * Closes this {@link HttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link HttpService}.
     */
    @Override
    default Completable closeAsync() {
        return Completable.completed();
    }

    /**
     * Compute a {@link HttpExecutionStrategy} given the programming model constraints of this
     * {@link BlockingStreamingHttpService} in combination with another {@link HttpExecutionStrategy}. This may involve
     * a merge operation between two {@link BlockingStreamingHttpService}.
     *
     * @param other The other {@link HttpExecutionStrategy} to consider during the computation.
     * @return The {@link HttpExecutionStrategy} for this {@link BlockingStreamingHttpService}.
     */
    default HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
        return other.merge(DEFAULT_SERVICE_STRATEGY);
    }
}
