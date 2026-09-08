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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.fromCallable;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_DATA_STRATEGY;
import static io.servicetalk.http.api.DisableInterruptOnCancelHttpServiceFilter.interruptOnCancel;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

final class BlockingToStreamingService extends AbstractServiceAdapterHolder {
    static final HttpExecutionStrategy DEFAULT_STRATEGY = OFFLOAD_RECEIVE_DATA_STRATEGY;
    private final BlockingHttpService original;

    BlockingToStreamingService(final BlockingHttpService original, HttpExecutionStrategy strategy) {
        super(defaultStrategy() == strategy ? DEFAULT_STRATEGY : strategy);
        this.original = requireNonNull(original);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        final boolean interruptOnCancel = interruptOnCancel(request);
        return request.toRequest().flatMap(req -> {
            Single<StreamingHttpResponse> response = fromCallable(
                    () -> original.handle(ctx, req, ctx.responseFactory()).toStreamingResponse());
            if (!interruptOnCancel) {
                response = response.<StreamingHttpResponse>liftSync(IgnoreCancellationSubscriber::new);
            }
            return response.shareContextOnSubscribe();
        });
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

    /**
     * Hides the {@link Cancellable} of {@code Single.fromCallable}, which would otherwise interrupt the service
     * thread.
     */
    private static final class IgnoreCancellationSubscriber implements Subscriber<StreamingHttpResponse> {
        private final Subscriber<? super StreamingHttpResponse> delegate;

        IgnoreCancellationSubscriber(final Subscriber<? super StreamingHttpResponse> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delegate.onSubscribe(IGNORE_CANCEL);
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse result) {
            delegate.onSuccess(result);
        }

        @Override
        public void onError(final Throwable t) {
            delegate.onError(t);
        }
    }
}
