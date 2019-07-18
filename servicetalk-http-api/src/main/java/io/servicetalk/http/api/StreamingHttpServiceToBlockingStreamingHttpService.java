/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.PlatformDependent;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class StreamingHttpServiceToBlockingStreamingHttpService implements BlockingStreamingHttpService {
    private final StreamingHttpService original;

    StreamingHttpServiceToBlockingStreamingHttpService(final StreamingHttpService original) {
        this.original = requireNonNull(original);
    }

    @Override
    public void handle(final HttpServiceContext ctx,
                       final BlockingStreamingHttpRequest request,
                       final BlockingStreamingHttpServerResponse svcResponse) throws Exception {
        // Block handle() for the duration of the request for now (for cancellation reasons)
        futureGetCancelOnInterrupt(handelRequestWithJersey(ctx, request, svcResponse));
    }

    @Nonnull
    private Completable handelRequestWithJersey(final HttpServiceContext ctx,
                                                final BlockingStreamingHttpRequest request,
                                                final BlockingStreamingHttpServerResponse svcResponse) {
        return original.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMapCompletable(streamingHttpResponse -> {
                    copyMeta(streamingHttpResponse, svcResponse);
                    return new PayloadBodyAndTrailersToPayloadWriter(
                            streamingHttpResponse.payloadBodyAndTrailers(), svcResponse.sendMetaData());
                });
    }

    private void copyMeta(final StreamingHttpResponse streamingResponse,
                          final BlockingStreamingHttpServerResponse svcResponse) {
        svcResponse.setHeaders(streamingResponse.headers());
        svcResponse.status(streamingResponse.status());
        svcResponse.version(streamingResponse.version());
    }

    @Override
    public void close() throws Exception {
        original.closeAsync().toFuture().get();
    }

    static void futureGetCancelOnInterrupt(Completable source) throws Exception {
        Future<Void> future = source.toFuture();
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            PlatformDependent.throwException(e.getCause());
            assert false : "unreachable";
        }
    }

    private static class PayloadBodyAndTrailersToPayloadWriter extends SubscribableCompletable {

        private final Publisher<Object> payloadBodyAndTrailers;
        private final HttpPayloadWriter<Buffer> payloadWriter;

        PayloadBodyAndTrailersToPayloadWriter(final Publisher<Object> payloadBodyAndTrailers,
                                              final HttpPayloadWriter<Buffer> payloadWriter) {
            this.payloadBodyAndTrailers = payloadBodyAndTrailers;
            this.payloadWriter = payloadWriter;
        }

        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            final DelayedCancellable cancellable = new DelayedCancellable();
            toSource(payloadBodyAndTrailers).subscribe(new PayloadPump(subscriber, cancellable, payloadWriter));
            subscriber.onSubscribe(cancellable);
        }

        private static final class PayloadPump implements PublisherSource.Subscriber<Object> {

            private static final AtomicIntegerFieldUpdater<PayloadPump> terminatedUpdater =
                    newUpdater(PayloadPump.class, "terminated");

            private final Subscriber subscriber;
            private final DelayedCancellable cancellable;
            private final HttpPayloadWriter<Buffer> payloadWriter;
            private volatile int terminated;

            PayloadPump(final Subscriber subscriber,
                        final DelayedCancellable cancellable,
                        final HttpPayloadWriter<Buffer> payloadWriter) {
                this.subscriber = subscriber;
                this.cancellable = cancellable;
                this.payloadWriter = payloadWriter;
            }

            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                cancellable.delayedCancellable(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Object bufferOrTrailers) {
                assert bufferOrTrailers != null;
                try {
                    if (bufferOrTrailers instanceof Buffer) {
                        payloadWriter.write((Buffer) bufferOrTrailers);
                        return;
                    }
                    if (bufferOrTrailers instanceof HttpHeaders) {
                        payloadWriter.setTrailers((HttpHeaders) bufferOrTrailers);
                        return;
                    }
                    assert false : "Expected only buffer or trailer in payloadBodyAndTrailers()";
                } catch (IOException e) {
                    try {
                        if (nonTerminated()) {
                            subscriber.onError(e);
                        } else {
                            PlatformDependent.throwException(e);
                        }
                    } finally {
                        cancellable.cancel();
                    }
                }
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    payloadWriter.flush();
                } catch (IOException e) {
                    t.addSuppressed(e);
                }
                if (nonTerminated()) {
                    subscriber.onError(t);
                } else {
                    PlatformDependent.throwException(t);
                }
            }

            @Override
            public void onComplete() {
                try {
                    payloadWriter.close();
                    if (nonTerminated()) {
                        subscriber.onComplete();
                    }
                } catch (IOException e) {
                    if (nonTerminated()) {
                        subscriber.onError(e);
                    } else {
                        PlatformDependent.throwException(e);
                    }
                }
            }

            boolean nonTerminated() {
                return terminatedUpdater.compareAndSet(this, 0, 1);
            }
        }
    }
}
