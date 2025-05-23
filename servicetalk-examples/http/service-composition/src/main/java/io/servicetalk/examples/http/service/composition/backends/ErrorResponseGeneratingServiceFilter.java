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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * A service filter that simulates errors based on a query parameter value.
 */
public final class ErrorResponseGeneratingServiceFilter implements StreamingHttpServiceFilterFactory {

    public static final String SIMULATE_ERROR_QP_NAME = "simulateError";

    private final String serviceName;

    public ErrorResponseGeneratingServiceFilter(final String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                if (request.hasQueryParameter(SIMULATE_ERROR_QP_NAME, serviceName)) {
                    return succeeded(responseFactory.internalServerError());
                }
                return delegate().handle(ctx, request, responseFactory);
            }
        };
    }
}
