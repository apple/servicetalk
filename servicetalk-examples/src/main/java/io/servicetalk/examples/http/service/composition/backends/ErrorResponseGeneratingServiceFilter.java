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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;

import java.util.Iterator;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * A service filter that simulates errors based on a query parameter value.
 */
public final class ErrorResponseGeneratingServiceFilter implements StreamingHttpService {

    public static final String ERROR_QP_NAME = "simulateError";

    private final StreamingHttpService service;
    private final String serviceName;

    ErrorResponseGeneratingServiceFilter(final StreamingHttpService service, final String serviceName) {
        this.service = service;
        this.serviceName = serviceName;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request, final StreamingHttpResponseFactory responseFactory) {
        final Iterator<String> parameters = request.queryParameters(ERROR_QP_NAME);
        while (parameters.hasNext()) {
            if (parameters.next().equals(serviceName)) {
                return succeeded(responseFactory.internalServerError());
            }
        }
        return service.handle(ctx, request, responseFactory);
    }

    @Override
    public Completable closeAsync() {
        return service.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return service.closeAsyncGracefully();
    }
}
