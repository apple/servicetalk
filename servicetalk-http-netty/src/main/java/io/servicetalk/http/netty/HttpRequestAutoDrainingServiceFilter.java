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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;

import javax.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

public final class HttpRequestAutoDrainingServiceFilter implements StreamingHttpServiceFilterFactory {

    static final StreamingHttpServiceFilterFactory INSTANCE = new HttpRequestAutoDrainingServiceFilter();

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
            return defer(() -> doHandle(ctx, request, responseFactory).shareContextOnSubscribe());
        }

        private Single<StreamingHttpResponse> doHandle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                    StreamingHttpResponseFactory responseFactory) {
            final AtomicBoolean payloadSubscribed = new AtomicBoolean();
            request.transformMessageBody(body ->
                    body.beforeSubscriber(() -> {
                        payloadSubscribed.set(true);
                        return HttpMessageDiscardWatchdogServiceFilter.NoopSubscriber.INSTANCE;
                    }));
            return delegate().handle(ctx, request, responseFactory)
                    .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                @Override
                public void onComplete() {
                    request.payloadBody().ignoreElements().shareContextOnSubscribe().subscribe();
                }

                @Override
                public void onError(Throwable throwable) {
                    cancel(); // same behavior as cancel()
                }

                @Override
                public void cancel() {
                    maybeCancelMessageBody(payloadSubscribed, request.messageBody());
                }
            }, true));
        }

        private void maybeCancelMessageBody(AtomicBoolean payloadSubscribed, Publisher<?> messageBody) {
            if (payloadSubscribed.compareAndSet(false, true)) {
                cancelMessageBody(messageBody);
            }
        }

        private void cancelMessageBody(Publisher<?> messageBody) {
            toSource(messageBody.shareContextOnSubscribe()).subscribe(CancelImmediatelySubscriber.INSTANCE);
        }
    }
}
