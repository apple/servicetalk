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
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_DATA_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.ThreadInterruptingCancellableUtils.cancellableForSubscribe;
import static io.servicetalk.http.api.ThreadInterruptingCancellableUtils.newCancellableIfInterrupting;
import static io.servicetalk.http.api.ThreadInterruptingCancellableUtils.setDone;
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
        return request.toRequest().flatMap(req -> new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                @Nullable
                final ThreadInterruptingCancellable tiCancellable = newCancellableIfInterrupting(interruptOnCancel);
                try {
                    subscriber.onSubscribe(cancellableForSubscribe(tiCancellable));
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }

                final StreamingHttpResponse result;
                try {
                    result = original.handle(ctx, req, ctx.responseFactory()).toStreamingResponse();
                } catch (Throwable cause) {
                    setDone(tiCancellable, cause);
                    safeOnError(subscriber, cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and
                // here.
                setDone(tiCancellable);
                subscriber.onSuccess(result);
            }
        }.shareContextOnSubscribe());
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
