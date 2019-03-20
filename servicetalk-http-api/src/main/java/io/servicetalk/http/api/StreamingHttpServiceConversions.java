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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static java.util.Objects.requireNonNull;

/**
 * Conversion routines to {@link StreamingHttpService}.
 */
public final class StreamingHttpServiceConversions {
    private StreamingHttpServiceConversions() {
        // no instances
    }

    /**
     * Convert from a {@link StreamingHttpRequestHandler} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link StreamingHttpRequestHandler} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(StreamingHttpRequestHandler handler) {
        return handler instanceof StreamingHttpService ? (StreamingHttpService) handler :
                        new StreamingHttpRequestHandlerToStreamingHttpService(handler);
    }

    /**
     * Convert from a {@link HttpRequestHandler} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link HttpRequestHandler} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(HttpRequestHandler handler) {
        return toStreamingHttpService(toHttpService(handler));
    }

    /**
     * Convert from a {@link BlockingStreamingHttpRequestHandler} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link BlockingStreamingHttpRequestHandler} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(BlockingStreamingHttpRequestHandler handler) {
        return BlockingStreamingHttpServiceToStreamingHttpService.transform(toBlockingStreamingService(handler));
    }

    /**
     * Convert from a {@link BlockingHttpRequestHandler} to a {@link StreamingHttpService}.
     *
     * @param handler The {@link BlockingHttpRequestHandler} to convert.
     * @return The conversion result.
     */
    public static StreamingHttpService toStreamingHttpService(BlockingHttpRequestHandler handler) {
        return BlockingHttpServiceToStreamingHttpService.transform(toBlockingService(handler));
    }

    static BlockingHttpService toBlockingService(BlockingHttpRequestHandler handler) {
        return handler instanceof BlockingHttpService ? (BlockingHttpService) handler :
            new BlockingHttpRequestHandlerToBlockingHttpService(handler);
    }

    static BlockingStreamingHttpService toBlockingStreamingService(BlockingStreamingHttpRequestHandler handler) {
        return handler instanceof BlockingStreamingHttpService ? (BlockingStreamingHttpService) handler :
                new BlockingStreamingHttpRequestHandlerToBlockingStreamingHttpService(handler);
    }

    static StreamingHttpService toStreamingHttpService(HttpService service) {
        return HttpServiceToStreamingHttpService.transform(service);
    }

    static HttpService toHttpService(HttpRequestHandler handler) {
        return handler instanceof HttpService ? (HttpService) handler :
                new HttpRequestHandlerToHttpService(handler);
    }

    private static final class BlockingHttpRequestHandlerToBlockingHttpService implements BlockingHttpService {
        private final BlockingHttpRequestHandler handler;

        private BlockingHttpRequestHandlerToBlockingHttpService(final BlockingHttpRequestHandler handler) {
            this.handler = requireNonNull(handler);
        }

        @Override
        public HttpResponse handle(final HttpServiceContext ctx,
                                   final HttpRequest request,
                                   final HttpResponseFactory responseFactory) throws Exception {
            return handler.handle(ctx, request, responseFactory);
        }

        @Override
        public void close() throws Exception {
            handler.close();
        }
    }

    private static final class BlockingStreamingHttpRequestHandlerToBlockingStreamingHttpService
            implements BlockingStreamingHttpService {
        private final BlockingStreamingHttpRequestHandler handler;

        private BlockingStreamingHttpRequestHandlerToBlockingStreamingHttpService(
                final BlockingStreamingHttpRequestHandler handler) {
            this.handler = requireNonNull(handler);
        }

        @Override
        public void handle(final HttpServiceContext ctx,
                           final BlockingStreamingHttpRequest request,
                           final BlockingStreamingHttpServerResponse response) throws Exception {
            handler.handle(ctx, request, response);
        }

        @Override
        public void close() throws Exception {
            handler.close();
        }
    }

    private static final class HttpRequestHandlerToHttpService implements HttpService {
        private final HttpRequestHandler handler;

        private HttpRequestHandlerToHttpService(final HttpRequestHandler handler) {
            this.handler = requireNonNull(handler);
        }

        @Override
        public Single<HttpResponse> handle(final HttpServiceContext ctx,
                                           final HttpRequest request,
                                           final HttpResponseFactory responseFactory) {
            return handler.handle(ctx, request, responseFactory);
        }

        @Override
        public Completable closeAsync() {
            return handler.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return handler.closeAsyncGracefully();
        }
    }

    private static final class StreamingHttpRequestHandlerToStreamingHttpService implements StreamingHttpService {
        private final StreamingHttpRequestHandler handler;

        private StreamingHttpRequestHandlerToStreamingHttpService(final StreamingHttpRequestHandler handler) {
            this.handler = requireNonNull(handler);
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            return handler.handle(ctx, request, responseFactory);
        }

        @Override
        public Completable closeAsync() {
            return handler.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return handler.closeAsyncGracefully();
        }
    }
}
