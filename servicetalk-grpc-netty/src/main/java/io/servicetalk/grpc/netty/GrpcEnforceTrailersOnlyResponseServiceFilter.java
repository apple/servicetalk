/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.grpc.internal.GrpcContextKeys.TRAILERS_ONLY_RESPONSE;

final class GrpcEnforceTrailersOnlyResponseServiceFilter implements StreamingHttpServiceFilterFactory {
    static final GrpcEnforceTrailersOnlyResponseServiceFilter INSTANCE =
            new GrpcEnforceTrailersOnlyResponseServiceFilter();

    private GrpcEnforceTrailersOnlyResponseServiceFilter() {
    }

    @Override
    public StreamingHttpServiceFilter create(StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory).flatMap(response -> {
                    Single<StreamingHttpResponse> mappedResponse;
                    if (Boolean.TRUE.equals(response.context().get(TRAILERS_ONLY_RESPONSE))) {
                        mappedResponse = response.toResponse().map(HttpResponse::toStreamingResponse);
                    } else {
                        mappedResponse = Single.succeeded(response);
                    }
                    return mappedResponse.shareContextOnSubscribe();
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }
}
