/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.internal.SubscribableSingle;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.fromCallable;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_DATA_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

final class BlockingToStreamingService extends AbstractServiceAdapterHolder {
    static final HttpExecutionStrategy DEFAULT_STRATEGY = OFFLOAD_RECEIVE_DATA_STRATEGY;
    private final BlockingHttpService original;
    private final boolean interruptOnCancel;

    BlockingToStreamingService(final BlockingHttpService original, final HttpExecutionStrategy strategy,
                               final boolean interruptOnCancel) {
        super(defaultStrategy() == strategy ? DEFAULT_STRATEGY : strategy);
        this.original = requireNonNull(original);
        this.interruptOnCancel = interruptOnCancel;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return request.toRequest().flatMap(req -> (interruptOnCancel ?
                fromCallable(() -> original.handle(ctx, req, ctx.responseFactory()).toStreamingResponse()) :
                uninterruptible(ctx, req)).shareContextOnSubscribe());
    }

    /**
     * Same as the {@code interruptOnCancel} branch of {@link #handle}, except the handling thread is never
     * {@link Thread#interrupt() interrupted} on cancellation.
     */
    private Single<StreamingHttpResponse> uninterruptible(final HttpServiceContext ctx,
                                                          final HttpRequest req) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }

                final StreamingHttpResponse result;
                try {
                    result = original.handle(ctx, req, ctx.responseFactory()).toStreamingResponse();
                } catch (Throwable cause) {
                    if (cause instanceof InterruptedException) {
                        // Mirrors ThreadInterruptingCancellable#setDone(Throwable): clear a stale interrupt flag
                        // before the (likely pooled) thread is reused, in case something other than
                        // cancellation (e.g. executor shutdown) interrupted it during handling.
                        Thread.interrupted();
                    }
                    safeOnError(subscriber, cause);
                    return;
                }
                subscriber.onSuccess(result);
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return Completable.fromCallable(() -> {
            original.close();
            return null;
        });
    }

    @Override
    public Completable closeAsyncGracefully() {
        return Completable.fromCallable(() -> {
            original.closeGracefully();
            return null;
        });
    }
}
