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
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Example service filter that converts a {@link HttpResponseException} from the wrapped service into a response,
 * logging the cause server-side.
 */
final class HttpResponseExceptionHandlingServiceFilter implements StreamingHttpServiceFilterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResponseExceptionHandlingServiceFilter.class);

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory).onErrorResume(cause -> {
                    if (cause instanceof HttpResponseException) {
                        // Log the full cause server-side and return only the non-sensitive upstream status code to the
                        // client. Echoing cause.getMessage() into the payload may leak internal information.
                        LOGGER.warn("Unexpected response from a backend service", cause);
                        final HttpResponseMetaData response = ((HttpResponseException) cause).metaData();
                        return responseFactory.internalServerError().toResponse().map(resp -> resp.payloadBody(
                                "Unexpected response status from a backend service: " + response.status(),
                                textSerializerUtf8()).toStreamingResponse());
                    }
                    // Pass all other exceptions as-is, they will be handled by default HttpExceptionMapperServiceFilter
                    return failed(cause);
                });
            }
        };
    }
}
