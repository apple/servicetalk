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

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;
import io.servicetalk.http.api.BlockingHttpClientToHttpClient.BlockingToReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HttpResponses.fromBlockingResponse;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientGroupToHttpClientGroup<UnresolvedAddress, I, O> extends
                                                                              HttpClientGroup<UnresolvedAddress, I, O> {
    private final BlockingHttpClientGroup<UnresolvedAddress, I, O> blockingClientGroup;

    BlockingHttpClientGroupToHttpClientGroup(BlockingHttpClientGroup<UnresolvedAddress, I, O> blockingClientGroup) {
        this.blockingClientGroup = requireNonNull(blockingClientGroup);
    }

    @Override
    public Single<HttpResponse<O>> request(final GroupKey<UnresolvedAddress> key, final HttpRequest<I> request) {
        return new Single<HttpResponse<O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<O>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final HttpResponse<O> response;
                try {
                    // Do the conversion inside the try/catch in case there is an exception.
                    response = fromBlockingResponse(blockingClientGroup.request(key,
                            new DefaultBlockingHttpRequest<>(request)),
                            key.getExecutionContext().getExecutor());
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
    public Single<? extends ReservedHttpConnection<I, O>> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest<I> request) {
        return new Single<ReservedHttpConnection<I, O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super ReservedHttpConnection<I, O>> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final ReservedHttpConnection<I, O> response;
                try {
                    // Do the conversion here in case there is a null value returned by the upgradeConnection.
                    response = new BlockingToReservedHttpConnection<>(blockingClientGroup.reserveConnection(
                            key, new DefaultBlockingHttpRequest<>(request)));
                } catch (Throwable cause) {
                    cancellable.setDone();
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.setDone();

                subscriber.onSuccess(response);
            }
        };
    }

    @Override
    public Completable onClose() {
        if (blockingClientGroup instanceof HttpClientGroupToBlockingHttpClientGroup) {
            return ((HttpClientGroupToBlockingHttpClientGroup) blockingClientGroup).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClientGroup.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClientGroup::close);
    }

    @Override
    BlockingHttpClientGroup<UnresolvedAddress, I, O> asBlockingClientGroupInternal() {
        return blockingClientGroup;
    }
}
