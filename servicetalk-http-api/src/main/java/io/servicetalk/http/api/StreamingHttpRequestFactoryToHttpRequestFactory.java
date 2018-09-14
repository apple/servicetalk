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

import static io.servicetalk.http.api.StreamingHttpRequestResponseFactories.newRequestBlocking;
import static io.servicetalk.http.api.StreamingHttpRequestResponseFactories.newResponseBlocking;
import static java.util.Objects.requireNonNull;

final class StreamingHttpRequestFactoryToHttpRequestFactory implements HttpRequestFactory {
    private final StreamingHttpRequestFactory requestFactory;
    private final HttpResponseFactory responseFactory;

    StreamingHttpRequestFactoryToHttpRequestFactory(StreamingHttpRequestFactory requestFactory) {
        this.requestFactory = requireNonNull(requestFactory);
        this.responseFactory = new StreamingHttpResponseFactoryToHttpResponseFactory(
                requestFactory.getHttpResponseFactory(), this);
    }

    @Override
    public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return newRequestBlocking(requestFactory, method, requestTarget);
    }

    @Override
    public HttpResponseFactory getHttpResponseFactory() {
        return responseFactory;
    }

    private static final class StreamingHttpResponseFactoryToHttpResponseFactory implements HttpResponseFactory {
        private final StreamingHttpResponseFactory responseFactory;
        private final HttpRequestFactory requestFactory;

        StreamingHttpResponseFactoryToHttpResponseFactory(StreamingHttpResponseFactory responseFactory,
                                                          HttpRequestFactory requestFactory) {
            this.responseFactory = responseFactory;
            this.requestFactory = requestFactory;
        }

        @Override
        public HttpResponse newResponse(final HttpResponseStatus status) {
            return newResponseBlocking(responseFactory, status);
        }

        @Override
        public HttpRequestFactory getHttpRequestFactory() {
            return requestFactory;
        }
    }
}
