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
package io.servicetalk.http.api;

import java.util.concurrent.ExecutionException;

final class RequestResponseFactories {

    private RequestResponseFactories() {
        // no instances
    }

    static StreamingHttpRequestResponseFactory toStreaming(
            final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
        return new BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(reqRespFactory);
    }

    private static final class BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory
            implements StreamingHttpRequestResponseFactory {
        private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

        BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget).toStreamingRequest();
        }

        @Override
        public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
            return reqRespFactory.newResponse(status).toStreamingResponse();
        }
    }

    static BlockingStreamingHttpRequestResponseFactory toBlockingStreaming(
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(reqRespFactory);
    }

    private static final class StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory
            implements BlockingStreamingHttpRequestResponseFactory {
        private final StreamingHttpRequestResponseFactory reqRespFactory;

        StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(
                final StreamingHttpRequestResponseFactory reqRespFactory) {
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget).toBlockingStreamingRequest();
        }

        @Override
        public BlockingStreamingHttpResponse newResponse(final HttpResponseStatus status) {
            return reqRespFactory.newResponse(status).toBlockingStreamingResponse();
        }
    }

    static HttpRequestResponseFactory toAggregated(final StreamingHttpRequestResponseFactory reqRespFactory) {
        return new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(reqRespFactory);
    }

    private static final class StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory
            implements HttpRequestResponseFactory {
        private final StreamingHttpRequestResponseFactory reqRespFactory;

        StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(
                final StreamingHttpRequestResponseFactory reqRespFactory) {
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return newRequestBlocking(reqRespFactory, method, requestTarget);
        }

        @Override
        public HttpResponse newResponse(final HttpResponseStatus status) {
            return newResponseBlocking(reqRespFactory, status);
        }

        static HttpRequest newRequestBlocking(StreamingHttpRequestFactory requestFactory,
                                              HttpRequestMethod method, String requestTarget) {
            try {
                return requestFactory.newRequest(method, requestTarget).toRequest().toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        static HttpResponse newResponseBlocking(StreamingHttpResponseFactory responseFactory,
                                                HttpResponseStatus status) {
            try {
                return responseFactory.newResponse(status).toResponse().toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
