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

import java.util.function.BiFunction;

/**
 * Same as {@link StreamingHttpService} but that accepts {@link HttpRequest} and returns {@link HttpResponse}.
 */
public abstract class HttpService implements AsyncCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     */
    public abstract Single<HttpResponse> handle(HttpServiceContext ctx, HttpRequest request);

    /**
     * Closes this {@link HttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link HttpService}.
     */
    @Override
    public Completable closeAsync() {
        return Completable.completed();
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
     * Convert this {@link HttpService} to the {@link BlockingStreamingHttpService} API.
     *
     * @return a {@link BlockingStreamingHttpService} representation of this {@link HttpService}.
     */
    public final BlockingStreamingHttpService asBlockingStreamingService() {
        return asStreamingService().asBlockingStreamingService();
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
     * Create a new {@link HttpService} from a {@link BiFunction}.
     *
     * @param handleFunc Provides the functionality for the {@link #handle(HttpServiceContext, HttpRequest)} method.
     * @return a new {@link HttpService}.
     */
    public static HttpService from(BiFunction<HttpServiceContext, HttpRequest, Single<HttpResponse>> handleFunc) {
        return new HttpService() {
            @Override
            public Single<HttpResponse> handle(final HttpServiceContext ctx, final HttpRequest request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }

    StreamingHttpService asStreamingServiceInternal() {
        return new HttpServiceToStreamingHttpService(this);
    }

    BlockingHttpService asBlockingServiceInternal() {
        return new HttpServiceToBlockingHttpService(this);
    }
}
