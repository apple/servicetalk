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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.from;
import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultAggregatedHttpResponse.from;
import static io.servicetalk.http.api.DefaultAggregatedHttpResponse.toHttpResponse;
import static io.servicetalk.http.api.HttpRequests.fromBlockingRequest;
import static io.servicetalk.http.api.HttpResponses.fromBlockingResponse;
import static java.lang.Thread.currentThread;

final class BlockingUtils {
    private BlockingUtils() {
        // no instances
    }

    interface RunnableCheckedException {
        void run() throws Exception;
    }

    interface SupplierCheckedException<T> {
        T get() throws Exception;
    }

    static Completable blockingToCompletable(RunnableCheckedException r) {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                try {
                    r.run();
                } catch (Throwable cause) {
                    cancellable.setDone(cause);
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and
                // here.
                cancellable.setDone();
                subscriber.onComplete();
            }
        };
    }

    static <T> Single<T> blockingToSingle(SupplierCheckedException<T> supplier) {
        return new Single<T>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super T> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final T response;
                try {
                    response = supplier.get();
                } catch (Throwable cause) {
                    cancellable.setDone(cause);
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

    static void close(AsyncCloseable closeable) throws ExecutionException, InterruptedException {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        awaitIndefinitely(closeable.closeAsync());
    }

    static BlockingHttpResponse<HttpPayloadChunk> request(final HttpRequester requester,
                                                          final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new DefaultBlockingHttpResponse<>(
                awaitIndefinitelyNonNull(requester.request(fromBlockingRequest(request))));
    }

    static Single<HttpResponse<HttpPayloadChunk>> request(final BlockingAggregatedHttpRequester requester,
                                                          final HttpRequest<HttpPayloadChunk> request) {
        return from(request, requester.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedRequest -> blockingToSingle(() ->
                        toHttpResponse(requester.request(aggregatedRequest))));
    }

    static AggregatedHttpResponse<HttpPayloadChunk> request(final AggregatedHttpRequester requester,
                                                            final AggregatedHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return awaitIndefinitelyNonNull(requester.request(request));
    }

    static AggregatedHttpResponse<HttpPayloadChunk> request(final HttpRequester requester,
                                                            final AggregatedHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return awaitIndefinitelyNonNull(requester.request(toHttpRequest(request)).flatMap(response ->
                from(response, requester.getExecutionContext().getBufferAllocator())));
    }

    static Single<HttpResponse<HttpPayloadChunk>> request(final BlockingHttpRequester blockingHttpRequester,
                                                  final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> fromBlockingResponse(blockingHttpRequester.request(
                new DefaultBlockingHttpRequest<>(request))));
    }

    static Single<AggregatedHttpResponse<HttpPayloadChunk>> request(final BlockingAggregatedHttpRequester requester,
                                                                final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> requester.request(request));
    }
}
