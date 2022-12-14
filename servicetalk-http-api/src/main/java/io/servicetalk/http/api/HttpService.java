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

import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY;

/**
 * Same as {@link StreamingHttpService} but that accepts {@link HttpRequest} and returns {@link HttpResponse}.
 */
@FunctionalInterface
public interface HttpService extends AsyncCloseable, HttpServiceBase {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @param responseFactory used to create {@link HttpResponse} objects.
     * @return {@link Single} of HTTP response.
     */
    Single<HttpResponse> handle(HttpServiceContext ctx, HttpRequest request, HttpResponseFactory responseFactory);

    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // safe default--implementations are expected to override
        return OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY;
    }

    /**
     * Closes this {@link HttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link HttpService}.
     */
    @Override
    default Completable closeAsync() {
        return Completable.completed();
    }
}
