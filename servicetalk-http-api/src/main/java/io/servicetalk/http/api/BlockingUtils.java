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

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.lang.Thread.currentThread;

final class BlockingUtils {

    private static final Object DUMMY = new Object();

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
                    cancellable.done(cause);
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and
                // here.
                cancellable.done();
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
                    cancellable.done(cause);
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.done();

                // The from(..) operator will take care of propagating cancel.
                subscriber.onSuccess(response);
            }
        };
    }

    static BlockingStreamingHttpResponse request(final StreamingHttpRequester requester,
                                                 final HttpExecutionStrategy strategy,
                                                 final BlockingStreamingHttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(requester.request(strategy, request.toStreamingRequest()))
                .toBlockingStreamingResponse();
    }

    static Single<StreamingHttpResponse> request(final BlockingHttpRequester requester,
                                                 final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> requester.request(strategy, req)))
                .map(HttpResponse::toStreamingResponse);
    }

    static HttpResponse request(final HttpRequester requester, final HttpExecutionStrategy strategy,
                                final HttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(requester.request(strategy, request));
    }

    static HttpResponse request(final StreamingHttpRequester requester, final HttpExecutionStrategy strategy,
                                final HttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(requester.request(strategy, request.toStreamingRequest())
                .flatMap(StreamingHttpResponse::toResponse));
    }

    static Single<StreamingHttpResponse> request(final BlockingStreamingHttpRequester requester,
                                                 final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return blockingToSingle(() -> requester.request(strategy, request.toBlockingStreamingRequest())
                .toStreamingResponse());
    }

    static Single<HttpResponse> request(final BlockingHttpRequester requester, final HttpExecutionStrategy strategy,
                                        final HttpRequest request) {
        return blockingToSingle(() -> requester.request(strategy, request));
    }

    static <T> T blockingInvocation(Single<T> source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            return source.toFuture().get();
        } catch (final ExecutionException e) {
            throwException(e.getCause());
            return uncheckedCast(); // Used to fool the compiler, but actually should never be invoked at runtime.
        }
    }

    static void blockingInvocation(Completable source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            source.toFuture().get();
        } catch (final ExecutionException e) {
            throwException(e.getCause());
        }
    }

    private static <T> T uncheckedCast() {
        return (T) DUMMY;
    }
}
