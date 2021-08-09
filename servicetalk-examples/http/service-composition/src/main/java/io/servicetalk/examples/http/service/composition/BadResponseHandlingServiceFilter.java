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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.ResponseCheckingClientFilter.BadResponseStatusException;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Example service filter that returns a response with the exception message if the wrapped service completes with a
 * {@link BadResponseStatusException}.
 */
final class BadResponseHandlingServiceFilter implements StreamingHttpServiceFilterFactory {
    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                return super.handle(ctx, request, responseFactory).onErrorResume(cause -> {
                    if (cause instanceof BadResponseStatusException) {
                        // It's useful to include the exception message in the payload for demonstration purposes, but
                        // this is not recommended in production as it may leak internal information.
                        return responseFactory.internalServerError().toResponse().map(
                                resp -> resp.payloadBody(cause.getMessage(), textSerializerUtf8()).toStreamingResponse());
                    }
                    return failed(cause);
                });
            }
        };
    }
}
