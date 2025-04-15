/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.concurrent.internal.NoopSubscribers;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

/**
 * Configure automatic consumption of request {@link StreamingHttpRequest#payloadBody() payload body} when it is not
 * consumed by the service.
 * <p>
 * For <a href="https://tools.ietf.org/html/rfc7230#section-6.3">persistent HTTP connections</a> it is required to
 * eventually consume the entire request payload to enable reading of the next request. This is required because
 * requests are pipelined for HTTP/1.1, so if the previous request is not completely read, next request can not be
 * read from the socket. For cases when there is a possibility that user may forget to consume request payload,
 * this filter can be used to automatically consume request payload body on the server-side.
 * An example of guaranteed consumption are {@link HttpRequest non-streaming APIs}.
 *
 * @see io.servicetalk.http.api.HttpServerBuilder#drainRequestPayloadBody(boolean) for adding this filter automatically
 * when the server is constructed.
 */
public final class HttpRequestAutoDrainingServiceFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Singleton instance of the draining service filter.
     */
    public static final StreamingHttpServiceFilterFactory INSTANCE = new HttpRequestAutoDrainingServiceFilter();

    private HttpRequestAutoDrainingServiceFilter() {
        // singleton
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    @Override
    public StreamingHttpServiceFilter create(StreamingHttpService service) {
        return new DrainingStreamingHttpServiceFilter(service);
    }

    private static final class DrainingStreamingHttpServiceFilter extends StreamingHttpServiceFilter {

        DrainingStreamingHttpServiceFilter(StreamingHttpService service) {
            super(service);
        }

        @Override
        public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                    StreamingHttpResponseFactory responseFactory) {
            final DrainTerminalSignalConsumer terminalSignalConsumer = new DrainTerminalSignalConsumer(request);
            request.transformMessageBody(body -> body.beforeSubscriber(terminalSignalConsumer));
            return delegate().handle(ctx, request, responseFactory)
                    .liftSync(new AfterFinallyHttpOperator(terminalSignalConsumer, true));
        }
    }

    private static final class DrainTerminalSignalConsumer implements TerminalSignalConsumer,
            Supplier<PublisherSource.Subscriber<Object>> {

        private static final AtomicIntegerFieldUpdater<DrainTerminalSignalConsumer> stateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(DrainTerminalSignalConsumer.class, "state");

        private static final int PENDING = 0;
        private static final int COMPLETE = 1;

        private final StreamingHttpRequest request;
        @SuppressWarnings("unused")
        private volatile int state = PENDING;

        DrainTerminalSignalConsumer(StreamingHttpRequest request) {
            this.request = request;
        }

        @Override
        public PublisherSource.Subscriber<Object> get() {
            once();
            return NoopSubscribers.NOOP_PUBLISHER_SUBSCRIBER;
        }

        @Override
        public void onComplete() {
            if (once()) {
                request.messageBody().ignoreElements().shareContextOnSubscribe().subscribe();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            cancel(); // same behavior as cancel()
        }

        @Override
        public void cancel() {
            if (once()) {
                toSource(request.messageBody().shareContextOnSubscribe())
                        .subscribe(CancelImmediatelySubscriber.INSTANCE);
            }
        }

        private boolean once() {
            return stateUpdater.compareAndSet(this, PENDING, COMPLETE);
        }
    }
}
