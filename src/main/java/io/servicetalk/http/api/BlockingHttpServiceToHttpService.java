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

final class BlockingHttpServiceToHttpService extends HttpService {
    private final BlockingHttpService blockingHttpService;

    BlockingHttpServiceToHttpService(BlockingHttpService blockingHttpService) {
        this.blockingHttpService = requireNonNull(blockingHttpService);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        return new Single<HttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final HttpResponse<HttpPayloadChunk> response;
                try {
                    // Do the conversion inside the try/catch in case there is an exception.
                    response = fromBlockingResponse(blockingHttpService.handle(ctx,
                            new DefaultBlockingHttpRequest<>(request)));
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
    BlockingHttpService asBlockingServiceInternal() {
        return blockingHttpService;
    }
}
