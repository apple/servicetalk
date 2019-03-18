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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY;

/**
 * Same as {@link StreamingHttpService} but that accepts {@link HttpRequest} and returns {@link HttpResponse}.
 */
public abstract class HttpService implements HttpRequestHandler, AsyncCloseable {
    static final HttpExecutionStrategy DEFAULT_SERVICE_STRATEGY = OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY;
    /**
     * Closes this {@link HttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link HttpService}.
     */
    @Override
    public Completable closeAsync() {
        return Completable.completed();
    }

    @Override
    public final HttpService asService() {
        return this;
    }

    /**
     * Convert this {@link HttpService} to the {@link StreamingHttpService} API.
     *
     * @return a {@link StreamingHttpService} representation of this {@link HttpService}.
     */
    public final StreamingHttpService asStreamingService() {
        return asStreamingServiceInternal();
    }

    /**
     * Convert this {@link HttpService} to the {@link BlockingHttpService} API.
     *
     * @return a {@link BlockingHttpService} representation of this {@link HttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asBlockingServiceInternal();
    }

    /**
     * Returns the {@link HttpExecutionStrategy}.
     *
     * @return The {@link HttpExecutionStrategy} for this {@link HttpService}.
     */
    public HttpExecutionStrategy executionStrategy() {
        return DEFAULT_SERVICE_STRATEGY;
    }

    StreamingHttpService asStreamingServiceInternal() {
        return HttpServiceToStreamingHttpService.transform(this);
    }

    BlockingHttpService asBlockingServiceInternal() {
        return HttpServiceToBlockingHttpService.transform(this);
    }
}
