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

import static io.servicetalk.utils.internal.PlatformDependent.throwException;

final class RequestResponseFactories {

    private RequestResponseFactories() {
        // no instances
    }

    static BlockingStreamingHttpRequestResponseFactory toBlockingStreaming(
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(reqRespFactory);
    }

    static BlockingStreamingHttpRequestResponseFactory toBlockingStreaming(final StreamingHttpRequester requester) {
        return new StreamingHttpRequesterToBlockingStreamingHttpRequestResponseFactory(requester);
    }

    private static final class StreamingHttpRequesterToBlockingStreamingHttpRequestResponseFactory
            implements BlockingStreamingHttpRequestResponseFactory {
        private final StreamingHttpRequester requester;

        private StreamingHttpRequesterToBlockingStreamingHttpRequestResponseFactory(
                final StreamingHttpRequester requester) {
            this.requester = requester;
        }

        @Override
        public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return requester.newRequest(method, requestTarget).toBlockingStreamingRequest();
        }

        @Override
        public BlockingStreamingHttpResponse newResponse(final HttpResponseStatus status) {
            return requester.httpResponseFactory().newResponse(status).toBlockingStreamingResponse();
        }
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

    static HttpRequestResponseFactory toAggregated(final StreamingHttpRequester requester) {
        return new StreamingHttpRequesterToHttpRequestResponseFactory(requester);
    }

    private static final class StreamingHttpRequesterToHttpRequestResponseFactory
            implements HttpRequestResponseFactory {
        private final StreamingHttpRequester requester;

        private StreamingHttpRequesterToHttpRequestResponseFactory(final StreamingHttpRequester requester) {
            this.requester = requester;
        }

        @Override
        public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return newRequestBlocking(requester, method, requestTarget);
        }

        @Override
        public HttpResponse newResponse(final HttpResponseStatus status) {
            return newResponseBlocking(requester.httpResponseFactory(), status);
        }
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
    }

    private static HttpRequest newRequestBlocking(StreamingHttpRequestFactory requestFactory,
                                                  HttpRequestMethod method, String requestTarget) {
        try {
            return requestFactory.newRequest(method, requestTarget).toRequest().toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            return throwException(e);
        }
    }

    private static HttpResponse newResponseBlocking(StreamingHttpResponseFactory responseFactory,
                                                    HttpResponseStatus status) {
        try {
            return responseFactory.newResponse(status).toResponse().toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            return throwException(e);
        }
    }
}
