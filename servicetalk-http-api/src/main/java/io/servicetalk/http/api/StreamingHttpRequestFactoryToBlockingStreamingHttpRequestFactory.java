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

import java.util.Objects;

final class StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory implements
                                                                         BlockingStreamingHttpRequestFactory {
    private final StreamingHttpRequestFactory requestFactory;
    private final BlockingStreamingHttpResponseFactory responseFactory;

    StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory(StreamingHttpRequestFactory requestFactory) {
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.responseFactory = new StreamingHttpResponseFactoryToBlockingStreamingHttpResponseFactory(
                requestFactory.getHttpResponseFactory(), this);
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return requestFactory.newRequest(method, requestTarget).toBlockingStreamingRequest();
    }

    @Override
    public BlockingStreamingHttpResponseFactory getHttpResponseFactory() {
        return responseFactory;
    }

    private static final class StreamingHttpResponseFactoryToBlockingStreamingHttpResponseFactory implements
                                                                                  BlockingStreamingHttpResponseFactory {
        private final StreamingHttpResponseFactory responseFactory;
        private final BlockingStreamingHttpRequestFactory requestFactory;

        private StreamingHttpResponseFactoryToBlockingStreamingHttpResponseFactory(
                final StreamingHttpResponseFactory responseFactory,
                final BlockingStreamingHttpRequestFactory requestFactory) {
            this.responseFactory = responseFactory;
            this.requestFactory = requestFactory;
        }

        @Override
        public BlockingStreamingHttpResponse newResponse(final HttpResponseStatus status) {
            return responseFactory.newResponse(status).toBlockingStreamingResponse();
        }

        @Override
        public BlockingStreamingHttpRequestFactory getHttpRequestFactory() {
            return requestFactory;
        }
    }
}
