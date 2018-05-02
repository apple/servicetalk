/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;
import io.servicetalk.transport.api.ConnectionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HttpResponses.fromBlockingResponse;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingHttpServiceToHttpService<I, O> extends HttpService<I, O> {
    private final BlockingHttpService<I, O> blockingHttpService;

    BlockingHttpServiceToHttpService(BlockingHttpService<I, O> blockingHttpService) {
        this.blockingHttpService = requireNonNull(blockingHttpService);
    }

    @Override
    public Single<HttpResponse<O>> handle(final ConnectionContext ctx, final HttpRequest<I> request) {
        return new Single<HttpResponse<O>>() {
            @Override
            protected void handleSubscribe(Subscriber<? super HttpResponse<O>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final HttpResponse<O> response;
                try {
                    // Do the conversion inside the try/catch in case there is an exception.
                    response = fromBlockingResponse(blockingHttpService.handle(ctx,
                            new DefaultBlockingHttpRequest<>(request)), ctx.getExecutor());
                } catch (Throwable cause) {
                    cancellable.setDone();
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.setDone();

                // The from(..) operator will take care of propagating cancel.
                subscriber.onSuccess(response);
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingHttpService::close);
    }

    @Override
    BlockingHttpService<I, O> asBlockingServiceInternal() {
        return blockingHttpService;
    }
}
