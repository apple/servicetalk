/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilter.checkContentLength;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilter.newLimiter;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;

/**
 * Limits the request payload size. The filter will throw an exception which may result in stream/connection closure.
 * A {@link PayloadTooLargeException} will be thrown when the maximum payload size is exceeded. The
 * {@code Content-Length} request header (when present) is inspected before the body is read so oversized requests
 * that declare their size fail early; otherwise the streaming body is bounded as bytes arrive.
 */
public final class PayloadSizeLimitingHttpServiceFilter implements StreamingHttpServiceFilterFactory {
    private final int maxRequestPayloadSize;

    /**
     * Create a new instance.
     * @param maxRequestPayloadSize The maximum request payload size allowed.
     */
    public PayloadSizeLimitingHttpServiceFilter(int maxRequestPayloadSize) {
        this.maxRequestPayloadSize = ensureNonNegative(maxRequestPayloadSize, "maxRequestPayloadSize");
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory responseFactory) {
                final PayloadTooLargeException ex = checkContentLength(request.headers(), maxRequestPayloadSize);
                if (ex != null) {
                    if (request.headers().containsIgnoreCase(EXPECT, CONTINUE)) {
                        // Don't subscribe: that would cause NettyHttpServer to write 100 Continue and
                        // the client to send the oversized body.
                        return Single.<StreamingHttpResponse>failed(ex).shareContextOnSubscribe();
                    }
                    // Cancel rather than drain — we have just decided the payload is too large to read.
                    toSource(request.messageBody()).subscribe(DelayedCancellationSubscriber.INSTANCE);
                    return Single.failed(ex);
                }
                return delegate().handle(ctx,
                        request.transformMessageBody(newLimiter(maxRequestPayloadSize)), responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    // This gives our server a grace period in which to send its response before we cancel the incoming stream which
    // has the unfortunate side effect of causing the entire dispatch to be cancelled.
    private static final class DelayedCancellationSubscriber implements PublisherSource.Subscriber<Object> {

        static final PublisherSource.Subscriber<Object> INSTANCE = new DelayedCancellationSubscriber();

        private final Duration delay = Duration.ofMillis(100);
        private final Executor executor = Executors.global();

        private DelayedCancellationSubscriber() {
            // singleton.
        }

        @Override
        public void onSubscribe(PublisherSource.Subscription subscription) {
            executor.schedule(subscription::cancel, delay);
        }

        @Override
        public void onNext(@Nullable Object o) {
            // because we never request items we should never get one.
        }

        @Override
        public void onError(Throwable t) {
            // don't care.
        }

        @Override
        public void onComplete() {
            // don't care.
        }
    }
}
