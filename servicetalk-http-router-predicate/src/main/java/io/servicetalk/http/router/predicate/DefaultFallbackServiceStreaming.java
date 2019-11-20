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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;

final class DefaultFallbackServiceStreaming implements StreamingHttpService {

    private static final DefaultFallbackServiceStreaming INSTANCE = new DefaultFallbackServiceStreaming();

    private DefaultFallbackServiceStreaming() {
        // singleton
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory factory) {
        final StreamingHttpResponse response = factory.newResponse(NOT_FOUND).version(request.version());
        response.headers().set(CONTENT_LENGTH, ZERO)
                .set(CONTENT_TYPE, TEXT_PLAIN_UTF_8);
        // TODO(derek): Set keepalive once we have an isKeepAlive helper method.
        return Single.succeeded(response);
    }

    static StreamingHttpService instance() {
        return INSTANCE;
    }
}
