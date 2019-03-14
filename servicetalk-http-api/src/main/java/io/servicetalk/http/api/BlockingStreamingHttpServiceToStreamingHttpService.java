/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleProcessor;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpServiceToStreamingHttpService extends StreamingHttpService {
    private final BlockingStreamingHttpService service;
    private final HttpExecutionStrategy effectiveStrategy;

    private BlockingStreamingHttpServiceToStreamingHttpService(final BlockingStreamingHttpService service,
                                                               final HttpExecutionStrategy effectiveStrategy) {
        this.service = requireNonNull(service);
        this.effectiveStrategy = requireNonNull(effectiveStrategy);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {

        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final ThreadInterruptingCancellable tiCancellable = new ThreadInterruptingCancellable(currentThread());

                final SingleProcessor<StreamingHttpResponse> responseProcessor = new SingleProcessor<>();
                responseProcessor.subscribe(new Subscriber<StreamingHttpResponse>() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        subscriber.onSubscribe(() -> {
                            try {
                                cancellable.cancel();
                            } finally {
                                tiCancellable.cancel();
                            }
                        });
                    }

                    @Override
                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                        subscriber.onSuccess(result);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        subscriber.onError(t);
                    }
                });
                try {
                    final BlockingStreamingHttpServerResponse response = new DefaultBlockingStreamingHttpServerResponse(
                            OK, request.version(),
                            ctx.headersFactory().newHeaders(), ctx.headersFactory().newTrailers(),
                            ctx.executionContext().bufferAllocator(), responseProcessor, tiCancellable);
                    service.handle(ctx, request.toBlockingStreamingRequest(), response);
                } catch (Throwable cause) {
                    tiCancellable.setDone(cause);
                    subscriber.onError(cause);
                }
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(service::close);
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return effectiveStrategy;
    }

    static StreamingHttpService transform(final BlockingStreamingHttpService service) {
        // The recommended approach for filtering is using the filter factories which forces people to use the
        // StreamingHttpServiceFilter API and use the effective strategy. When that path is used, then we will not get
        // here as the intermediate transitions take care of returning the original StreamingHttpService.
        // If we are here, it is for a user implemented BlockingStreamingHttpService, so we assume the strategy provided
        // by the passed service is the effective strategy.
        return new BlockingStreamingHttpServiceToStreamingHttpService(service, service.executionStrategy());
    }
}
